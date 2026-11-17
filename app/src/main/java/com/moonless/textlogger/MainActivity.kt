package com.moonless.textlogger

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var logText: TextView
    private lateinit var logFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(TextLoggerService.PREFS, MODE_PRIVATE)
        logFile = File(filesDir, TextLoggerService.LOG_FILE)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        logText = findViewById(R.id.logText)

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            logFile.delete(); logFile.createNewFile()
            refreshLog()
            Toast.makeText(this, "Log șters", Toast.LENGTH_SHORT).show()
        }

        toggleButton.setOnClickListener { toggle() }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        refreshLog()
    }

    private fun toggle() {
        val enabled = prefs.getBoolean(TextLoggerService.KEY_ENABLED, false)
        if (!enabled) {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Activează serviciul în Settings → Accessibility", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return
            }
            prefs.edit().putBoolean(TextLoggerService.KEY_ENABLED, true).apply()
        } else {
            prefs.edit().putBoolean(TextLoggerService.KEY_ENABLED, false).apply()
        }
        updateUi()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${TextLoggerService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun updateUi() {
        val enabled = prefs.getBoolean(TextLoggerService.KEY_ENABLED, false)
        statusText.text = if (enabled) "Status: ACTIV" else "Status: INACTIV"
        toggleButton.text = if (enabled) "Dezactivează" else "Activează"
    }

    private fun refreshLog() {
        if (!logFile.exists()) { logText.text = "(Gol)"; return }
        val content = logFile.readText(Charsets.UTF_8)
        logText.text = if (content.isEmpty()) "(Gol)" else if (content.length > 5000) "…" + content.takeLast(5000) else content
    }
}