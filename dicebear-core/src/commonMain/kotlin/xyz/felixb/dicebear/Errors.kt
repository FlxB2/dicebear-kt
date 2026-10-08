package xyz.felixb.dicebear

/** A single failure inside a [ValidationError]. */
public data class ValidationErrorDetail(
    /** JSON pointer to the failing value (e.g. `/components/eyes/extends`), if any. */
    val instancePath: String?,
    /** Human-readable description of the failure. */
    val message: String?,
)

/** Base class for schema validation errors; [details] lists every per-field failure. */
public open class ValidationError(
    prefix: String,
    public val details: List<ValidationErrorDetail>,
) : IllegalArgumentException(format(prefix, details)) {
    private companion object {
        fun format(prefix: String, details: List<ValidationErrorDetail>): String =
            "$prefix: " + details.joinToString(", ") { detail ->
                listOfNotNull(
                    detail.instancePath?.takeIf(String::isNotEmpty),
                    detail.message?.takeIf(String::isNotEmpty),
                ).joinToString(" ")
            }
    }
}

/** Thrown when a style definition is invalid. */
public class StyleValidationError(details: List<ValidationErrorDetail>) :
    ValidationError("Invalid style definition", details)

/** Thrown when avatar options are invalid. */
public class OptionsValidationError(details: List<ValidationErrorDetail>) :
    ValidationError("Invalid options", details)

/** Thrown when style colors reference each other in a cycle; [chain] is the resolution path. */
public class CircularColorReferenceError(public val chain: List<String>) :
    IllegalStateException("Circular color reference: ${chain.joinToString(" → ")}")
