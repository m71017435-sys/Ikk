package com.voicebubble.reader

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

data class AppEntry(val label: String, val packageName: String)

class MainActivity : AppCompatActivity() {

    private lateinit var allApps: List<AppEntry>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var filtered: MutableList<AppEntry>
    private lateinit var txtCurrent: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtCurrent = findViewById(R.id.txtCurrent)
        val editSearch: EditText = findViewById(R.id.editSearch)
        val listApps: ListView = findViewById(R.id.listApps)
        val btnAccessibility: Button = findViewById(R.id.btnAccessibility)

        allApps = loadLaunchableApps()
        filtered = allApps.toMutableList()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered.map { it.label })
        listApps.adapter = adapter

        updateCurrentLabel()

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                filtered = if (query.isEmpty()) {
                    allApps.toMutableList()
                } else {
                    allApps.filter {
                        it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                    }.toMutableList()
                }
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, filtered.map { it.label })
                listApps.adapter = adapter
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listApps.setOnItemClickListener { _, _, position, _ ->
            val chosen = filtered[position]
            val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            prefs.edit()
                .putString(Prefs.TARGET_PACKAGE, chosen.packageName)
                .putString(Prefs.TARGET_LABEL, chosen.label)
                .apply()
            updateCurrentLabel()
            Toast.makeText(this, "انتخاب شد: ${chosen.label}", Toast.LENGTH_SHORT).show()
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentLabel()
    }

    private fun updateCurrentLabel() {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val label = prefs.getString(Prefs.TARGET_LABEL, null)
        txtCurrent.text = if (label != null) {
            getString(R.string.current_target, label)
        } else {
            getString(R.string.no_target)
        }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

object Prefs {
    const val NAME = "voice_bubble_prefs"
    const val TARGET_PACKAGE = "target_package"
    const val TARGET_LABEL = "target_label"
}
