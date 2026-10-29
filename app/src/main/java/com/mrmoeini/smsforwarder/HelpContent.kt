package com.mrmoeini.smsforwarder

/** One bit of field-level guidance: a short title, a one-line explanation, and a concrete example. */
data class FieldHelpText(val title: String, val desc: String, val example: String)

data class HelpSection(val title: String, val body: String)

/** All help text, in English and Persian, looked up by a stable key. */
object HelpContent {

    fun field(key: String, persian: Boolean): FieldHelpText = (if (persian) fa else en)[key]
        ?: FieldHelpText(key, "", "")

    fun sections(persian: Boolean): List<HelpSection> = if (persian) faSections else enSections

    // ------------------------------------------------------------------ English

    private val en: Map<String, FieldHelpText> = mapOf(
        "sender" to FieldHelpText(
            "Sender",
            "The phone number or sender ID the SMS must come from. Numbers are matched by their last 10 digits, so the country code is optional. Sender IDs (bank names etc.) are matched as text, not as a number.",
            "+989121234567   or   09121234567   or   BankMelli"
        ),
        "keyword" to FieldHelpText(
            "Contains (optional)",
            "Only forward the SMS if its text contains this word or phrase. Leave empty to match every message from this sender.",
            "verification code"
        ),
        "regex" to FieldHelpText(
            "Regex (optional)",
            "A regular expression applied to the SMS text before forwarding. If the pattern has a capture group in parentheses, only that group is sent - otherwise the whole match is sent. If it does not match, the message is not forwarded.",
            "\\d{4,6}   -> sends just the numeric code from a message like \"Your code is 482913\""
        ),
        "dest" to FieldHelpText(
            "Forward to",
            "The phone number that receives the forwarded SMS. Pick a contact or type a number.",
            "09123456789"
        ),
        "api_url" to FieldHelpText(
            "API URL",
            "The full address of your server's endpoint. Must start with http:// or https://.",
            "https://api.example.com/sms-hook"
        ),
        "api_body" to FieldHelpText(
            "Body template (Custom format)",
            "The exact text sent as the request body. Use placeholders that get replaced automatically; add _json to a placeholder to JSON-escape its value.",
            "{\"from\":\"{{sender_json}}\",\"text\":\"{{message_json}}\"}"
        ),
        "api_headers" to FieldHelpText(
            "Extra headers",
            "One custom HTTP header per line, as Name: value.",
            "X-Source: sms-forwarder\nX-Env: production"
        ),
        "api_content_type" to FieldHelpText(
            "Content-Type",
            "The MIME type sent with a Custom body, so the server knows how to parse it.",
            "application/json"
        ),
        "api_timeout" to FieldHelpText(
            "Timeout",
            "How many seconds to wait for the server before giving up and retrying later.",
            "15"
        )
    )

    private val enSections: List<HelpSection> = listOf(
        HelpSection(
            "Creating a rule",
            "Open SMS Forwarder, tap +, fill in who the SMS is from (Sender), optionally a Keyword or Regex to only forward matching messages, then choose where it goes: another phone number, or an API server."
        ),
        HelpSection(
            "Regex basics",
            "A regex picks part of the message. \\d+ means \"one or more digits\". If your pattern has (parentheses), only the part inside them is forwarded - handy for extracting just a verification code."
        ),
        HelpSection(
            "Sending to an API",
            "Choose \"Send to an API server\", enter the URL, pick a method and body format, and set authentication if your server needs it (Basic, Bearer token, API key, or OAuth 2.0). Use \"Send Test Request\" to check it works before relying on it."
        ),
        HelpSection(
            "Device Identity",
            "Every API message starts with the device name and a UDID. You can override both on the home screen if your server expects a specific value."
        ),
        HelpSection(
            "Backup and Restore",
            "Backup saves every rule (including API passwords/tokens) to one file - keep it private. Restore replaces all current rules with the ones in a chosen backup file."
        ),
        HelpSection(
            "Why does my antivirus warn about this app?",
            "Any app that reads and sends SMS looks like the pattern used by SMS-stealing malware, so scanners flag it automatically - even though this app only acts on the rules you create, and never reads message text into any log."
        ),
        HelpSection(
            "Permissions",
            "SMS permissions are needed to receive and forward messages. On some phones you must also allow \"restricted settings\" from the app's system info page, and disable battery optimization so forwarding keeps working in the background."
        )
    )

    // ------------------------------------------------------------------ Persian

    private val fa: Map<String, FieldHelpText> = mapOf(
        "sender" to FieldHelpText(
            "فرستنده",
            "شماره یا شناسهٔ فرستنده‌ای که پیامک باید از آن بیاید. شماره‌ها با ۱۰ رقم آخرشان مقایسه می‌شوند، پس کد کشور اختیاری است. شناسه‌های متنی (مثل نام بانک) به‌صورت متن مقایسه می‌شوند.",
            "+989121234567   یا   09121234567   یا   BankMelli"
        ),
        "keyword" to FieldHelpText(
            "شامل (اختیاری)",
            "فقط وقتی پیامک فوروارد شود که متنش شامل این کلمه یا عبارت باشد. اگر خالی بماند، هر پیامی از این فرستنده فوروارد می‌شود.",
            "کد تایید"
        ),
        "regex" to FieldHelpText(
            "Regex (اختیاری)",
            "یک عبارت باقاعده که قبل از ارسال روی متن پیامک اعمال می‌شود. اگر الگو یک گروه با پرانتز داشته باشد، فقط همان بخش ارسال می‌شود، وگرنه کل بخش منطبق‌شده. اگر الگو match نشود، پیام اصلاً فوروارد نمی‌شود.",
            "\\d{4,6}   ← فقط کد عددی را از پیامی مثل «کد شما 482913 است» می‌فرستد"
        ),
        "dest" to FieldHelpText(
            "ارسال به",
            "شماره‌ای که پیامک فورواردشده را دریافت می‌کند. از مخاطبین انتخاب کنید یا دستی بنویسید.",
            "09123456789"
        ),
        "api_url" to FieldHelpText(
            "آدرس API",
            "آدرس کامل endpoint سرور شما. باید با http:// یا https:// شروع شود.",
            "https://api.example.com/sms-hook"
        ),
        "api_body" to FieldHelpText(
            "قالب بدنه (فرمت سفارشی)",
            "متن دقیقی که به‌عنوان بدنهٔ درخواست ارسال می‌شود. از جایگزین‌ها استفاده کنید؛ برای escape شدن مقدار در JSON پسوند _json اضافه کنید.",
            "{\"from\":\"{{sender_json}}\",\"text\":\"{{message_json}}\"}"
        ),
        "api_headers" to FieldHelpText(
            "هدرهای اضافه",
            "هر خط یک هدر سفارشی HTTP، به شکل Name: value.",
            "X-Source: sms-forwarder\nX-Env: production"
        ),
        "api_content_type" to FieldHelpText(
            "Content-Type",
            "نوع MIME ارسالی با بدنهٔ سفارشی، تا سرور بداند چطور آن را بخواند.",
            "application/json"
        ),
        "api_timeout" to FieldHelpText(
            "Timeout",
            "چند ثانیه منتظر پاسخ سرور بماند قبل از اینکه تلاش را رها کند و بعداً دوباره امتحان کند.",
            "15"
        )
    )

    private val faSections: List<HelpSection> = listOf(
        HelpSection(
            "ساخت یک قانون",
            "فوروارد پیامک را باز کنید، روی + بزنید، فرستنده را مشخص کنید، اختیاری یک کلیدواژه یا regex برای محدود کردن پیام‌ها بگذارید، و بعد مقصد را انتخاب کنید: یک شمارهٔ دیگر یا یک سرور API."
        ),
        HelpSection(
            "اصول Regex",
            "regex بخشی از پیام را انتخاب می‌کند. \\d+ یعنی «یک یا چند رقم». اگر الگوی شما (پرانتز) دارد، فقط همان بخش ارسال می‌شود؛ این برای استخراج فقط کد تایید مفید است."
        ),
        HelpSection(
            "ارسال به API",
            "«ارسال به سرور API» را انتخاب کنید، آدرس را بنویسید، متد و فرمت بدنه را انتخاب کنید، و اگر سرورتان نیاز دارد احراز هویت (Basic، Bearer، API key، یا OAuth 2.0) را تنظیم کنید. قبل از اعتماد به آن، با «ارسال درخواست آزمایشی» تست کنید."
        ),
        HelpSection(
            "مشخصات دستگاه",
            "هر پیام API با نام دستگاه و یک UDID شروع می‌شود. اگر سرورتان مقدار خاصی می‌خواهد، می‌توانید هر دو را از صفحهٔ اصلی تغییر دهید."
        ),
        HelpSection(
            "پشتیبان‌گیری و بازیابی",
            "پشتیبان‌گیری همهٔ قانون‌ها (از جمله رمز/توکن API) را در یک فایل ذخیره می‌کند؛ آن را جای امن نگه دارید. بازیابی همهٔ قانون‌های فعلی را با محتوای فایل پشتیبان جایگزین می‌کند."
        ),
        HelpSection(
            "چرا آنتی‌ویروس به این اپ هشدار می‌دهد؟",
            "هر اپی که پیامک بخواند و بفرستد شبیه الگوی بدافزارهای سرقت پیامک به نظر می‌رسد، پس آنتی‌ویروس‌ها خودکار هشدار می‌دهند؛ با این حال این اپ فقط طبق قانون‌هایی که خودتان می‌سازید عمل می‌کند و متن پیام را هیچ‌جا لاگ نمی‌کند."
        ),
        HelpSection(
            "مجوزها",
            "برای دریافت و فوروارد پیامک، دسترسی SMS لازم است. روی بعضی گوشی‌ها باید «تنظیمات محدودشده» را هم از صفحهٔ اطلاعات سیستمی اپ مجاز کنید، و بهینه‌سازی باتری را خاموش کنید تا فوروارد در پس‌زمینه کار کند."
        )
    )
}
