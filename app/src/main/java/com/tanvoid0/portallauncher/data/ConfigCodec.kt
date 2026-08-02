package com.tanvoid0.portallauncher.data

import kotlinx.serialization.json.Json

/**
 * Reads and writes automation configs as JSON in
 * [AutomationConfigEntity.configJson].
 *
 * Replaces a hand-written `"primary:a,b;secondary:c"` format that existed for
 * exactly one of the six config types — the other five could not be persisted at
 * all — and that silently corrupted any value containing `,` or `;`.
 */
object ConfigCodec {

    val json = Json {
        // An older build must survive reading a config a newer build wrote. Without
        // this, adding a field to any config makes the previous version throw on it.
        ignoreUnknownKeys = true
        // Write defaults out explicitly, so a stored config is a full record of what
        // was in effect rather than something that shifts when a default changes.
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    /**
     * Decodes, falling back to [fallback] on anything unreadable.
     *
     * A config is not a trust boundary in the security sense — it is our own row in
     * our own database — but it *is* readable by anyone with root or a debug build,
     * and it can genuinely be stale: dev installs still hold rows in the old
     * `primary:…;secondary:…` format, which is not JSON. Neither case should be a
     * crash on the home screen, and a launcher that cannot start is unrecoverable
     * for the user, so a bad config degrades to the default.
     */
    inline fun <reified T> decodeOr(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching { json.decodeFromString<T>(raw) }.getOrDefault(fallback)
    }
}
