# SMS Forwarder

Android app (iOS-style UI) that forwards incoming SMS according to rules.

## Rules
Each rule has a **sender** (phone number or sender ID such as a bank name; pick from contacts or type), an optional **keyword** and an optional **regex**, and one **action**:

1. **Forward SMS to a number** - re-sends the text as `[sender] text`.
2. **Send to an API server** - HTTP request with the SMS text. The text always starts with:
   ```
   Devicename : <device name>
   Udid: <udid>
   <sms text>
   ```
   - Methods: POST, PUT, PATCH, GET
   - Body: plain text, JSON, form, or a custom template (`{{device_name}} {{udid}} {{sender}} {{message}} {{text}} {{timestamp}}`, add `_json` to JSON-escape)
   - Authentication: none, Basic, Bearer token, API key (header or query), OAuth 2.0 client credentials
   - Extra headers, timeout, optional "ignore SSL errors", "Send Test Request" button
   - Failed requests are retried automatically (network required, exponential backoff).

**Regex**: if set, only the first match is forwarded - group 1 if the pattern has a capture group, otherwise the whole match. If it does not match, the message is not forwarded. Use the *Test Regex* button to try it.

**Device Identity** (home screen): device name and UDID sent to the API. Default UDID is the Android ID; type the adb serial if it must equal Appium's `udid`.

**Backup / Restore**: one JSON file with all rules (it contains API credentials - keep it safe).

## Notes
- Needs `RECEIVE_SMS`, `SEND_SMS` (SMS-to-number action) and `INTERNET` (API action).
- On Android 13+ for APKs installed from outside a store: App info -> three dots -> *Allow restricted settings*, then allow SMS.
- Menu "Activity Log" shows every received SMS (sender only) and the result of each forward.

## Build
Every push to `main` runs `.github/workflows/build.yml` and uploads `SMS_forwarder-debug-apk` (Actions -> latest run -> Artifacts).

Developed by : MR Moeini - https://www.linkedin.com/in/mohammad-reza-moeini-83088987

## Language, theme and help (v2.0)
- First launch asks for a language (English or فارسی); change it anytime in Settings - the whole app switches immediately, independent of the phone's system language.
- Settings also has a dark/light/system toggle and four accent colors (Blue, Green, Purple, Orange).
- Every key field in the rule editor has a small (i) icon - tap it for a short, localized explanation and a concrete example.
- A dedicated Help screen (home screen's "?" icon, or Settings) explains rules, regex, API setup and permissions in the current language.
- The package name is now `com.mrmoeini.smsforward`, distinct from the Uniqa Client app, so both can be installed on the same phone without conflicting.

## Release signing (for Play Store)
Debug builds are signed with Android's default debug key - fine for testing, never valid for a Play Store release. A separate release keystore is used instead, supplied to CI only through GitHub Actions secrets (`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`). It is never committed to this repo. Once those secrets are set, every push also produces a signed `SMS_forwarder-release-apk` and a `SMS_forwarder-release-aab` (the format Play Store requires for new apps) as workflow artifacts. Keep the keystore file and its passwords permanently and privately - losing them means the app can never be updated again under the same listing; leaking them lets someone else publish updates as you.
