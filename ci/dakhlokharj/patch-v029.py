from pathlib import Path

# v0.2.9: fix bank SMS reception/parsing compatibility for store review.

manifest = Path("dakhlokharj/app/src/main/AndroidManifest.xml")
manifest_text = manifest.read_text(encoding="utf-8")
manifest_text = manifest_text.replace(
    '            android:exported="true"\n            android:permission="android.permission.BROADCAST_SMS">',
    '            android:exported="true">'
)
if 'android:permission="android.permission.BROADCAST_SMS"' in manifest_text:
    raise SystemExit("Failed to remove BROADCAST_SMS receiver restriction")
if 'android.permission.RECEIVE_SMS' not in manifest_text:
    raise SystemExit("RECEIVE_SMS permission unexpectedly missing")
manifest.write_text(manifest_text, encoding="utf-8")

receiver = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsReceiver.kt")
receiver_text = receiver.read_text(encoding="utf-8")
old_join = 'val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }'
new_join = 'val body = messages.joinToString(separator = "\\n") { it.messageBody.orEmpty() }'
if old_join in receiver_text:
    receiver_text = receiver_text.replace(old_join, new_join)
elif new_join not in receiver_text:
    raise SystemExit("BankSmsReceiver multipart join anchor not found")
receiver.write_text(receiver_text, encoding="utf-8")

parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
ptext = parser.read_text(encoding="utf-8")

old_gate = '''        if (!looksLikeBankSender(sender, bankFromSender) && !hasStrongBankBody(normalized, bankFromBody)) {
            return null
        }'''
new_gate = '''        if (
            !looksLikeBankSender(sender, bankFromSender) &&
            !hasStrongBankBody(normalized, bankFromBody) &&
            !hasExplicitTransactionContext(normalized)
        ) {
            return null
        }'''
if old_gate in ptext:
    ptext = ptext.replace(old_gate, new_gate)
elif "hasExplicitTransactionContext(normalized)" not in ptext:
    raise SystemExit("Bank sender gate anchor not found")

anchor = '''    private fun normalizeSender(sender: String?): String =
        sender.orEmpty().lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
'''
helper = '''    private fun hasExplicitTransactionContext(text: String): Boolean {
        val promoWords = listOf(
            "تخفیف", "کد تخفیف", "جشنواره", "فروش ویژه", "پیشنهاد ویژه", "قرعه کشی", "قرعه‌کشی"
        )
        if (promoWords.any { text.contains(it, ignoreCase = true) }) return false

        val hasTransactionWord = (incomeWords + expenseWords + genericExpenseWords)
            .any { text.contains(it, ignoreCase = true) }
        val hasCurrency = listOf("ریال", "تومان", "تومن", "irr", "rial")
            .any { text.contains(it, ignoreCase = true) }
        val hasAccountSignal = listOf(
            "کارت", "حساب", "سپرده", "مانده", "موجودی", "شبا", "پایا", "ساتنا", "خودپرداز", "پایانه"
        ).any { text.contains(it, ignoreCase = true) }

        return hasTransactionWord && (hasCurrency || hasAccountSignal)
    }

''' + anchor
if "private fun hasExplicitTransactionContext" not in ptext:
    if anchor not in ptext:
        raise SystemExit("normalizeSender anchor not found")
    ptext = ptext.replace(anchor, helper)

# Accept Arabic comma separators and the common colloquial currency spelling.
ptext = ptext.replace("[0-9,٬.]*", "[0-9,٬،.]*")
ptext = ptext.replace("(ریال|تومان)", "(ریال|تومان|تومن)")
ptext = ptext.replace(
    'raw.replace(",", "").replace("٬", "").replace(".", "")',
    'raw.replace(",", "").replace("٬", "").replace("،", "").replace(".", "")'
)
ptext = ptext.replace(
    '            "تومان" -> value * 10L\n            "ریال" -> value',
    '            "تومان", "تومن" -> value * 10L\n            "ریال" -> value'
)
if '"تومان", "تومن" -> value * 10L' not in ptext:
    raise SystemExit("Toman/Tomen currency patch failed")
if 'replace("،", "")' not in ptext:
    raise SystemExit("Arabic comma amount patch failed")
parser.write_text(ptext, encoding="utf-8")

test = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
ttext = test.read_text(encoding="utf-8")
extra_tests = r'''
    @Test
    fun acceptsTerseBankTransactionFromFullPhoneSender() {
        val parsed = BankSmsParser.parse(
            "09121234567",
            "برداشت 1,250,000 ریال",
            101L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(1_250_000L, parsed.amountToman)
    }

    @Test
    fun acceptsArabicCommaAndTomenSpelling() {
        val parsed = BankSmsParser.parse(
            "30001234",
            "واریز مبلغ ۱۲۵،۰۰۰ تومن به حساب شما",
            102L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(1_250_000L, parsed.amountToman)
    }

    @Test
    fun ignoresPromotionalPurchaseSms() {
        val parsed = BankSmsParser.parse(
            "09121234567",
            "خرید ویژه با تخفیف 100,000 ریال فقط امروز",
            103L
        )
        assertNull(parsed)
    }
'''
if "acceptsTerseBankTransactionFromFullPhoneSender" not in ttext:
    ttext = ttext.replace("\n}\n", extra_tests + "\n}\n")
test.write_text(ttext, encoding="utf-8")

app = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/AppUi.kt")
app_text = app.read_text(encoding="utf-8")
app_text = app_text.replace('item { Text("نسخه ۰.۲.۸") }', 'item { Text("نسخه ۰.۲.۹") }')
app.write_text(app_text, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 10", "versionCode = 11")
gtext = gtext.replace('versionName = "0.2.8"', 'versionName = "0.2.9"')
if "versionCode = 11" not in gtext or 'versionName = "0.2.9"' not in gtext:
    raise SystemExit("v0.2.9 version bump failed")
gradle.write_text(gtext, encoding="utf-8")
