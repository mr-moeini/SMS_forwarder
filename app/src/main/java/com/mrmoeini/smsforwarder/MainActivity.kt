package com.mrmoeini.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val rules = mutableListOf<Rule>()
    private lateinit var adapter: RuleAdapter
    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView

    // State for the rule dialog's "pick contact" buttons.
    private var senderField: EditText? = null
    private var destField: EditText? = null
    private var pickingSender = false
    private var pickedSenderName = ""
    private var pickedSenderNumber = ""
    private var pickedDestName = ""
    private var pickedDestNumber = ""

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS
    )

    private var userTappedBanner = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshUi()
            // If the user asked for the permission and the system still did not grant it
            // (denied before, or restricted for sideloaded apps), send them to app settings.
            if (userTappedBanner && missingPermissions().isNotEmpty()) openAppSettings()
            userTappedBanner = false
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
                        val number = Rule.toAsciiNumber(c.getString(0) ?: "")
                        val name = c.getString(1) ?: ""
                        if (pickingSender) {
                            pickedSenderNumber = number
                            pickedSenderName = name
                            senderField?.setText(number)
                        } else {
                            pickedDestNumber = number
                            pickedDestName = name
                            destField?.setText(number)
                        }
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
            onEdit = { showRuleDialog(it) }
        )

        val rv = findViewById<RecyclerView>(R.id.rvRules)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        attachSwipeToDelete(rv)

        findViewById<View>(R.id.btnAdd).setOnClickListener { showRuleDialog(null) }
        findViewById<View>(R.id.btnLog).setOnClickListener { showLog() }

        tvStatus.setOnClickListener {
            val missing = missingPermissions()
            if (missing.isEmpty()) {
                openAppSettings()
            } else {
                userTappedBanner = true
                permLauncher.launch(missing.toTypedArray())
            }
        }

        if (!hasPermissions()) permLauncher.launch(requiredPermissions)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun missingPermissions(): List<String> = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun hasPermissions(): Boolean = missingPermissions().isEmpty()

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun refreshUi() {
        val missing = missingPermissions()
        tvStatus.text = if (missing.isEmpty()) {
            getString(R.string.perm_missing)
        } else {
            getString(R.string.perm_missing) + "\n(" +
                missing.joinToString(", ") { it.substringAfterLast('.') } + ")"
        }
        tvStatus.visibility = if (missing.isEmpty()) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persist() {
        RuleStore.save(this, rules)
        adapter.notifyDataSetChanged()
        refreshUi()
    }

    private fun launchContactPicker(forSender: Boolean) {
        pickingSender = forSender
        try {
            pickContact.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.err_no_contacts, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRuleDialog(existing: Rule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etSender = view.findViewById<EditText>(R.id.etSender)
        val etKeyword = view.findViewById<EditText>(R.id.etKeyword)
        val etDest = view.findViewById<EditText>(R.id.etDest)
        val btnPickSender = view.findViewById<Button>(R.id.btnPickSender)
        val btnPickDest = view.findViewById<Button>(R.id.btnPick)

        pickedSenderName = existing?.senderName ?: ""
        pickedSenderNumber = existing?.sender ?: ""
        pickedDestName = existing?.destName ?: ""
        pickedDestNumber = existing?.destNumber ?: ""
        if (existing != null) {
            etName.setText(existing.name)
            etSender.setText(existing.sender)
            etKeyword.setText(existing.keyword)
            etDest.setText(existing.destNumber)
        }
        senderField = etSender
        destField = etDest

        btnPickSender.setOnClickListener { launchContactPicker(true) }
        btnPickDest.setOnClickListener { launchContactPicker(false) }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_rule else R.string.edit_rule)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener {
            senderField = null
            destField = null
        }
        dialog.show()

        // Set the click listener after show() so invalid input does not close the dialog.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val sender = Rule.cleanSender(etSender.text.toString())
            val dest = Rule.toAsciiNumber(etDest.text.toString())
            if (sender.isEmpty()) {
                etSender.error = getString(R.string.err_sender)
                return@setOnClickListener
            }
            if (dest.isEmpty()) {
                etDest.error = getString(R.string.err_dest)
                return@setOnClickListener
            }
            val senderName = if (sender == pickedSenderNumber) pickedSenderName else ""
            val destName = if (dest == pickedDestNumber) pickedDestName else ""
            val name = etName.text.toString().trim()
            val keyword = etKeyword.text.toString().trim()

            if (existing == null) {
                rules.add(
                    Rule(
                        name = name, sender = sender, senderName = senderName,
                        keyword = keyword, destNumber = dest, destName = destName
                    )
                )
            } else {
                val i = rules.indexOfFirst { it.id == existing.id }
                if (i >= 0) {
                    rules[i] = rules[i].copy(
                        name = name, sender = sender, senderName = senderName,
                        keyword = keyword, destNumber = dest, destName = destName
                    )
                }
            }
            persist()
            dialog.dismiss()
        }
    }

    private fun confirmDelete(rule: Rule, onCancel: (() -> Unit)? = null) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setPositiveButton(R.string.delete) { _, _ ->
                rules.removeAll { it.id == rule.id }
                persist()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel?.invoke() }
            .setOnCancelListener { onCancel?.invoke() }
            .show()
    }

    private fun showLog() {
        val lines = RuleStore.readLog(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.log)
            .setMessage(if (lines.isEmpty()) getString(R.string.log_empty) else lines.joinToString("\n\n"))
            .setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.clear_log) { _, _ -> RuleStore.clearLog(this) }
            .show()
    }

    /** iOS-style swipe left/right on a rule to reveal a red Delete action. */
    private fun attachSwipeToDelete(rv: RecyclerView) {
        val density = resources.displayMetrics.density
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.ios_red)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16f * density
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val callback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || pos >= rules.size) return
                confirmDelete(rules[pos]) { adapter.notifyItemChanged(pos) }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val v = viewHolder.itemView
                if (dX != 0f) {
                    val r = if (dX < 0) {
                        RectF(v.right + dX, v.top.toFloat(), v.right.toFloat(), v.bottom.toFloat())
                    } else {
                        RectF(v.left.toFloat(), v.top.toFloat(), v.left + dX, v.bottom.toFloat())
                    }
                    c.drawRoundRect(r, 12 * density, 12 * density, bgPaint)
                    if (r.width() > 72 * density) {
                        val y = r.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                        c.drawText(getString(R.string.delete), r.centerX(), y, textPaint)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }
}
