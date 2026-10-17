# Call and SMS Forwarder

Android app (iOS-style UI) with:
- **SMS Forwarder** - forward SMS from a number / sender ID (optionally containing a keyword) to another number.
- **Call Forwarder** - switch carrier call forwarding on/off on a schedule (time window + days).
- **Call Answering** - auto-answer calls (all callers or chosen people, time window, date range / no expiry) and play a chosen or recorded voice message, then hang up.
- **Backup / Restore** - one zip file with every rule and recorded message.
- **About** - Developed by : MR Moeini - https://www.linkedin.com/in/mohammad-reza-moeini-83088987


Android app that forwards incoming SMS automatically based on rules.
اپلیکیشن اندروید برای فوروارد خودکار پیامک بر اساس قانون.

## How it works / نحوهٔ کار
- Add a rule: **sender** (phone number or alphanumeric sender ID such as a bank name), optional **keyword**, and a **destination** number (typed or picked from contacts).
- When an SMS arrives from that sender (and contains the keyword, if set), it is re-sent to the destination as `[sender] message`.
- Numbers are compared by their last 10 digits, so `+98912...`, `0912...` and `98912...` all match. Sender IDs are compared case-insensitively.
- Menu -> **Forward log** shows the last 50 forwards (numbers and result only, never message text).

## Notes / نکات مهم
- Needs the `RECEIVE_SMS` and `SEND_SMS` permissions. On Android 13+ for apps installed from an APK: App info -> three dots -> **Allow restricted settings**, then grant SMS.
- Disable battery optimization for the app, and enable autostart on Xiaomi/Huawei/Oppo-style ROMs.
- Each forward is a normal outgoing SMS and may be charged by your carrier. Persian text uses 70 characters per part.
- For personal use / sideloading. Google Play does not allow this kind of SMS permission use.

## Build
Every push to `main` runs `.github/workflows/build.yml` and uploads `SMS_forwarder-debug-apk`.
Actions tab -> latest run -> Artifacts.

## Troubleshooting / عیب‌یابی
Open menu -> **Forward log**. Each incoming SMS writes `RECV from <sender> rules=N matched=M`:
- No `RECV` line at all: the system never delivered the SMS to the app. Check the SMS permission (and on Xiaomi/MIUI the separate "Receive SMS"/"Send SMS" and Autostart switches) and disable battery optimization. Chat/RCS messages are not SMS and are never delivered to apps.
- `RECV ... matched=0`: the sender in your rule does not match the real sender shown in the log line. Copy it exactly.
- `queued` followed by `SENT OK`: forwarding worked. `FAILED ...` shows the real error.
