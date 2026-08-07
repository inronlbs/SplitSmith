package com.splitsmith.app.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Extension function to format doubles as currency, preserving two decimal places
 * and avoiding micro-leakage in display that occurs with '%.0f' truncation.
 */
fun Double.formatCurrency(): String {
    // If it's a whole number (or very close), format without decimals
    if (Math.abs(this - Math.round(this)) < 0.005) {
        return "%.0f".format(this)
    }
    // Otherwise format with 2 decimal places
    return "%.2f".format(this)
}

fun Double.formatCurrencyWithSymbol(): String {
    return "₹${this.formatCurrency()}"
}
