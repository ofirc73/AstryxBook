package com.eepiemi.materialbook.utils

import java.net.URL
import java.net.URLDecoder

fun fbRedirectSanitizer(link: String): String {
    try {
        var url = URL(link)

        if (url.host == "l.facebook.com" && url.path == "/l.php") {
            val target = url.query?.split("&")
                ?.firstOrNull { it.startsWith("u=") }
                ?.removePrefix("u=")
                ?: return link
            url = URL(URLDecoder.decode(target, "UTF-8"))
        }

        // Filter the raw query string: values stay exactly as encoded in the link.
        val params = url.query?.split("&")
            ?.filter { it.isNotEmpty() && it != "fbclid" && !it.startsWith("fbclid=") }
            ?.joinToString("&")

        return buildString {
            append("${url.protocol}://${url.host}")
            if (url.port != -1 && url.port != url.defaultPort) append(":${url.port}")
            append(url.path)
            if (!params.isNullOrBlank()) append("?").append(params)
            url.ref?.let { append("#").append(it) }
        }
    } catch (_: Exception) {
        return link
    }
}
