package com.eepiemi.materialbook.utils

import java.net.URL
import java.net.URLDecoder

/**
 * Unwraps Facebook's l.facebook.com/l.php link shim and drops the `fbclid` tracking parameter.
 * The other query parameters and the #fragment are kept exactly as written: values are already
 * percent-encoded, so they are never decoded or re-encoded here.
 */
fun fbRedirectSanitizer(link: String): String {
    try {
        var url = URL(link)

        if (url.host == "l.facebook.com" && url.path == "/l.php") {
            val target = url.query?.split("&")
                ?.firstOrNull { it.startsWith("u=") }
                ?.substringAfter('=')
                ?: return link
            url = URL(URLDecoder.decode(target, "UTF-8"))
        }

        val params = url.query?.split("&")
            ?.filter { it.isNotEmpty() && it != "fbclid" && !it.startsWith("fbclid=") }
            ?.joinToString("&")

        return buildString {
            append("${url.protocol}://${url.host}")
            if (url.port != -1 && url.port != url.defaultPort) append(":${url.port}")
            append(url.path)
            if (!params.isNullOrBlank()) append("?").append(params)
            if (!url.ref.isNullOrEmpty()) append("#").append(url.ref)
        }
    } catch (_: Exception) {
        return link
    }
}
