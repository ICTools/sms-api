package be.ictools.smsgateway.data.auth

/** How saved credentials are shown on screen: the secret never, the key only partly. */
object CredentialMask {
    private const val VISIBLE_PREFIX = 6
    private const val VISIBLE_SUFFIX = 3
    private const val ELLIPSIS = "…"

    // Revealing 9 characters of a shorter key would show most of it.
    private const val MIN_LENGTH_TO_REVEAL = 16

    /** `01M6YBXXXXXXXXXXKV` → `01M6YB…XKV`; keys too short to hide anything are fully masked. */
    fun maskApiKey(apiKey: String): String {
        val key = apiKey.trim()
        if (key.isEmpty()) return ""
        if (key.length < MIN_LENGTH_TO_REVEAL) return ELLIPSIS
        return key.take(VISIBLE_PREFIX) + ELLIPSIS + key.takeLast(VISIBLE_SUFFIX)
    }

    /**
     * A blank input keeps the stored value: the form never shows the secret, so an empty field
     * means "unchanged", and the only way to change it is to type a new one.
     */
    fun resolveInput(input: String, stored: String): String = input.trim().ifEmpty { stored }
}
