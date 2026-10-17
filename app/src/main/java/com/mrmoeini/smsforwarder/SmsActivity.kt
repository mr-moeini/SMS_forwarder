package com.mrmoeini.smsforwarder

import android.Manifest
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog

class SmsActivity : BaseListActivity<Rule>() {

    override val titleRes = R.string.sms_forwarder
    override val requiredPermissions =
        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)

    override fun loadItems(): MutableList<Rule> = RuleStore.load(this)
    override fun saveItems() = RuleStore.save(this, items)
    override fun idOf(item: Rule) = item.id
    override fun titleOf(item: Rule) = if (item.name.isNotBlank()) item.name else item.sender
    override fun enabledOf(item: Rule) = item.enabled
    override fun withEnabled(item: Rule, enabled: Boolean) = item.copy(enabled = enabled)

    override fun detailOf(item: Rule): String {
        val from = if (item.senderName.isNotBlank()) "${item.senderName} (${item.sender})" else item.sender
        val dest = if (item.destName.isNotBlank()) "${item.destName} (${item.destNumber})" else item.destNumber
        var detail = getString(R.string.rule_detail, from, dest)
        if (item.keyword.isNotBlank()) detail += "\n" + getString(R.string.rule_keyword, item.keyword)
        return detail
    }

    override fun showEditor(existing: Rule?) {
        val view = layoutInflater.inflate(R.layout.dialog_rule, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etSender = view.findViewById<EditText>(R.id.etSender)
        val etKeyword = view.findViewById<EditText>(R.id.etKeyword)
        val etDest = view.findViewById<EditText>(R.id.etDest)
        val btnPickSender = view.findViewById<Button>(R.id.btnPickSender)
        val btnPickDest = view.findViewById<Button>(R.id.btnPick)

        var pickedSenderName = existing?.senderName ?: ""
        var pickedSenderNumber = existing?.sender ?: ""
        var pickedDestName = existing?.destName ?: ""
        var pickedDestNumber = existing?.destNumber ?: ""
        if (existing != null) {
            etName.setText(existing.name)
            etSender.setText(existing.sender)
            etKeyword.setText(existing.keyword)
            etDest.setText(existing.destNumber)
        }

        btnPickSender.setOnClickListener {
            pickContact { number, name ->
                pickedSenderNumber = number
                pickedSenderName = name
                etSender.setText(number)
            }
        }
        btnPickDest.setOnClickListener {
            pickContact { number, name ->
                pickedDestNumber = number
                pickedDestName = name
                etDest.setText(number)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_rule else R.string.edit_rule)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()

        // Set the listener after show() so invalid input does not close the dialog.
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
                items.add(
                    Rule(
                        name = name, sender = sender, senderName = senderName,
                        keyword = keyword, destNumber = dest, destName = destName
                    )
                )
            } else {
                val i = items.indexOfFirst { it.id == existing.id }
                if (i >= 0) {
                    items[i] = items[i].copy(
                        name = name, sender = sender, senderName = senderName,
                        keyword = keyword, destNumber = dest, destName = destName
                    )
                }
            }
            persist()
            dialog.dismiss()
        }
    }
}
