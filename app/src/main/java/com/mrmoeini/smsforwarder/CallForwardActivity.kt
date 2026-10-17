package com.mrmoeini.smsforwarder

import android.Manifest
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class CallForwardActivity : BaseListActivity<CallForwardRule>() {

    override val titleRes = R.string.call_forwarder
    override val requiredPermissions = arrayOf(Manifest.permission.CALL_PHONE)

    private var tvStatus: TextView? = null

    override fun loadItems(): MutableList<CallForwardRule> = CallRuleStore.load(this)
    override fun saveItems() = CallRuleStore.save(this, items)
    override fun idOf(item: CallForwardRule) = item.id
    override fun enabledOf(item: CallForwardRule) = item.enabled
    override fun withEnabled(item: CallForwardRule, enabled: Boolean) = item.copy(enabled = enabled)

    private fun modeLabel(mode: Int): String = getString(
        when (mode) {
            1 -> R.string.cf_mode_busy
            2 -> R.string.cf_mode_noanswer
            3 -> R.string.cf_mode_unreach
            else -> R.string.cf_mode_all
        }
    )

    override fun titleOf(item: CallForwardRule): String {
        if (item.name.isNotBlank()) return item.name
        return if (item.destName.isNotBlank()) item.destName else item.destNumber
    }

    override fun detailOf(item: CallForwardRule): String {
        val dest = if (item.destName.isNotBlank()) "${item.destName} (${item.destNumber})" else item.destNumber
        return modeLabel(item.mode) + " -> " + dest + "\n" +
            TimeUtil.format(item.startMin) + " - " + TimeUtil.format(item.endMin) + "  |  " +
            TimeUtil.daysText(item.days, getString(R.string.cf_every_day))
    }

    override fun setUpExtra(container: LinearLayout) {
        val v = layoutInflater.inflate(R.layout.layout_cf_header, container, false)
        container.addView(v)
        tvStatus = v.findViewById(R.id.tvCfStatus)
        v.findViewById<Button>(R.id.btnTurnOff).setOnClickListener {
            CallForwardScheduler.turnOffAll(this) { refreshStatus() }
        }
        refreshStatus()
    }

    private fun refreshStatus() {
        tvStatus?.text = getString(R.string.cf_status, CallForwardScheduler.statusText(this))
    }

    override fun onResume() {
        super.onResume()
        CallForwardScheduler.reconcile(this) { refreshStatus() }
    }

    override fun onItemsChanged() {
        CallForwardScheduler.reconcile(this) { refreshStatus() }
    }

    override fun showEditor(existing: CallForwardRule?) {
        val view = layoutInflater.inflate(R.layout.dialog_call_rule, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etDest = view.findViewById<EditText>(R.id.etDest)
        val btnPick = view.findViewById<Button>(R.id.btnPick)
        val tvMode = view.findViewById<TextView>(R.id.tvMode)
        val tvFrom = view.findViewById<TextView>(R.id.tvFrom)
        val tvTo = view.findViewById<TextView>(R.id.tvTo)
        val tvDays = view.findViewById<TextView>(R.id.tvDays)

        var mode = existing?.mode ?: 0
        var startMin = existing?.startMin ?: (8 * 60)
        var endMin = existing?.endMin ?: (17 * 60)
        var days = existing?.days ?: 127
        var pickedName = existing?.destName ?: ""
        var pickedNumber = existing?.destNumber ?: ""
        if (existing != null) {
            etName.setText(existing.name)
            etDest.setText(existing.destNumber)
        }

        fun refresh() {
            tvMode.text = modeLabel(mode)
            tvFrom.text = getString(R.string.cf_from) + "  " + TimeUtil.format(startMin)
            tvTo.text = getString(R.string.cf_to) + "  " + TimeUtil.format(endMin)
            tvDays.text = getString(R.string.cf_days) + "  " +
                TimeUtil.daysText(days, getString(R.string.cf_every_day))
        }
        refresh()

        tvMode.setOnClickListener {
            val labels = Array(4) { modeLabel(it) }
            AlertDialog.Builder(this)
                .setTitle(R.string.cf_mode)
                .setSingleChoiceItems(labels, mode) { d, which ->
                    mode = which
                    refresh()
                    d.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        tvFrom.setOnClickListener { TimeUtil.pickTime(this, startMin) { startMin = it; refresh() } }
        tvTo.setOnClickListener { TimeUtil.pickTime(this, endMin) { endMin = it; refresh() } }
        tvDays.setOnClickListener { TimeUtil.pickDays(this, days) { days = it; refresh() } }
        btnPick.setOnClickListener {
            pickContact { number, name ->
                pickedNumber = number
                pickedName = name
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

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val dest = Rule.toAsciiNumber(etDest.text.toString())
            if (dest.isEmpty()) {
                etDest.error = getString(R.string.err_dest)
                return@setOnClickListener
            }
            val destName = if (dest == pickedNumber) pickedName else ""
            val rule = CallForwardRule(
                id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                name = etName.text.toString().trim(),
                destNumber = dest,
                destName = destName,
                mode = mode,
                startMin = startMin,
                endMin = endMin,
                days = days,
                enabled = existing?.enabled ?: true
            )
            if (existing == null) {
                items.add(rule)
            } else {
                val i = items.indexOfFirst { it.id == existing.id }
                if (i >= 0) items[i] = rule
            }
            persist()
            dialog.dismiss()
        }
    }
}
