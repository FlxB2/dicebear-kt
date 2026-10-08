# DiceBear Kotlin

A Kotlin Multiplatform port of [DiceBear](https://github.com/dicebear/dicebear), the avatar library.
It turns any seed (a username, an email address, …) into an SVG avatar in one of 63 styles. Everything
runs **offline**: no HTTP API, no WebView, no JavaScript engine.

> [!NOTE]
> This project is 100% vibe coded: an AI agent (Claude Code) wrote it, porting DiceBear's reference
> implementation. It is checked against DiceBear's official cross-language test suite and the
> official JavaScript output for every bundled style, on every target. Still, review it before you
> rely on it.

- **Targets:** Android, iOS (arm64, simulator arm64, x64), JVM, JS and Wasm (browser and Node).
- **Byte-identical output:** the same seed and options produce exactly the SVG the official
  JavaScript, PHP, Python, Rust, Go, Dart and C# implementations produce. This is checked on every
  target (see [Testing](#testing)).
- **No dependencies:** the engine ships its own JSON parser and JSON Schema validator.

| Module            | Contents                                                                    |
| ----------------- | --------------------------------------------------------------------------- |
| `dicebear-core`   | The engine: `Style`, `Avatar`, `OptionsDescriptor`, validation errors.      |
| `dicebear-styles` | All 63 official style definitions as `DiceBearStyles.<name>` (depends on core). |

Versions: core ports `@dicebear/core` **11.0.0-rc.2**, styles are `@dicebear/styles` **11.0.0-rc.3**.

## Installation

The library is not on Maven Central yet. Publish it to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then, in your project:

```kotlin
repositories {
    mavenLocal()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("xyz.felixb.dicebear:dicebear-styles:11.0.0-rc.2") // includes dicebear-core
        }
    }
}
```

Use `dicebear-core` alone if you bring your own style definitions (JSON) and don't want the
bundled ones.

## Usage

```kotlin
import xyz.felixb.dicebear.Avatar
import xyz.felixb.dicebear.styles.DiceBearStyles
import xyz.felixb.dicebear.styles.lorelei

val avatar = Avatar(DiceBearStyles.lorelei) {
    seed = "Felix"
    size = 128
    backgroundColor = listOf("b6e3f4", "c0aede", "d1d4f9")
}

avatar.svg          // "<svg xmlns=…>…</svg>"
avatar.toDataUri()  // "data:image/svg+xml;charset=utf-8,%3Csvg…" (e.g. for <img src>)
avatar.toJson()     // {"svg": …, "options": …}, like JSON.stringify(avatar) in JS
avatar.resolvedOptions // what was picked: eyesVariant, backgroundColor, rotate, …
```

Options use the names from the [DiceBear docs](https://www.dicebear.com/styles/). Every option can
also be passed as a map, including the style-specific ones:

```kotlin
val avatar = Avatar(
    DiceBearStyles.adventurer,
    mapOf(
        "seed" to "Aneka",
        "flip" to "horizontal",
        "rotate" to listOf(-10, 10),          // a range: picked deterministically from the seed
        "eyesVariant" to listOf("variant01", "variant02"),
        "glassesProbability" to 100,
        "backgroundColorFill" to "linear",
    ),
)
// The builder has `set` for the same purpose: Avatar(style) { this["eyesVariant"] = "variant01" }
```

Styles by name, for example from user settings:

```kotlin
DiceBearStyles.names             // ["adventurer", "adventurer-neutral", "avataaars", …]
DiceBearStyles["pixel-art"]      // Style?
DiceBearStyles.definition("pixel-art") // raw JSON
```

Custom styles ([format](https://www.dicebear.com/create-styles/definition-schema/)):

```kotlin
val style = Style.parse(jsonText) // throws StyleValidationError when invalid
```

`OptionsDescriptor(style).toMap()` lists every option a style accepts (types, ranges, variant
names). That is enough to build an avatar editor UI without reading the definition yourself.

### Displaying the SVG

The library returns SVG markup; drawing it is up to the platform:

- **Compose Multiplatform / Android:** e.g. [Coil 3](https://coil-kt.github.io/coil/) with its SVG
  decoder (`coil-svg`): pass `avatar.svg.encodeToByteArray()` as the model.
- **iOS:** `WKWebView.loadHTMLString`, or an SVG library such as SVGKit.
- **Web (JS/Wasm):** set `innerHTML`, or use `toDataUri()` as an `<img>` source.

### Swift (iOS)

```bash
./gradlew :dicebear-styles:assembleDiceBearXCFramework
# → dicebear-styles/build/XCFrameworks/release/DiceBear.xcframework
```

```swift
import DiceBear

let avatar = try Avatar(style: DiceBearStyles.shared.lorelei, options: [
    "seed": "Felix",
    "size": 128,
    "backgroundColor": ["b6e3f4", "c0aede"],
])
print(avatar.svg)
```

Invalid options and styles throw Swift errors (`ValidationError` subclasses).

### Errors

| Exception                     | When                                                       |
| ----------------------------- | ---------------------------------------------------------- |
| `OptionsValidationError`      | The options violate the DiceBear options schema.           |
| `StyleValidationError`        | A style definition is invalid.                             |
| `CircularColorReferenceError` | A style's colors reference each other in a cycle.          |

Both validation errors extend `ValidationError` (an `IllegalArgumentException`) and list every
failure in `details`.

## Licenses

The engine is MIT licensed (see `LICENSE`; it is a port of DiceBear by Florian Körner). **The avatar
styles have their own licenses:** 44 are CC0 1.0, 14 are CC BY 4.0 (attribution required), 4 are
"free for personal and commercial use" and 1 is MIT.
See `dicebear-styles/LICENSE.md` and the KDoc of each `DiceBearStyles.<name>` accessor.
`Style.meta` gives you the creator, source and license at runtime. Every rendered SVG also embeds
this information in its `<metadata>` element.

## Testing

```bash
./gradlew jvmTest jsNodeTest wasmJsNodeTest iosSimulatorArm64Test testAndroidHostTest
```

Every target runs:

- **The DiceBear parity suite** (`dicebear-core/parity`, copied from
  [`tests/fixtures/parity`](https://github.com/dicebear/dicebear/tree/main/tests/fixtures/parity)):
  FNV-1a, Mulberry32, PRNG, number formatting, initials, colors, schema validation, option
  descriptors and full avatars. The SVGs must match byte for byte.
- **The reference avatars** (`dicebear-core/reference/avatars.json`): all 63 bundled styles × 3
  seeds × 2 option sets, rendered by the official `@dicebear/core`. They must be byte-identical,
  and the resolved options must match. Regenerate with `scripts/generate-reference.mjs` when
  updating the styles.

`./gradlew :dicebear-styles:jvmTest` also writes example galleries to
`dicebear-styles/build/gallery/` (`index.html`: every style; `moods.html`: 100 × moods).

## Updating DiceBear

1. **Styles:** replace `dicebear-styles/definitions/*.json` with the `dist/*.min.json` files of a
   new `@dicebear/styles` release (drop the `.min`), then regenerate the reference avatars.
2. **Engine:** port changes from the reference implementation (the Dart port in
   `src/dart/core` is the closest to Kotlin). Copy the new parity fixtures to
   `dicebear-core/parity` and the schemas of `@dicebear/schema` to `dicebear-core/schema`.

## Project layout

```
build-logic/      EmbedJsonTask: turns JSON files into Kotlin sources (styles, schemas, fixtures)
dicebear-core/    engine (commonMain) + parity/reference tests (commonTest)
  schema/         draft-07 JSON Schemas from @dicebear/schema
  parity/         cross-language parity fixtures
  reference/      official JS output for every bundled style
dicebear-styles/  bundled styles + XCFramework
  definitions/    style definitions from @dicebear/styles
scripts/          reference fixture generator (Node)
```
