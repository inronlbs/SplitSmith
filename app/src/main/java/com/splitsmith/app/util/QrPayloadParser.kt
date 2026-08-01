package com.splitsmith.app.util

import android.net.Uri

sealed class ParsedQrPayload {
    data class UserQr(val userCode: String, val uid: String = "") : ParsedQrPayload()
    data class GroupQr(val groupId: String) : ParsedQrPayload()
    data class Unknown(val rawText: String) : ParsedQrPayload()
}

object QrPayloadParser {

    /**
     * Parses any scanned QR payload (URIs, deep links, or raw text) into a structured ParsedQrPayload.
     */
    fun parse(rawPayload: String): ParsedQrPayload {
        val trimmed = rawPayload.trim()
        if (trimmed.isBlank()) return ParsedQrPayload.Unknown("")

        try {
            if (trimmed.startsWith("splitsmith://", ignoreCase = true) ||
                trimmed.startsWith("https://splitsmith.app/", ignoreCase = true) ||
                trimmed.startsWith("http://splitsmith.app/", ignoreCase = true)
            ) {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: uri.authority ?: ""
                val path = uri.path ?: ""

                // User QR: splitsmith://user?code=SHORTCODE&uid=UID
                if (host.equals("user", ignoreCase = true) || path.contains("user", ignoreCase = true)) {
                    val code = uri.getQueryParameter("code") ?: ""
                    val uid = uri.getQueryParameter("uid") ?: ""
                    if (code.isNotEmpty() || uid.isNotEmpty()) {
                        return ParsedQrPayload.UserQr(userCode = code.ifEmpty { uid }, uid = uid)
                    }
                }

                // Group QR: splitsmith://join?code=GROUP_ID or splitsmith://group?id=GROUP_ID
                if (host.equals("join", ignoreCase = true) || host.equals("group", ignoreCase = true) ||
                    path.contains("join", ignoreCase = true) || path.contains("group", ignoreCase = true)
                ) {
                    val groupId = uri.getQueryParameter("code") ?: uri.getQueryParameter("id") ?: ""
                    if (groupId.isNotEmpty()) {
                        return ParsedQrPayload.GroupQr(groupId = groupId)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback heuristics for raw strings
        return when {
            trimmed.startsWith("g_", ignoreCase = true) -> ParsedQrPayload.GroupQr(trimmed)
            else -> ParsedQrPayload.Unknown(trimmed)
        }
    }

    /**
     * Returns true if the string is safe to pass into Firestore document() calls
     * (does not contain slashes, colons, question marks, etc.)
     */
    fun isValidFirestoreDocId(id: String): Boolean {
        if (id.isBlank()) return false
        val invalidChars = listOf('/', '\\', '?', ':', '#', '%', '&', '=')
        return invalidChars.none { id.contains(it) }
    }

    /**
     * Extracts a clean, Firestore-safe code/ID string from any payload.
     */
    fun extractCleanCode(rawPayload: String): String {
        return when (val parsed = parse(rawPayload)) {
            is ParsedQrPayload.UserQr -> parsed.userCode.ifEmpty { parsed.uid }
            is ParsedQrPayload.GroupQr -> parsed.groupId
            is ParsedQrPayload.Unknown -> {
                if (isValidFirestoreDocId(parsed.rawText)) {
                    parsed.rawText
                } else {
                    parsed.rawText.replace(Regex("[^a-zA-Z0-9_\\-@]"), "")
                }
            }
        }
    }
}
