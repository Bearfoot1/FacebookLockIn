package app.lockin.core

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Decides which facebook.com URLs the user may view.
 *
 * Only Marketplace, login/recovery/checkpoint pages and legal/help pages are allowed.
 * Non-Facebook URLs are never judged here (they return true), and anything that cannot
 * be parsed fails open, so this object only ever locks Facebook pages.
 */
object WebRules {

    const val MARKETPLACE_URL: String = "https://www.facebook.com/marketplace/"

    private const val MARKETPLACE_SEARCH_URL = "https://www.facebook.com/marketplace/search/"

    val allowedPathPrefixes: List<String> = listOf(
        "marketplace",
        "login",
        "login.php",
        "checkpoint",
        "recover",
        "two_step_verification",
        "help",
        "policies",
        "privacy",
        "terms",
        "cookies",
    )

    /** True for facebook.com itself and any subdomain of it (www, m, web, mbasic, ...). */
    fun isFacebookHost(host: String): Boolean {
        val normalized = host.trim().lowercase().trimEnd('.')
        return normalized == "facebook.com" || normalized.endsWith(".facebook.com")
    }

    /**
     * True when the URL may be viewed.
     *
     * - Blank input is treated like the Facebook home page and is locked.
     * - Non-Facebook URLs are allowed; host-less input (other than a leading "/") is allowed.
     * - A leading "/" is a Facebook-relative path and is judged by the rules below.
     * - Unparseable input is allowed (fail open).
     * - On Facebook, the first path segment must exactly equal an allowed prefix
     *   (case-insensitive). The home page "/" or an empty path is locked.
     */
    fun isAllowedUrl(rawUrl: String): Boolean {
        val url = stripInvisible(rawUrl)
        if (url.isBlank()) return false

        // Unparseable input that mentions Facebook or Messenger fails closed: it is a bypass attempt, not garbage.
        val uri = parse(url) ?: return !mentionsFacebookOrMessenger(url)
        val host = uri.host
        // A bare path such as "/marketplace/item/1" only occurs on Facebook pages,
        // so it is judged as a Facebook path. Other host-less input fails open.
        val isRelativePath = host == null && url.trim().startsWith("/")
        // Host-less input that still mentions Facebook (e.g. an invisible or non-ASCII
        // character in the host) fails closed, like unparseable input.
        if (host == null && !isRelativePath) return !mentionsFacebookOrMessenger(url)
        if (host != null && !isFacebookHost(host)) return true

        val path = uri.path.orEmpty().trimStart('/')
        if (path.isEmpty()) return false

        val firstSegment = path.substringBefore('/').lowercase()
        return allowedPathPrefixes.any { it == firstSegment }
    }

    fun isLockedUrl(url: String): Boolean = !isAllowedUrl(url)

    /**
     * True when browser URL bar text is a bare host with no path, query or fragment
     * ("m.facebook.com"). Samsung Internet always shows Facebook pages this way, so
     * the page cannot be known; the owner chose to allow these (2026-11-05) rather
     * than block every Facebook page in that browser.
     */
    fun isHostOnlyDisplay(urlBarText: String): Boolean {
        val text = stripInvisible(urlBarText).trim().substringAfter("://")
        return text.isNotEmpty() && text.none { it == '/' || it == '?' || it == '#' || it.isWhitespace() }
    }

    /**
     * Removes Unicode format characters (category Cf: LRM U+200E, zero-width space, BOM, ...).
     * Samsung Internet prefixes its URL bar text with U+200E, which made the URL
     * unparseable and so allowed (seen on a Galaxy S9+, 2026-11-05).
     */
    private fun stripInvisible(text: String): String =
        text.filterNot { Character.getType(it) == Character.FORMAT.toInt() }

    private fun mentionsFacebookOrMessenger(text: String): Boolean {
        val lower = text.lowercase()
        return listOf("facebook.com", "fb.com", "messenger.com").any { lower.contains(it) }
    }

    /** Marketplace search URL; a blank query yields the bare search page. */
    fun marketplaceSearchUrl(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return MARKETPLACE_SEARCH_URL
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8).replace("+", "%20")
        return "$MARKETPLACE_SEARCH_URL?query=$encoded"
    }

    /**
     * Parses with java.net.URI. Scheme-less input that looks like a host
     * (for example "facebook.com/marketplace") is retried as https.
     */
    private fun parse(url: String): URI? {
        val trimmed = url.trim()
        val candidate = if (!trimmed.contains("://") && !trimmed.startsWith("/") &&
            !trimmed.startsWith("?") && !trimmed.startsWith("#")
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return try {
            URI(candidate)
        } catch (e: java.net.URISyntaxException) {
            null
        }
    }
}
