// Regenerates dicebear-styles/reference/avatars.json with the official JavaScript implementation.
//
// The Kotlin test ReferenceAvatarsTest renders the same cases on every target and requires
// byte-identical SVG and identical resolved options.
//
// Usage (in an empty directory, outside this repository):
//   npm init -y
//   npm install --ignore-scripts @dicebear/core@<core version> @dicebear/styles@<styles version>
//   node <repo>/scripts/generate-reference.mjs node_modules/@dicebear/styles/dist <repo>/dicebear-styles/reference/avatars.json
//
// The definitions in dicebear-styles/definitions must come from the same @dicebear/styles release.

import { Avatar, Style } from '@dicebear/core';
import fs from 'node:fs';
import path from 'node:path';

const [dist, outFile] = process.argv.slice(2);

const optionSets = [
  {},
  {
    flip: ['horizontal', 'vertical', 'both'],
    rotate: [-30, 30],
    scale: [0.8, 1.2],
    translateX: [-5, 5],
    backgroundColor: ['b6e3f4', 'c0aede', 'd1d4f9', 'ffd5dc'],
    backgroundColorFill: ['linear', 'radial'],
    backgroundColorAngle: [0, 90],
    borderRadius: 20,
    size: 64,
    title: 'Avatar <&> "quoted"',
  },
];

const seeds = ['Felix', 'Aneka', 'Ünïcødé 🎲 user@example.com'];
const cases = [];

for (const file of fs.readdirSync(dist).filter((f) => f.endsWith('.min.json')).sort()) {
  const name = file.replace('.min.json', '');
  const style = new Style(JSON.parse(fs.readFileSync(path.join(dist, file), 'utf8')));

  for (const seed of seeds) {
    for (const set of optionSets) {
      const options = { seed, ...set };
      const avatar = new Avatar(style, options);

      cases.push({
        style: name,
        options,
        svg: avatar.toString(),
        resolvedOptions: JSON.parse(JSON.stringify(avatar)).options,
      });
    }
  }
}

fs.writeFileSync(outFile, JSON.stringify(cases));
console.log(`${cases.length} cases written to ${outFile}`);
