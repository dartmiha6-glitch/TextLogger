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

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_VIEW_FOCUSED, 
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastText = "" // Resetăm când schimbăm câmpul
            }
        }
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Nu loga propria aplicație

        // PROTECȚIE: Dacă e câmp de parolă, nu salva!
        val source = event.source ?: return
        if (source.isPassword) {
            source.recycle()
            return
        }

        val currentText = source.text?.toString() ?: ""
        source.recycle()

        if (currentText.isEmpty() || currentText == lastText) return

        val delta = diffText(lastText, currentText)
        lastText = currentText

        if (delta.isNotEmpty()) {
            appendToLog(pkg, delta)
        }
    }

    private fun diffText(old: String, new: String): String {
        if (old.isEmpty()) return new
        if (new.startsWith(old)) return new.substring(old.length) // S-a adaugat
        if (old.startsWith(new)) return "" // S-a sters (backspace)
        return "\n[Modificat]: $new" // S-a dat paste sau s-a rescris
    }

    private fun appendToLog(pkg: String, text: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val line = "[$timestamp] [$pkg] $text\n"
            
            // Rotatie log daca depaseste 5MB
            if (logFile.exists() && logFile.length() > 5 * 1024 * 1024) {
                val backup = File(filesDir, "textlog_backup_${System.currentTimeMillis()}.txt")
                logFile.renameTo(backup)
            }
            
            logFile.appendText(line, Charsets.UTF_8)
        } catch (_: Exception) { }
    }

    override fun onInterrupt() { isRunning = false }
    override fun onDestroy() { isRunning = false; super.onDestroy() }
}
