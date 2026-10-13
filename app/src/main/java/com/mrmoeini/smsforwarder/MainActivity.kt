package com.mrmoeini.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val rules = mutableListOf<Rule>()
    private lateinit var adapter: RuleAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView

    // State for the rule dialog's "pick contact" button.
    private var destField: EditText? = null
    private var pickedName: String = ""
    private var pickedNumber: String = ""

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshUi()
        }

    private val pickContact =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        pickedNumber = normalizeNumber(c.getString(0) ?: "")
                        pickedName = c.getString(1) ?: ""
                        destField?.setText(pickedNumber)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)

        rules.addAll(RuleStore.load(this))

        adapter = RuleAdapter(
            rules,
            onToggle = { rule, checked ->
                val i = rules.indexOfFirst { it.id == rule.id }
                if (i >= 0) {
                    rules[i] = rules[i].copy(enabled = checked)
                    RuleStore.save(this, rules)
                }
            },
            onEdit = { showRuleDialog(it) },
            onDelete = { confirmDelete(it) }
        )

        val rv = findViewById<RecyclerView>(R.id.rvRules)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showRuleDialog(null) }

        tvStatus.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        if (!hasPermissions()) permLauncher.launch(requiredPermissions)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshUi() {
        tvStatus.visibility = if (hasPermissions()) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun normalizeNumber(s: String): String = s.filter { it.isDigit() || it == '+' }

    private fun persist() {
        RuleStore.save(this, rules)
        adapter.notifyDataSetChanged()
        refreshUi()
    }

    private fun showRuleDialog(existing: Rule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etSender = view.findViewById<EditText>(R.id.etSender)
        val etKeyword = view.findViewById<EditText>(R.id.etKeyword)
        val etDest = view.findViewById<EditText>(R.id.etDest)
        val btnPick = view.findViewById<Button>(R.id.btnPick)

        pickedName = existing?.destName ?: ""
        pickedNumber = existing?.destNumber ?: ""
        if (existing != null) {
            etName.setText(existing.name)
            etSender.setText(existing.sender)
            etKeyword.setText(existing.keyword)
            etDest.setText(existing.destNumber)
        }
        destField = etDest

        btnPick.setOnClickListener {
            try {
                pickContact.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.err_no_contacts, Toast.LENGTH_SHORT).show()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_rule else R.string.edit_rule)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { destField = null }
        dialog.show()

        // Set the click listener after show() so invalid input does not close the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val sender = etSender.text.toString().trim()
            val dest = normalizeNumber(etDest.text.toString())
            if (sender.isEmpty()) {
                etSender.error = getString(R.string.err_sender)
                return@setOnClickListener
            }
            if (dest.isEmpty()) {
                etDest.error = getString(R.string.err_dest)
                return@setOnClickListener
            }
            val destName = if (dest == pickedNumber) pickedName else ""
            val name = etName.text.toString().trim()
            val keyword = etKeyword.text.toString().trim()

            if (existing == null) {
                rules.add(Rule(name = name, sender = sender, keyword = keyword,
                    destNumber = dest, destName = destName))
            } else {
                val i = rules.indexOfFirst { it.id == existing.id }
                if (i >= 0) {
                    rules[i] = rules[i].copy(name = name, sender = sender, keyword = keyword,
                        destNumber = dest, destName = destName)
                }
            }
            persist()
            dialog.dismiss()
        }
    }

    private fun confirmDelete(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.removeAll { it.id == rule.id }
                persist()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_log) {
            val lines = RuleStore.readLog(this)
            AlertDialog.Builder(this)
                .setTitle(R.string.log)
                .setMessage(if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n\n"))
                .setPositiveButton(R.string.close, null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
