package com.mrmoeini.smsforwarder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject
import java.util.UUID

/** iOS-style form to create / edit one forwarding rule. */
class RuleEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RULE = "rule"
    }

    private lateinit var etName: EditText
    private lateinit var etSender: EditText
    private lateinit var etKeyword: EditText
    private lateinit var etRegex: EditText
    private lateinit var tvAction: TextView
    private lateinit var smsBox: View
    private lateinit var apiBox: View
    private lateinit var etDest: EditText
    private lateinit var etUrl: EditText
    private lateinit var tvMethod: TextView
    private lateinit var tvFormat: TextView
    private lateinit var customBox: View
    private lateinit var etContentType: EditText
    private lateinit var etBody: EditText
    private lateinit var tvAuth: TextView
    private lateinit var etTokenUrl: EditText
    private lateinit var etAuthUser: EditText
    private lateinit var etAuthName: EditText
    private lateinit var etAuthSecret: EditText
    private lateinit var etScope: EditText
    private lateinit var etHeaders: EditText
    private lateinit var etTimeout: EditText
    private lateinit var swInsecure: SwitchMaterial
    private lateinit var btnTestApi: Button

    private var existing: Rule? = null
    private var action = Rule.ACTION_SMS
    private var methodIdx = 0
    private var format = ApiConfig.FORMAT_TEXT
    private var authType = ApiConfig.AUTH_NONE
    private var pickedSenderName = ""
    private var pickedSenderNumber = ""
    private var pickedDestName = ""
    private var pickedDestNumber = ""
    private var contactCallback: ((String, String) -> Unit)? = null

    private val contactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null) {
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        contactCallback?.invoke(
                            Rule.toAsciiNumber(c.getString(0) ?: ""), c.getString(1) ?: ""
                        )
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_editor)

        etName = findViewById(R.id.etName)
        etSender = findViewById(R.id.etSender)
        etKeyword = findViewById(R.id.etKeyword)
        etRegex = findViewById(R.id.etRegex)
        tvAction = findViewById(R.id.tvAction)
        smsBox = findViewById(R.id.smsBox)
        apiBox = findViewById(R.id.apiBox)
        etDest = findViewById(R.id.etDest)
        etUrl = findViewById(R.id.etUrl)
        tvMethod = findViewById(R.id.tvMethod)
        tvFormat = findViewById(R.id.tvFormat)
        customBox = findViewById(R.id.customBox)
        etContentType = findViewById(R.id.etContentType)
        etBody = findViewById(R.id.etBody)
        tvAuth = findViewById(R.id.tvAuth)
        etTokenUrl = findViewById(R.id.etTokenUrl)
        etAuthUser = findViewById(R.id.etAuthUser)
        etAuthName = findViewById(R.id.etAuthName)
        etAuthSecret = findViewById(R.id.etAuthSecret)
        etScope = findViewById(R.id.etScope)
        etHeaders = findViewById(R.id.etHeaders)
        etTimeout = findViewById(R.id.etTimeout)
        swInsecure = findViewById(R.id.swInsecure)
        btnTestApi = findViewById(R.id.btnTestApi)

        val json = intent.getStringExtra(EXTRA_RULE)
        if (json != null) {
            try {
                existing = Rule.fromJson(JSONObject(json))
            } catch (_: Exception) {
            }
        }
        findViewById<TextView>(R.id.tvEditorTitle)
            .setText(if (existing == null) R.string.new_rule else R.string.edit_rule)

        existing?.let { r ->
            etName.setText(r.name)
            etSender.setText(r.sender)
            etKeyword.setText(r.keyword)
            etRegex.setText(r.regex)
            etDest.setText(r.destNumber)
            action = r.action
            pickedSenderName = r.senderName
            pickedSenderNumber = r.sender
            pickedDestName = r.destName
            pickedDestNumber = r.destNumber
            val a = r.api
            etUrl.setText(a.url)
            methodIdx = ApiConfig.METHODS.indexOf(a.method).coerceAtLeast(0)
            format = a.format
            etBody.setText(a.bodyTemplate)
            etContentType.setText(a.contentType)
            authType = a.authType
            etTokenUrl.setText(a.tokenUrl)
            etAuthUser.setText(a.authUser)
            etAuthName.setText(a.authName)
            etAuthSecret.setText(a.authSecret)
            etScope.setText(a.scope)
            etHeaders.setText(a.headers)
            etTimeout.setText(a.timeoutSec.toString())
            swInsecure.isChecked = a.insecureSsl
        }
        if (existing == null) {
            etContentType.setText("application/json")
            etTimeout.setText("15")
        }

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnSave).setOnClickListener { save() }

        findViewById<Button>(R.id.btnPickSender).setOnClickListener {
            pickContact { number, name ->
                pickedSenderNumber = number
                pickedSenderName = name
                etSender.setText(number)
            }
        }
        findViewById<Button>(R.id.btnPickDest).setOnClickListener {
            pickContact { number, name ->
                pickedDestNumber = number
                pickedDestName = name
                etDest.setText(number)
            }
        }
        findViewById<Button>(R.id.btnTestRegex).setOnClickListener { testRegex() }
        btnTestApi.setOnClickListener { testApi() }

        tvAction.setOnClickListener {
            val labels = arrayOf(getString(R.string.action_sms), getString(R.string.action_api))
            pickOption(R.string.sec_action, labels, action) { action = it; refreshUi() }
        }
        tvMethod.setOnClickListener {
            pickOption(R.string.sec_api, ApiConfig.METHODS, methodIdx) { methodIdx = it; refreshUi() }
        }
        tvFormat.setOnClickListener {
            pickOption(R.string.sec_api, formatLabels(), format) { format = it; refreshUi() }
        }
        tvAuth.setOnClickListener {
            pickOption(R.string.sec_auth, authLabels(), authType) { authType = it; refreshUi() }
        }
        refreshUi()
    }

    private fun formatLabels() = arrayOf(
        getString(R.string.fmt_text), getString(R.string.fmt_json),
        getString(R.string.fmt_form), getString(R.string.fmt_custom)
    )

    private fun authLabels() = arrayOf(
        getString(R.string.auth_none), getString(R.string.auth_basic), getString(R.string.auth_bearer),
        getString(R.string.auth_key_header), getString(R.string.auth_key_query), getString(R.string.auth_oauth2)
    )

    private fun pickOption(title: Int, labels: Array<String>, current: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, current) { d, which ->
                onPick(which)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickContact(callback: (String, String) -> Unit) {
        contactCallback = callback
        try {
            contactLauncher.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            )
        } catch (e: Exception) {
            Toast.makeText(this, R.string.err_no_contacts, Toast.LENGTH_SHORT).show()
        }
    }

    private fun show(v: View, visible: Boolean) {
        v.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun refreshUi() {
        tvAction.text = getString(if (action == Rule.ACTION_API) R.string.action_api else R.string.action_sms)
        show(smsBox, action == Rule.ACTION_SMS)
        show(apiBox, action == Rule.ACTION_API)

        tvMethod.text = getString(R.string.method_label, ApiConfig.METHODS[methodIdx])
        tvFormat.text = getString(R.string.format_label, formatLabels()[format.coerceIn(0, 3)])
        val isGet = ApiConfig.METHODS[methodIdx] == "GET"
        show(tvFormat, !isGet)
        show(customBox, !isGet && format == ApiConfig.FORMAT_CUSTOM)

        tvAuth.text = getString(R.string.auth_label, authLabels()[authType.coerceIn(0, 5)])
        show(etTokenUrl, authType == ApiConfig.AUTH_OAUTH2)
        show(etScope, authType == ApiConfig.AUTH_OAUTH2)
        show(etAuthUser, authType == ApiConfig.AUTH_BASIC || authType == ApiConfig.AUTH_OAUTH2)
        show(etAuthName, authType == ApiConfig.AUTH_KEY_HEADER || authType == ApiConfig.AUTH_KEY_QUERY)
        show(etAuthSecret, authType != ApiConfig.AUTH_NONE)
        when (authType) {
            ApiConfig.AUTH_BASIC -> {
                etAuthUser.hint = getString(R.string.hint_username)
                etAuthSecret.hint = getString(R.string.hint_password)
            }
            ApiConfig.AUTH_BEARER -> etAuthSecret.hint = getString(R.string.hint_token)
            ApiConfig.AUTH_KEY_HEADER -> {
                etAuthName.hint = getString(R.string.hint_key_header)
                etAuthSecret.hint = getString(R.string.hint_api_key)
            }
            ApiConfig.AUTH_KEY_QUERY -> {
                etAuthName.hint = getString(R.string.hint_key_query)
                etAuthSecret.hint = getString(R.string.hint_api_key)
            }
            ApiConfig.AUTH_OAUTH2 -> {
                etAuthUser.hint = getString(R.string.hint_client_id)
                etAuthSecret.hint = getString(R.string.hint_client_secret)
            }
        }
    }

    private fun readApi(): ApiConfig = ApiConfig(
        url = etUrl.text.toString().trim(),
        method = ApiConfig.METHODS[methodIdx],
        format = format,
        bodyTemplate = etBody.text.toString(),
        contentType = etContentType.text.toString().trim().ifEmpty { "application/json" },
        authType = authType,
        authUser = etAuthUser.text.toString().trim(),
        authSecret = etAuthSecret.text.toString().trim(),
        authName = etAuthName.text.toString().trim(),
        tokenUrl = etTokenUrl.text.toString().trim(),
        scope = etScope.text.toString().trim(),
        headers = etHeaders.text.toString(),
        timeoutSec = (etTimeout.text.toString().toIntOrNull() ?: 15).coerceIn(1, 120),
        insecureSsl = swInsecure.isChecked
    )

    private fun validUrl(u: String): Boolean =
        u.startsWith("http://", true) || u.startsWith("https://", true)

    private fun save() {
        val sender = Rule.cleanSender(etSender.text.toString())
        if (sender.isEmpty()) {
            etSender.error = getString(R.string.err_sender)
            return
        }
        val regex = etRegex.text.toString().trim()
        if (regex.isNotEmpty()) {
            try {
                java.util.regex.Pattern.compile(regex)
            } catch (e: Exception) {
                etRegex.error = getString(R.string.err_regex, e.message?.lineSequence()?.firstOrNull() ?: "")
                return
            }
        }
        val dest = Rule.toAsciiNumber(etDest.text.toString())
        val api = readApi()
        if (action == Rule.ACTION_SMS && dest.isEmpty()) {
            etDest.error = getString(R.string.err_dest)
            return
        }
        if (action == Rule.ACTION_API && !validUrl(api.url)) {
            etUrl.error = getString(R.string.err_url)
            return
        }
        if (action == Rule.ACTION_API && authType == ApiConfig.AUTH_OAUTH2 && !validUrl(api.tokenUrl)) {
            etTokenUrl.error = getString(R.string.err_url)
            return
        }
        val rule = Rule(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = etName.text.toString().trim(),
            sender = sender,
            senderName = if (sender == pickedSenderNumber) pickedSenderName else "",
            keyword = etKeyword.text.toString().trim(),
            destNumber = dest,
            destName = if (dest == pickedDestNumber) pickedDestName else "",
            enabled = existing?.enabled ?: true,
            regex = regex,
            action = action,
            api = api
        )
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RULE, rule.toJson().toString()))
        finish()
    }

    private fun testRegex() {
        val pattern = etRegex.text.toString().trim()
        if (pattern.isEmpty()) {
            etRegex.error = getString(R.string.regex_hint)
            return
        }
        val sample = EditText(this)
        sample.hint = getString(R.string.regex_sample_hint)
        sample.minLines = 3
        sample.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        val pad = (20 * resources.displayMetrics.density).toInt()
        sample.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle(R.string.btn_test_regex)
            .setView(sample)
            .setPositiveButton(R.string.regex_test_run) { _, _ ->
                val msg = try {
                    val r = Rule.applyRegex(pattern, sample.text.toString())
                    if (r == null) getString(R.string.regex_no_match) else getString(R.string.regex_result, r)
                } catch (e: Exception) {
                    getString(R.string.err_regex, e.message?.lineSequence()?.firstOrNull() ?: "")
                }
                AlertDialog.Builder(this).setMessage(msg).setPositiveButton(R.string.close, null).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun testApi() {
        val api = readApi()
        if (!validUrl(api.url)) {
            etUrl.error = getString(R.string.err_url)
            return
        }
        btnTestApi.isEnabled = false
        btnTestApi.setText(R.string.test_running)
        val app = applicationContext
        val tmp = Rule(name = "test", sender = "TEST", keyword = "", action = Rule.ACTION_API, api = api)
        Thread {
            val res = ApiClient.send(
                app, tmp, "TEST", "This is a test message from SMS Forwarder",
                System.currentTimeMillis(), SystemClock.elapsedRealtime() + 20000L, true
            )
            runOnUiThread {
                btnTestApi.isEnabled = true
                btnTestApi.setText(R.string.btn_test_api)
                AlertDialog.Builder(this)
                    .setTitle(if (res.success) R.string.test_ok else R.string.test_failed)
                    .setMessage(res.message)
                    .setPositiveButton(R.string.close, null)
                    .show()
            }
        }.start()
    }
}
