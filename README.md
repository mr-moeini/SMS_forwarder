# SMS_forwarder

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
