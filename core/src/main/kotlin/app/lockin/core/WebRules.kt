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
    fun isAllowedUrl(url: String): Boolean {
        if (url.isBlank()) return false

        // Unparseable input that mentions Facebook or Messenger fails closed: it is a bypass attempt, not garbage.
        val uri = parse(url) ?: return !mentionsFacebookOrMessenger(url)
        val host = uri.host
        // A bare path such as "/marketplace/item/1" only occurs on Facebook pages,
        // so it is judged as a Facebook path. Other host-less input fails open.
        val isRelativePath = host == null && url.trim().startsWith("/")
        if (host == null && !isRelativePath) return true
        if (host != null && !isFacebookHost(host)) return true

        val path = uri.path.orEmpty().trimStart('/')
        if (path.isEmpty()) return false

        val firstSegment = path.substringBefore('/').lowercase()
        return allowedPathPrefixes.any { it == firstSegment }
    }

    fun isLockedUrl(url: String): Boolean = !isAllowedUrl(url)

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
