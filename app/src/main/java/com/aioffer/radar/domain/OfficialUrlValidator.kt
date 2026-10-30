package com.aioffer.radar.domain

import java.net.URI
import java.net.URISyntaxException

object OfficialUrlValidator {
    fun validate(rawUrl: String?, allowHttp: Boolean = false): String? {
        if (rawUrl.isNullOrBlank()) return null
        val uri = try {
            URI(rawUrl)
        } catch (_: URISyntaxException) {
            return null
        }
        val isSupportedScheme = uri.scheme.equals("https", ignoreCase = true) ||
            (allowHttp && uri.scheme.equals("http", ignoreCase = true))
        if (!isSupportedScheme || !uri.isAbsolute || uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return uri.toASCIIString()
    }
}
