package com.mrmoeini.smsforwarder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CallAnswerActivity : BaseListActivity<AnswerRule>() {

    override val titleRes = R.string.call_answering

    override val requiredPermissions: Array<String>
        get() {
            val l = mutableListOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ANSWER_PHONE_CALLS
            )
            if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
            return l.toTypedArray()
        }

    private val handler = Handler(Looper.getMainLooper())
    private var preview: MediaPlayer? = null
    private var audioCallback: ((Uri) -> Unit)? = null
    private var pendingAfterMic: (() -> Unit)? = null

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) audioCallback?.invoke(uri)
        }

    private val micLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val p = pendingAfterMic
            pendingAfterMic = null
            if (granted) p?.invoke() else toast(R.string.err_mic_perm)
        }

    override fun loadItems(): MutableList<AnswerRule> = AnswerRuleStore.load(this)
    override fun saveItems() = AnswerRuleStore.save(this, items)
    override fun idOf(item: AnswerRule) = item.id
    override fun enabledOf(item: AnswerRule) = item.enabled
    override fun withEnabled(item: AnswerRule, enabled: Boolean) = item.copy(enabled = enabled)

    private fun whoText(item: AnswerRule): String =
        if (item.allCalls) getString(R.string.ca_everyone)
        else item.callers.joinToString(", ") { if (it.name.isNotBlank()) it.name else it.number }

    override fun titleOf(item: AnswerRule): String =
        if (item.name.isNotBlank()) item.name else whoText(item)

    override fun detailOf(item: AnswerRule): String {
        val time = if (item.timeFrom >= 0 && item.timeTo >= 0) {
            TimeUtil.format(item.timeFrom) + " - " + TimeUtil.format(item.timeTo)
        } else getString(R.string.ca_any_time)
        val exp = if (item.dateFrom > 0L || item.dateTo > 0L) {
            DateUtil.format(item.dateFrom) + " -> " + DateUtil.format(item.dateTo)
        } else getString(R.string.ca_no_expiry)
        return whoText(item) + "\n" + time + "  |  " + exp + "\n" + item.audioName
    }

    override fun onItemRemoved(item: AnswerRule) {
        AnswerRuleStore.deleteAudioIfUnused(this, item.audioFile)
    }

    override fun setUpExtra(container: LinearLayout) {
        val v = layoutInflater.inflate(R.layout.layout_answer_header, container, false)
        container.addView(v)
        val sw = v.findViewById<SwitchMaterial>(R.id.swMaster)
        sw.isChecked = AnswerRuleStore.isEnabled(this)
        sw.setOnCheckedChangeListener { _, on ->
            AnswerRuleStore.setEnabled(this, on)
            if (on) {
                requestMissingPermissions()
                try {
                    AnswerService.start(this)
                } catch (e: Exception) {
                    Toast.makeText(this, e.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
            } else {
                AnswerService.stop(this)
            }
        }
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- audio helpers

    private fun importAudio(uri: Uri): Pair<String, String>? {
        return try {
            var display = "audio"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) display = c.getString(0) ?: display
            }
            val ext = display.substringAfterLast('.', "").lowercase(Locale.US)
                .filter { it.isLetterOrDigit() }.take(5).ifEmpty { "audio" }
            val name = "aud_" + UUID.randomUUID().toString().take(8) + "." + ext
            val out = AnswerRuleStore.audioFile(this, name)
            val input = contentResolver.openInputStream(uri) ?: return null
            input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            Pair(name, display)
        } catch (e: Exception) {
            null
        }
    }

    private fun startPreview(file: File, onEnd: () -> Unit) {
        stopPreview()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                stopPreview()
                onEnd()
            }
            mp.prepare()
            mp.start()
            preview = mp
        } catch (e: Exception) {
            stopPreview()
            toast(R.string.err_audio_copy)
        }
        onEnd()
    }

    private fun stopPreview() {
        try {
            preview?.stop()
        } catch (_: Exception) {
        }
        try {
            preview?.release()
        } catch (_: Exception) {
        }
        preview = null
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(file: File): MediaRecorder {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96000)
        r.setAudioSamplingRate(44100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        return r
    }

    private fun startRecordingFlow(onSaved: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAfterMic = { startRecordingFlow(onSaved) }
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val name = "rec_" + UUID.randomUUID().toString().take(8) + ".m4a"
        val file = AnswerRuleStore.audioFile(this, name)
        val recorder = try {
            newRecorder(file)
        } catch (e: Exception) {
            file.delete()
            toast(R.string.err_record)
            return
        }
        fun stopRecorder() {
            try {
                recorder.stop()
            } catch (_: RuntimeException) {
            }
            try {
                recorder.release()
            } catch (_: Exception) {
            }
        }

        val startTime = SystemClock.elapsedRealtime()
        val tv = TextView(this)
        val pad = (24 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        tv.textSize = 20f
        val ticker = object : Runnable {
            override fun run() {
                val sec = (SystemClock.elapsedRealtime() - startTime) / 1000
                tv.text = getString(R.string.ca_recording, TimeUtil.mmss(sec))
                handler.postDelayed(this, 500)
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.ca_record)
            .setView(tv)
            .setCancelable(false)
            .setPositiveButton(R.string.ca_stop_save) { _, _ ->
                stopRecorder()
                onSaved(name)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                stopRecorder()
                file.delete()
            }
            .create()
        dialog.setOnDismissListener { handler.removeCallbacks(ticker) }
        dialog.show()
        ticker.run()
    }

    // ---------------------------------------------------------------- editor

    override fun showEditor(existing: AnswerRule?) {
        val view = layoutInflater.inflate(R.layout.dialog_answer_rule, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val swAll = view.findViewById<SwitchMaterial>(R.id.swAllCalls)
        val callersBox = view.findViewById<View>(R.id.callersBox)
        val tvCallers = view.findViewById<TextView>(R.id.tvCallers)
        val btnAddContact = view.findViewById<Button>(R.id.btnAddContact)
        val btnAddNumber = view.findViewById<Button>(R.id.btnAddNumber)
        val btnClear = view.findViewById<Button>(R.id.btnClearCallers)
        val swTime = view.findViewById<SwitchMaterial>(R.id.swTime)
        val timeBox = view.findViewById<View>(R.id.timeBox)
        val tvTimeFrom = view.findViewById<TextView>(R.id.tvTimeFrom)
        val tvTimeTo = view.findViewById<TextView>(R.id.tvTimeTo)
        val swDate = view.findViewById<SwitchMaterial>(R.id.swDate)
        val dateBox = view.findViewById<View>(R.id.dateBox)
        val tvDateFrom = view.findViewById<TextView>(R.id.tvDateFrom)
        val tvDateTo = view.findViewById<TextView>(R.id.tvDateTo)
        val tvAudio = view.findViewById<TextView>(R.id.tvAudio)
        val btnChoose = view.findViewById<Button>(R.id.btnChooseAudio)
        val btnRecord = view.findViewById<Button>(R.id.btnRecord)
        val btnPlay = view.findViewById<Button>(R.id.btnPlay)
        val etDelay = view.findViewById<EditText>(R.id.etDelay)

        val callers = (existing?.callers ?: emptyList()).toMutableList()
        var timeFrom = if (existing != null && existing.timeFrom >= 0) existing.timeFrom else 8 * 60
        var timeTo = if (existing != null && existing.timeTo >= 0) existing.timeTo else 17 * 60
        val now = System.currentTimeMillis()
        var dateFrom = if (existing != null && existing.dateFrom > 0L) existing.dateFrom
        else DateUtil.startOfDay(now)
        var dateTo = if (existing != null && existing.dateTo > 0L) existing.dateTo
        else DateUtil.endOfDay(now + 30L * 24 * 3600 * 1000)
        var audioFile = existing?.audioFile ?: ""
        var audioName = existing?.audioName ?: ""
        val created = mutableListOf<String>()
        var saved = false

        etName.setText(existing?.name ?: "")
        etDelay.setText((existing?.delaySec ?: 3).toString())
        swAll.isChecked = existing?.allCalls ?: true
        swTime.isChecked = existing != null && existing.timeFrom >= 0 && existing.timeTo >= 0
        swDate.isChecked = existing != null && (existing.dateFrom > 0L || existing.dateTo > 0L)

        fun refresh() {
            callersBox.visibility = if (swAll.isChecked) View.GONE else View.VISIBLE
            tvCallers.text = if (callers.isEmpty()) getString(R.string.ca_no_callers)
            else callers.joinToString("\n") {
                if (it.name.isNotBlank()) "${it.name} (${it.number})" else it.number
            }
            timeBox.visibility = if (swTime.isChecked) View.VISIBLE else View.GONE
            tvTimeFrom.text = getString(R.string.cf_from) + "  " + TimeUtil.format(timeFrom)
            tvTimeTo.text = getString(R.string.cf_to) + "  " + TimeUtil.format(timeTo)
            dateBox.visibility = if (swDate.isChecked) View.VISIBLE else View.GONE
            tvDateFrom.text = getString(R.string.ca_date_from, DateUtil.format(dateFrom))
            tvDateTo.text = getString(R.string.ca_date_to, DateUtil.format(dateTo))
            tvAudio.text = if (audioFile.isBlank()) getString(R.string.ca_no_audio) else audioName
            btnPlay.text = getString(if (preview != null) R.string.ca_stop else R.string.ca_play)
        }
        refresh()

        swAll.setOnCheckedChangeListener { _, _ -> refresh() }
        swTime.setOnCheckedChangeListener { _, _ -> refresh() }
        swDate.setOnCheckedChangeListener { _, _ -> refresh() }

        btnAddContact.setOnClickListener {
            pickContact { number, name ->
                if (number.isNotEmpty() && callers.none { it.number == number }) {
                    callers.add(Caller(number, name))
                }
                refresh()
            }
        }
        btnAddNumber.setOnClickListener {
            val et = EditText(this)
            et.inputType = InputType.TYPE_CLASS_PHONE
            et.hint = getString(R.string.ca_enter_number)
            val pad = (20 * resources.displayMetrics.density).toInt()
            et.setPadding(pad, pad, pad, pad)
            AlertDialog.Builder(this)
                .setTitle(R.string.ca_enter_number)
                .setView(et)
                .setPositiveButton(R.string.save) { _, _ ->
                    val n = Rule.toAsciiNumber(et.text.toString())
                    if (n.isNotEmpty() && callers.none { it.number == n }) callers.add(Caller(n, ""))
                    refresh()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        btnClear.setOnClickListener {
            callers.clear()
            refresh()
        }

        tvTimeFrom.setOnClickListener { TimeUtil.pickTime(this, timeFrom) { timeFrom = it; refresh() } }
        tvTimeTo.setOnClickListener { TimeUtil.pickTime(this, timeTo) { timeTo = it; refresh() } }
        tvDateFrom.setOnClickListener {
            DateUtil.pick(this, dateFrom) { dateFrom = DateUtil.startOfDay(it); refresh() }
        }
        tvDateTo.setOnClickListener {
            DateUtil.pick(this, dateTo) { dateTo = DateUtil.endOfDay(it); refresh() }
        }

        btnChoose.setOnClickListener {
            stopPreview()
            audioCallback = { uri ->
                val r = importAudio(uri)
                if (r == null) {
                    toast(R.string.err_audio_copy)
                } else {
                    created.add(r.first)
                    audioFile = r.first
                    audioName = r.second
                    refresh()
                }
            }
            audioPicker.launch(arrayOf("audio/*"))
        }
        btnRecord.setOnClickListener {
            stopPreview()
            startRecordingFlow { fileName ->
                created.add(fileName)
                audioFile = fileName
                audioName = getString(R.string.ca_record) + " " +
                    SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())
                refresh()
            }
        }
        btnPlay.setOnClickListener {
            if (preview != null) {
                stopPreview()
                refresh()
            } else if (audioFile.isNotBlank()) {
                startPreview(AnswerRuleStore.audioFile(this, audioFile)) { refresh() }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_rule else R.string.edit_rule)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnDismissListener {
            stopPreview()
            if (!saved) created.forEach { AnswerRuleStore.audioFile(this, it).delete() }
        }
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val allCalls = swAll.isChecked
            if (!allCalls && callers.isEmpty()) {
                toast(R.string.err_callers)
                return@setOnClickListener
            }
            if (audioFile.isBlank()) {
                toast(R.string.err_audio)
                return@setOnClickListener
            }
            if (swDate.isChecked && dateTo < dateFrom) {
                toast(R.string.err_dates)
                return@setOnClickListener
            }
            val delay = (etDelay.text.toString().toIntOrNull() ?: 3).coerceIn(0, 60)
            val rule = AnswerRule(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = etName.text.toString().trim(),
                enabled = existing?.enabled ?: true,
                allCalls = allCalls,
                callers = callers.toList(),
                timeFrom = if (swTime.isChecked) timeFrom else -1,
                timeTo = if (swTime.isChecked) timeTo else -1,
                dateFrom = if (swDate.isChecked) dateFrom else 0L,
                dateTo = if (swDate.isChecked) dateTo else 0L,
                audioFile = audioFile,
                audioName = audioName,
                delaySec = delay
            )
            if (existing == null) {
                items.add(rule)
            } else {
                val i = items.indexOfFirst { it.id == existing.id }
                if (i >= 0) items[i] = rule
            }
            saved = true
            persist()
            created.filter { it != audioFile }.forEach { AnswerRuleStore.audioFile(this, it).delete() }
            if (existing != null && existing.audioFile != audioFile) {
                AnswerRuleStore.deleteAudioIfUnused(this, existing.audioFile)
            }
            dialog.dismiss()
        }
    }
}
