package com.jeevan.expensetracker.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Handles all ML Kit receipt scanning logic.
 * Previously split across processReceiptImage() and extractTotalAmount()
 * in MainActivity — neither function needed Activity state, so both
 * are now pure functions here.
 */
object ReceiptScanner {

    private const val TAG = "ReceiptScanner"

    /**
     * Runs ML Kit text recognition on [uri], extracts the largest currency
     * amount found, and populates [targetInput] with the result.
     *
     * Callbacks run on the calling thread (main thread via ML Kit's task API).
     */
    fun scanAndFill(
        context: Context,
        uri: Uri,
        targetInput: EditText?,
        onSuccess: () -> Unit
    ) {
        Toast.makeText(context, "Scanning receipt...", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener { visionText ->
                    val amount = extractTotalAmount(visionText.text)
                    if (amount != null && amount > 0) {
                        targetInput?.setText(amount.toString())
                        onSuccess()
                        Toast.makeText(context, "Auto-filled: $amount", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Could not find a clear total amount.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit text recognition failed", e)
                    Toast.makeText(context, "Failed to scan receipt.", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "InputImage creation failed", e)
            Toast.makeText(context, "Failed to scan receipt.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extracts the largest decimal amount from raw OCR text.
     * Matches values like "1,234.56" or "99.99".
     *
     * Limitation: takes the maximum value, which works well for most
     * receipts but may pick up large product codes on some formats.
     */
    fun extractTotalAmount(text: String): Double? {
        val regex = Regex("""\b\d{1,3}(?:,\d{3})*\.\d{2}\b|\b\d+\.\d{2}\b""")
        val max = regex.findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .maxOrNull() ?: return null
        return if (max > 0) max else null
    }

    /**
     * Copies the image at [uri] into the app's internal storage and
     * returns the absolute file path, or null on failure.
     *
     * Must be called from an IO coroutine — file I/O blocks the thread.
     */
    fun saveToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = java.io.File(context.filesDir, "receipt_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save receipt to internal storage", e)
            null
        }
    }
}