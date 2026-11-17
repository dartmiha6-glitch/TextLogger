package com.moonless.textlogger

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var logFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(TextLoggerService.PREFS, MODE_PRIVATE)
        logFile = File(filesDir, TextLoggerService.LOG_FILE)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        logText = findViewById(R.id.logText)
        scrollView = findViewById(R.id.scrollView)

        toggleButton.setOnClickListener { toggle() }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            logFile.delete(); logFile.createNewFile()
            refreshLog()
            Toast.makeText(this, "Log șters", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.shareButton).setOnClickListener { shareLog() }
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
                Toast.makeText(this, "Te rog activează serviciul în Setări → Accesibilitate", Toast.LENGTH_LONG).show()
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
        statusText.text = if (enabled) "🟢 Status: ACTIV" else "🔴 Status: INACTIV"
        toggleButton.text = if (enabled) "Dezactivează" else "Activează"
    }

    private fun refreshLog() {
        if (!logFile.exists()) { logText.text = "(Niciun log)"; return }
        val content = logFile.readText(Charsets.UTF_8)
        logText.text = if (content.isEmpty()) "(Niciun log)" else {
            if (content.length > 10000) "…" + content.takeLast(10000) else content
        }
        
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun shareLog() {
        if (!logFile.exists() || logFile.length() == 0L) {
            Toast.makeText(this, "Log-ul este gol!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Trimite log-ul prin..."))
        } catch (e: Exception) {
            Toast.makeText(this, "Eroare la partajare", Toast.LENGTH_SHORT).show()
        }
    }
}
