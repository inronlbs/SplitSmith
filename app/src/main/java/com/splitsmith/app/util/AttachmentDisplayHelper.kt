package com.splitsmith.app.util

import android.content.Context
import android.net.Uri

object AttachmentDisplayHelper {

    fun cleanAttachmentLabel(rawName: String, index: Int, isPdf: Boolean): String {
        if (rawName.isBlank()) {
            return if (isPdf) "Invoice ${index + 1}" else "Receipt ${index + 1}"
        }

        val decoded = try { Uri.decode(rawName) } catch (e: Exception) { rawName }
        val nameOnly = decoded.substringAfterLast("/").substringBefore("?")

        // Check if raw name is camera capture or timestamp pattern
        val isSystemName = nameOnly.startsWith("personal_", ignoreCase = true) ||
                nameOnly.startsWith("exp_", ignoreCase = true) ||
                nameOnly.startsWith("quick_", ignoreCase = true) ||
                nameOnly.startsWith("IMG_", ignoreCase = true) ||
                nameOnly.startsWith("CAP_", ignoreCase = true) ||
                nameOnly.startsWith("camera_slip_", ignoreCase = true) ||
                nameOnly.matches(Regex("^\\d{8,}.*"))

        if (isSystemName) {
            return if (isPdf) "Invoice ${index + 1}" else "Receipt ${index + 1}"
        }

        return if (nameOnly.length > 18) {
            nameOnly.take(15) + "..."
        } else {
            nameOnly
        }
    }
}
