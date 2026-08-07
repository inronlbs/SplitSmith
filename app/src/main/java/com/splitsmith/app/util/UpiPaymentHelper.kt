package com.splitsmith.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object UpiPaymentHelper {

    fun launchUpiPayment(
        context: Context,
        receiverUpi: String,
        receiverName: String,
        amount: Double,
        note: String = "SplitSmith Settlement",
        onPaymentInitiated: () -> Unit = {}
    ) {
        if (receiverUpi.isBlank()) {
            Toast.makeText(context, "Receiver has no registered UPI ID", Toast.LENGTH_SHORT).show()
            return
        }

        val formattedAmount = if (amount % 1.0 == 0.0) amount.toInt().toString() else String.format("%.2f", amount)
        val uri = Uri.parse("upi://pay").buildUpon()
            .appendQueryParameter("pa", receiverUpi)
            .appendQueryParameter("pn", receiverName)
            .appendQueryParameter("am", formattedAmount)
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", note)
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            val chooser = Intent.createChooser(intent, "Pay via UPI App")
            context.startActivity(chooser)
            Toast.makeText(context, "Opening UPI app...", Toast.LENGTH_SHORT).show()
            onPaymentInitiated()
        } catch (e: Exception) {
            // Handle missing UPI app on device / profile gracefully
            copyToClipboard(context, receiverUpi)
            Toast.makeText(context, "No UPI app found. UPI ID ($receiverUpi) copied to clipboard!", Toast.LENGTH_LONG).show()
            onPaymentInitiated()
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("UPI ID", text)
            clipboard?.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }
}
