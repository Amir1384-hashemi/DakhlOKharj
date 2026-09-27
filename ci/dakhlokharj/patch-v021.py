from pathlib import Path

app = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/AppUi.kt")
text = app.read_text()
text = text.replace("import android.icu.util.PersianCalendar\n", "import java.util.Calendar\n")

old = """private fun reportBounds(period: ReportPeriod): Pair<Long, Long> {
    if (period == ReportPeriod.ALL) return 0L to Long.MAX_VALUE
    val now = PersianCalendar()
    val start = PersianCalendar().apply {
        timeInMillis = now.timeInMillis
        when (period) {
            ReportPeriod.TODAY -> Unit
            ReportPeriod.WEEK -> set(PersianCalendar.DAY_OF_WEEK, firstDayOfWeek)
            ReportPeriod.MONTH -> set(PersianCalendar.DAY_OF_MONTH, 1)
            ReportPeriod.YEAR -> {
                set(PersianCalendar.MONTH, 0)
                set(PersianCalendar.DAY_OF_MONTH, 1)
            }
            ReportPeriod.ALL -> Unit
        }
        set(PersianCalendar.HOUR_OF_DAY, 0)
        set(PersianCalendar.MINUTE, 0)
        set(PersianCalendar.SECOND, 0)
        set(PersianCalendar.MILLISECOND, 0)
    }
    return start.timeInMillis to System.currentTimeMillis()
}
"""

new = """private fun reportBounds(period: ReportPeriod): Pair<Long, Long> {
    if (period == ReportPeriod.ALL) return 0L to Long.MAX_VALUE

    val now = Calendar.getInstance()
    val start = Calendar.getInstance()

    when (period) {
        ReportPeriod.TODAY -> Unit
        ReportPeriod.WEEK -> {
            val daysSinceSaturday = (now.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
            start.add(Calendar.DAY_OF_MONTH, -daysSinceSaturday)
        }
        ReportPeriod.MONTH, ReportPeriod.YEAR -> {
            val today = gregorianToJalali(
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH)
            )
            val targetMonth = if (period == ReportPeriod.YEAR) 1 else today.month
            val (gy, gm, gd) = jalaliToGregorian(today.year, targetMonth, 1)
            start.set(Calendar.YEAR, gy)
            start.set(Calendar.MONTH, gm - 1)
            start.set(Calendar.DAY_OF_MONTH, gd)
        }
        ReportPeriod.ALL -> Unit
    }

    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)
    return start.timeInMillis to System.currentTimeMillis()
}
"""
if old not in text:
    raise SystemExit("Expected reportBounds block not found")
app.write_text(text.replace(old, new))

fmt = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/Formatters.kt")
ftext = fmt.read_text()
anchor = "fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {"
if "fun jalaliToGregorian(" not in ftext:
    fn = """fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
    var y = jy + 1595
    var days = -355668 + 365 * y + (y / 33) * 8 + ((y % 33 + 3) / 4) + jd
    days += if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186

    var gy = 400 * (days / 146097)
    days %= 146097

    if (days > 36524) {
        days--
        gy += 100 * (days / 36524)
        days %= 36524
        if (days >= 365) days++
    }

    gy += 4 * (days / 1461)
    days %= 1461

    if (days > 365) {
        gy += (days - 1) / 365
        days = (days - 1) % 365
    }

    var gd = days + 1
    val leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
    val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gm = 1
    while (gm <= 12 && gd > monthDays[gm]) {
        gd -= monthDays[gm]
        gm++
    }
    return Triple(gy, gm, gd)
}

"""
    if anchor not in ftext:
        raise SystemExit("Formatter anchor not found")
    fmt.write_text(ftext.replace(anchor, fn + anchor))


# v0.2.2: show income categories in reports
text = app.read_text()
old_agg = '''    val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amountToman }
    val expense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountToman }
    val groups = filtered.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryName ?: "بدون دسته" }
        .mapValues { (_, list) -> list.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }
    val max = groups.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
'''
new_agg = '''    val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amountToman }
    val expense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountToman }
    val incomeGroups = filtered.filter { it.type == TransactionType.INCOME }
        .groupBy { it.categoryName ?: "بدون دسته" }
        .mapValues { (_, list) -> list.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }
    val expenseGroups = filtered.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryName ?: "بدون دسته" }
        .mapValues { (_, list) -> list.sumOf { it.amountToman } }
        .toList().sortedByDescending { it.second }
    val incomeMax = incomeGroups.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
    val expenseMax = expenseGroups.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
'''
if old_agg in text:
    text = text.replace(old_agg, new_agg)

old_ui = '''        item { SummaryCard("مانده", income - expense, Modifier.fillMaxWidth()) }
        item { Text("هزینه‌ها بر اساس دسته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (groups.isEmpty()) item { EmptyState("در این بازه هزینه‌ای ثبت نشده است.") }
        else items(groups) { group ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(group.first, Modifier.weight(1f))
                    Text(formatToman(group.second), fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { (group.second.toFloat() / max.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }
'''
new_ui = '''        item { SummaryCard("مانده", income - expense, Modifier.fillMaxWidth()) }

        item { Text("درآمدها بر اساس دسته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (incomeGroups.isEmpty()) item { EmptyState("در این بازه درآمدی ثبت نشده است.") }
        else items(incomeGroups) { group ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(group.first, Modifier.weight(1f))
                    Text(formatToman(group.second), fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { (group.second.toFloat() / incomeMax.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }

        item { Text("هزینه‌ها بر اساس دسته", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (expenseGroups.isEmpty()) item { EmptyState("در این بازه هزینه‌ای ثبت نشده است.") }
        else items(expenseGroups) { group ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(group.first, Modifier.weight(1f))
                    Text(formatToman(group.second), fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { (group.second.toFloat() / expenseMax.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        }
'''
if old_ui in text:
    text = text.replace(old_ui, new_ui)

text = text.replace('item { Text("نسخه ۰.۲.۱") }', 'item { Text("نسخه ۰.۲.۲") }')
app.write_text(text)

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text()
gtext = gtext.replace("versionCode = 3", "versionCode = 4")
gtext = gtext.replace('versionName = "0.2.1"', 'versionName = "0.2.2"')
gradle.write_text(gtext)


# v0.2.3: store-release privacy hardening
app_text = app.read_text()
app_text = app_text.replace('item { Text("نسخه ۰.۲.۲") }', 'item { Text("نسخه ۰.۲.۳") }')
app.write_text(app_text)

manifest = Path("dakhlokharj/app/src/main/AndroidManifest.xml")
mtext = manifest.read_text()
mtext = mtext.replace('android:allowBackup="true"', 'android:allowBackup="false"')
if 'android:usesCleartextTraffic=' not in mtext:
    mtext = mtext.replace('android:allowBackup="false"\n        android:icon=', 'android:allowBackup="false"\n        android:usesCleartextTraffic="false"\n        android:icon=')
manifest.write_text(mtext)

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text()
gtext = gtext.replace("versionCode = 4", "versionCode = 5")
gtext = gtext.replace('versionName = "0.2.2"', 'versionName = "0.2.3"')
gradle.write_text(gtext)


# v0.2.4: robust bank SMS parsing for store review
parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
parser.write_text(r'''package ir.dakhlokharj.app.sms

import ir.dakhlokharj.app.domain.TransactionType
import java.security.MessageDigest

data class ParsedBankSms(
    val amountToman: Long,
    val type: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val fingerprint: String
)

object BankSmsParser {
    private val incomeWords = listOf(
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "دریافت وجه", "انتقال ورودی",
        "به حساب شما", "به کارت شما", "به سپرده شما"
    )
    private val expenseWords = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "بدهکار", "کسر از", "برداشت از",
        "از حساب شما", "از کارت شما", "از سپرده شما"
    )
    private val genericExpenseWords = listOf(
        "کارت به کارت", "انتقال وجه", "انتقال", "حواله", "پایا", "ساتنا"
    )
    private val securityWords = listOf(
        "رمز", "یکبار مصرف", "یک بار مصرف", "کد ورود", "کد تایید", "کد تأیید",
        "کد امنیتی", "otp", "فعال سازی", "فعالسازی"
    )

    private val knownBanks = linkedMapOf(
        "ملت" to "بانک ملت",
        "ملی" to "بانک ملی",
        "صادرات" to "بانک صادرات",
        "تجارت" to "بانک تجارت",
        "سامان" to "بانک سامان",
        "پاسارگاد" to "بانک پاسارگاد",
        "پارسیان" to "بانک پارسیان",
        "کشاورزی" to "بانک کشاورزی",
        "مسکن" to "بانک مسکن",
        "رفاه" to "بانک رفاه",
        "اقتصاد نوین" to "بانک اقتصاد نوین",
        "شهر" to "بانک شهر",
        "آینده" to "بانک آینده",
        "بلو" to "بلوبانک",
        "رسالت" to "بانک قرض الحسنه رسالت",
        "مهر ایران" to "بانک قرض الحسنه مهر ایران"
    )

    fun parse(sender: String?, body: String, timestampMs: Long): ParsedBankSms? {
        val normalized = normalize(body)
        if (normalized.isBlank() || isSecurityMessage(normalized)) return null

        val type = detectType(normalized) ?: return null
        val amount = extractAmountToman(normalized) ?: return null
        if (amount <= 0L) return null

        val bankName = knownBanks.entries.firstOrNull { normalized.contains(it.key) }?.value
            ?: sender?.takeIf { it.isNotBlank() }
        val last4 = extractLast4(normalized)
        val fingerprint = sha256(sender.orEmpty() + "|" + body + "|" + timestampMs)

        return ParsedBankSms(amount, type, bankName, last4, fingerprint)
    }

    private fun isSecurityMessage(text: String): Boolean =
        securityWords.any { text.contains(it, ignoreCase = true) }

    private fun detectType(text: String): TransactionType? {
        val incomeIndex = firstIndex(text, incomeWords)
        val expenseIndex = firstIndex(text, expenseWords)

        return when {
            incomeIndex != null && expenseIndex != null ->
                if (incomeIndex <= expenseIndex) TransactionType.INCOME else TransactionType.EXPENSE
            incomeIndex != null -> TransactionType.INCOME
            expenseIndex != null -> TransactionType.EXPENSE
            Regex("(?:^|\\s)\\+\\s*[0-9]").containsMatchIn(text) -> TransactionType.INCOME
            Regex("(?:^|\\s)-\\s*[0-9]").containsMatchIn(text) -> TransactionType.EXPENSE
            genericExpenseWords.any { text.contains(it) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    private fun firstIndex(text: String, words: List<String>): Int? =
        words.asSequence().map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()

    private fun extractAmountToman(text: String): Long? {
        val currencyPattern = Regex("([0-9][0-9,٬.]*)\\s*(ریال|تومان)")
        for (match in currencyPattern.findAll(text)) {
            val start = (match.range.first - 28).coerceAtLeast(0)
            val context = text.substring(start, match.range.first)
            if (isBalanceContext(context)) continue
            parseAmount(match.groupValues[1], match.groupValues[2])?.let { return it }
        }

        val labeledPatterns = listOf(
            Regex("(?:مبلغ(?:\\s+(?:تراکنش|خرید|برداشت|واریز|انتقال))?)\\s*[:：=-]?\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?"),
            Regex("(?:برداشت|واریز|خرید|پرداخت|انتقال(?: وجه)?|کارت به کارت)\\s*[:：=-]\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?")
        )
        for (pattern in labeledPatterns) {
            val match = pattern.find(text) ?: continue
            parseAmount(match.groupValues[1], match.groupValues.getOrNull(2).orEmpty())?.let { return it }
        }
        return null
    }

    private fun isBalanceContext(context: String): Boolean {
        val tail = context.takeLast(28)
        return listOf("مانده", "موجودی", "قابل برداشت", "balance")
            .any { tail.contains(it, ignoreCase = true) }
    }

    private fun parseAmount(raw: String, currency: String): Long? {
        val value = raw.replace(",", "").replace("٬", "").replace(".", "").toLongOrNull() ?: return null
        return when (currency) {
            "تومان" -> value
            "ریال" -> value / 10L
            else -> value / 10L
        }
    }

    private fun extractLast4(text: String): String? {
        val direct = Regex("(?:کارت|حساب)[^0-9]{0,22}([0-9]{4})(?![0-9])").find(text)
        if (direct != null) return direct.groupValues[1]

        return Regex("[*xX-]{2,}([0-9]{4})(?![0-9])")
            .find(text)?.groupValues?.getOrNull(1)
    }

    internal fun normalize(input: String): String = buildString(input.length) {
        input.forEach { ch ->
            append(
                when (ch) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    else -> ch
                }
            )
        }
    }.replace('\u200c', ' ').replace(Regex("\\s+"), " ").trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
''', encoding="utf-8")

app_text = app.read_text(encoding="utf-8")
app_text = app_text.replace('item { Text("نسخه ۰.۲.۳") }', 'item { Text("نسخه ۰.۲.۴") }')
app.write_text(app_text, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 5", "versionCode = 6")
gtext = gtext.replace('versionName = "0.2.3"', 'versionName = "0.2.4"')
gradle.write_text(gtext, encoding="utf-8")

test = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(r'''package ir.dakhlokharj.app.sms

import ir.dakhlokharj.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsParserTest {
    @Test
    fun parsesExpenseAndSkipsBalance() {
        val parsed = BankSmsParser.parse(
            "BANK",
            "برداشت از کارت 1234 مبلغ: 1,250,000 ریال مانده: 50,000,000 ریال",
            1L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(125_000L, parsed.amountToman)
    }

    @Test
    fun parsesPersianDigitsIncome() {
        val parsed = BankSmsParser.parse(
            "BANK",
            "واریز ۲٬۵۰۰٬۰۰۰ ریال به حساب شما موجودی ۱۰٬۰۰۰٬۰۰۰ ریال",
            2L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(250_000L, parsed.amountToman)
    }

    @Test
    fun recognizesIncomingCardToCard() {
        val parsed = BankSmsParser.parse(
            "BANK",
            "کارت به کارت به کارت شما مبلغ 3,000,000 ریال",
            3L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
    }

    @Test
    fun recognizesOutgoingTransfer() {
        val parsed = BankSmsParser.parse(
            "BANK",
            "انتقال وجه: 4,000,000 ریال",
            4L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(400_000L, parsed.amountToman)
    }

    @Test
    fun ignoresOtpMessagesEvenWhenTheyMentionPurchase() {
        val parsed = BankSmsParser.parse(
            "BANK",
            "رمز پویا 123456 برای خرید مبلغ 1,000,000 ریال",
            5L
        )
        assertNull(parsed)
    }
}
''', encoding="utf-8")


# v0.2.5: sender-aware bank SMS classification
parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
parser.write_text(r'''package ir.dakhlokharj.app.sms

import ir.dakhlokharj.app.domain.TransactionType
import java.security.MessageDigest

data class ParsedBankSms(
    val amountToman: Long,
    val type: TransactionType,
    val bankName: String?,
    val accountLast4: String?,
    val fingerprint: String
)

object BankSmsParser {
    private val incomeWords = listOf(
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "دریافت وجه", "انتقال ورودی",
        "به حساب شما", "به کارت شما", "به سپرده شما"
    )

    private val expenseWords = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "بدهکار", "کسر از", "برداشت از",
        "از حساب شما", "از کارت شما", "از سپرده شما"
    )

    private val genericExpenseWords = listOf(
        "کارت به کارت", "انتقال وجه", "انتقال", "حواله", "پایا", "ساتنا"
    )

    private val securityWords = listOf(
        "رمز", "رمز پویا", "رمز دوم", "یکبار مصرف", "یک بار مصرف",
        "کد ورود", "کد تایید", "کد تأیید", "کد امنیتی", "otp",
        "فعال سازی", "فعالسازی"
    )

    private val bodyBankNames = linkedMapOf(
        "ملت" to "بانک ملت",
        "ملی" to "بانک ملی",
        "صادرات" to "بانک صادرات",
        "تجارت" to "بانک تجارت",
        "سامان" to "بانک سامان",
        "پاسارگاد" to "بانک پاسارگاد",
        "پارسیان" to "بانک پارسیان",
        "کشاورزی" to "بانک کشاورزی",
        "مسکن" to "بانک مسکن",
        "رفاه" to "بانک رفاه",
        "اقتصاد نوین" to "بانک اقتصاد نوین",
        "شهر" to "بانک شهر",
        "آینده" to "بانک آینده",
        "بلو" to "بلوبانک",
        "رسالت" to "بانک قرض الحسنه رسالت",
        "مهر ایران" to "بانک قرض الحسنه مهر ایران",
        "سپاه" to "بانک سپه",
        "سپه" to "بانک سپه",
        "سینا" to "بانک سینا",
        "دی" to "بانک دی",
        "کارآفرین" to "بانک کارآفرین",
        "ایران زمین" to "بانک ایران زمین",
        "گردشگری" to "بانک گردشگری",
        "پست بانک" to "پست بانک ایران"
    )

    private val senderBankHints = linkedMapOf(
        "mellat" to "بانک ملت",
        "bankmellat" to "بانک ملت",
        "melli" to "بانک ملی",
        "bankmelli" to "بانک ملی",
        "bmi" to "بانک ملی",
        "saderat" to "بانک صادرات",
        "bsi" to "بانک صادرات",
        "tejarat" to "بانک تجارت",
        "saman" to "بانک سامان",
        "pasargad" to "بانک پاسارگاد",
        "bpi" to "بانک پاسارگاد",
        "parsian" to "بانک پارسیان",
        "keshavarzi" to "بانک کشاورزی",
        "bki" to "بانک کشاورزی",
        "maskan" to "بانک مسکن",
        "refah" to "بانک رفاه",
        "enbank" to "بانک اقتصاد نوین",
        "eghtesadnovin" to "بانک اقتصاد نوین",
        "shahr" to "بانک شهر",
        "citybank" to "بانک شهر",
        "ayandeh" to "بانک آینده",
        "blu" to "بلوبانک",
        "resalat" to "بانک قرض الحسنه رسالت",
        "mehriran" to "بانک قرض الحسنه مهر ایران",
        "sepah" to "بانک سپه",
        "sina" to "بانک سینا",
        "daybank" to "بانک دی",
        "karafarin" to "بانک کارآفرین",
        "iranzamin" to "بانک ایران زمین",
        "gardeshgari" to "بانک گردشگری",
        "postbank" to "پست بانک ایران"
    )

    private val strongBodySignals = listOf(
        "بانک", "کارت", "حساب", "سپرده", "مانده", "موجودی",
        "شبا", "پایا", "ساتنا", "خودپرداز", "پایانه", "درگاه", "pos", "atm"
    )

    fun parse(sender: String?, body: String, timestampMs: Long): ParsedBankSms? {
        val normalized = normalize(body)
        if (normalized.isBlank() || isSecurityMessage(normalized)) return null

        val type = detectType(normalized) ?: return null
        val amount = extractAmountToman(normalized) ?: return null
        if (amount <= 0L) return null

        val bankFromBody = bodyBankNames.entries
            .firstOrNull { normalized.contains(it.key, ignoreCase = true) }?.value
        val bankFromSender = bankNameFromSender(sender)

        if (!looksLikeBankSender(sender, bankFromSender) && !hasStrongBankBody(normalized, bankFromBody)) {
            return null
        }

        val bankName = bankFromSender ?: bankFromBody ?: sender?.takeIf { it.isNotBlank() }
        val last4 = extractLast4(normalized)
        val fingerprint = sha256(sender.orEmpty() + "|" + body + "|" + timestampMs)

        return ParsedBankSms(amount, type, bankName, last4, fingerprint)
    }

    private fun isSecurityMessage(text: String): Boolean =
        securityWords.any { text.contains(it, ignoreCase = true) }

    private fun detectType(text: String): TransactionType? {
        val incomeIndex = firstIndex(text, incomeWords)
        val expenseIndex = firstIndex(text, expenseWords)

        return when {
            incomeIndex != null && expenseIndex != null ->
                if (incomeIndex <= expenseIndex) TransactionType.INCOME else TransactionType.EXPENSE
            incomeIndex != null -> TransactionType.INCOME
            expenseIndex != null -> TransactionType.EXPENSE
            Regex("(?:^|\\s)\\+\\s*[0-9]").containsMatchIn(text) -> TransactionType.INCOME
            Regex("(?:^|\\s)-\\s*[0-9]").containsMatchIn(text) -> TransactionType.EXPENSE
            genericExpenseWords.any { text.contains(it) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    private fun firstIndex(text: String, words: List<String>): Int? =
        words.asSequence().map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()

    private fun extractAmountToman(text: String): Long? {
        val currencyPattern = Regex("([0-9][0-9,٬.]*)\\s*(ریال|تومان)")
        for (match in currencyPattern.findAll(text)) {
            val start = (match.range.first - 32).coerceAtLeast(0)
            val context = text.substring(start, match.range.first)
            if (isBalanceContext(context)) continue
            parseAmount(match.groupValues[1], match.groupValues[2])?.let { return it }
        }

        val labeledPatterns = listOf(
            Regex("(?:مبلغ(?:\\s+(?:تراکنش|خرید|برداشت|واریز|انتقال))?)\\s*[:：=-]?\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?"),
            Regex("(?:برداشت|واریز|خرید|پرداخت|انتقال(?: وجه)?|کارت به کارت)\\s*[:：=-]\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?")
        )
        for (pattern in labeledPatterns) {
            val match = pattern.find(text) ?: continue
            parseAmount(match.groupValues[1], match.groupValues.getOrNull(2).orEmpty())?.let { return it }
        }
        return null
    }

    private fun isBalanceContext(context: String): Boolean {
        val tail = context.takeLast(32)
        return listOf("مانده", "موجودی", "قابل برداشت", "balance")
            .any { tail.contains(it, ignoreCase = true) }
    }

    private fun parseAmount(raw: String, currency: String): Long? {
        val value = raw.replace(",", "").replace("٬", "").replace(".", "").toLongOrNull() ?: return null
        return when (currency) {
            "تومان" -> value
            "ریال" -> value / 10L
            else -> value / 10L
        }
    }

    private fun bankNameFromSender(sender: String?): String? {
        val normalizedSender = normalizeSender(sender)
        if (normalizedSender.isBlank()) return null
        return senderBankHints.entries
            .firstOrNull { normalizedSender.contains(it.key) }?.value
    }

    private fun looksLikeBankSender(sender: String?, bankFromSender: String?): Boolean {
        if (bankFromSender != null) return true

        val raw = sender?.trim().orEmpty()
        if (raw.isBlank()) return false

        val digits = raw.filter { it.isDigit() }
        val numericLike = raw.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }

        if (numericLike) {
            // Iranian bank/service SMS often arrives from short numeric service senders.
            // Full phone numbers are not trusted by sender alone.
            return digits.length in 3..9
        }

        // Alphanumeric sender IDs are service-style rather than ordinary personal numbers.
        return raw.length >= 3
    }

    private fun hasStrongBankBody(text: String, bankFromBody: String?): Boolean {
        if (bankFromBody != null) return true

        val hits = strongBodySignals.count { text.contains(it, ignoreCase = true) }
        val hasAccountReference = listOf("کارت", "حساب", "سپرده", "شبا")
            .any { text.contains(it) }
        val hasBalanceReference = listOf("مانده", "موجودی", "قابل برداشت")
            .any { text.contains(it) }

        return hits >= 2 || (hasAccountReference && hasBalanceReference)
    }

    private fun normalizeSender(sender: String?): String =
        sender.orEmpty().lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")

    private fun extractLast4(text: String): String? {
        val direct = Regex("(?:کارت|حساب)[^0-9]{0,22}([0-9]{4})(?![0-9])").find(text)
        if (direct != null) return direct.groupValues[1]

        return Regex("[*xX-]{2,}([0-9]{4})(?![0-9])")
            .find(text)?.groupValues?.getOrNull(1)
    }

    internal fun normalize(input: String): String = buildString(input.length) {
        input.forEach { ch ->
            append(
                when (ch) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    else -> ch
                }
            )
        }
    }.replace('\u200c', ' ').replace(Regex("\\s+"), " ").trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
''', encoding="utf-8")

app_text = app.read_text(encoding="utf-8")
app_text = app_text.replace('item { Text("نسخه ۰.۲.۴") }', 'item { Text("نسخه ۰.۲.۵") }')
app.write_text(app_text, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 6", "versionCode = 7")
gtext = gtext.replace('versionName = "0.2.4"', 'versionName = "0.2.5"')
gradle.write_text(gtext, encoding="utf-8")

test = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(r'''package ir.dakhlokharj.app.sms

import ir.dakhlokharj.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsParserTest {
    @Test
    fun knownBankSenderAcceptsTersePurchase() {
        val parsed = BankSmsParser.parse(
            "BankMellat",
            "خرید 1,250,000 ریال",
            1L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(125_000L, parsed.amountToman)
        assertEquals("بانک ملت", parsed.bankName)
    }

    @Test
    fun shortServiceSenderAcceptsBankLikeMessage() {
        val parsed = BankSmsParser.parse(
            "30001234",
            "برداشت از کارت 1234 مبلغ: 1,250,000 ریال مانده: 50,000,000 ریال",
            2L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(125_000L, parsed.amountToman)
    }

    @Test
    fun personalPhoneNeedsStrongBankBody() {
        val weak = BankSmsParser.parse(
            "09121234567",
            "خرید 100,000 ریال",
            3L
        )
        assertNull(weak)

        val strong = BankSmsParser.parse(
            "09121234567",
            "برداشت از کارت 1234 مبلغ 100,000 ریال مانده 5,000,000 ریال",
            4L
        )
        assertNotNull(strong)
    }

    @Test
    fun parsesPersianDigitsIncome() {
        val parsed = BankSmsParser.parse(
            "Melli",
            "واریز ۲٬۵۰۰٬۰۰۰ ریال به حساب شما موجودی ۱۰٬۰۰۰٬۰۰۰ ریال",
            5L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(250_000L, parsed.amountToman)
        assertEquals("بانک ملی", parsed.bankName)
    }

    @Test
    fun recognizesIncomingCardToCard() {
        val parsed = BankSmsParser.parse(
            "Saman",
            "کارت به کارت به کارت شما مبلغ 3,000,000 ریال",
            6L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
    }

    @Test
    fun recognizesOutgoingTransfer() {
        val parsed = BankSmsParser.parse(
            "Pasargad",
            "انتقال وجه: 4,000,000 ریال",
            7L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(400_000L, parsed.amountToman)
    }

    @Test
    fun ignoresOtpMessagesEvenFromBankSender() {
        val parsed = BankSmsParser.parse(
            "BankMellat",
            "رمز پویا 123456 برای خرید مبلغ 1,000,000 ریال",
            8L
        )
        assertNull(parsed)
    }
}
''', encoding="utf-8")


# v0.2.6: extend transaction vocabulary
parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
ptext = parser.read_text(encoding="utf-8")

ptext = ptext.replace(
'''    private val incomeWords = listOf(
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "دریافت وجه", "انتقال ورودی",
        "به حساب شما", "به کارت شما", "به سپرده شما"
    )''',
'''    private val incomeWords = listOf(
        "واریز گروهی", "واریز سود", "سود سپرده", "دریافت وجه", "دریافت",
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "انتقال ورودی",
        "به حساب شما", "به کارت شما", "به سپرده شما"
    )'''
)

ptext = ptext.replace(
'''    private val expenseWords = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "بدهکار", "کسر از", "برداشت از",
        "از حساب شما", "از کارت شما", "از سپرده شما"
    )''',
'''    private val expenseWords = listOf(
        "پرداخت قبض", "قبض", "کارمزد", "برداشت", "خرید", "پرداخت",
        "کسر", "بدهکار", "کسر از", "برداشت از",
        "از حساب شما", "از کارت شما", "از سپرده شما"
    )'''
)

ptext = ptext.replace(
'''    private val genericExpenseWords = listOf(
        "کارت به کارت", "انتقال وجه", "انتقال", "حواله", "پایا", "ساتنا"
    )''',
'''    private val genericExpenseWords = listOf(
        "کارت به کارت", "انتقال وجه", "انتقال", "حواله", "پایا", "ساتنا"
    )'''
)

ptext = ptext.replace(
'''    private val strongBodySignals = listOf(
        "بانک", "کارت", "حساب", "سپرده", "مانده", "موجودی",
        "شبا", "پایا", "ساتنا", "خودپرداز", "پایانه", "درگاه", "pos", "atm"
    )''',
'''    private val strongBodySignals = listOf(
        "بانک", "کارت", "کارت هدیه", "حساب", "سپرده", "مانده", "موجودی",
        "شبا", "پایا", "ساتنا", "خودپرداز", "پایانه", "درگاه", "قبض",
        "کارمزد", "سود", "pos", "atm"
    )'''
)

ptext = ptext.replace(
'''            Regex("(?:مبلغ(?:\\s+(?:تراکنش|خرید|برداشت|واریز|انتقال))?)\\s*[:：=-]?\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?"),
            Regex("(?:برداشت|واریز|خرید|پرداخت|انتقال(?: وجه)?|کارت به کارت)\\s*[:：=-]\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?")''',
'''            Regex("(?:مبلغ(?:\\s+(?:تراکنش|خرید|برداشت|واریز|انتقال|قبض|کارمزد|دریافت|سود))?)\\s*[:：=-]?\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?"),
            Regex("(?:برداشت|واریز|خرید|پرداخت|انتقال(?: وجه)?|کارت به کارت|قبض|کارمزد|دریافت|سود)\\s*[:：=-]\\s*([0-9][0-9,٬.]*)\\s*(ریال|تومان)?")'''
)

parser.write_text(ptext, encoding="utf-8")

app_text = app.read_text(encoding="utf-8")
app_text = app_text.replace('item { Text("نسخه ۰.۲.۵") }', 'item { Text("نسخه ۰.۲.۶") }')
app.write_text(app_text, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 7", "versionCode = 8")
gtext = gtext.replace('versionName = "0.2.5"', 'versionName = "0.2.6"')
gradle.write_text(gtext, encoding="utf-8")

test = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
ttext = test.read_text(encoding="utf-8")
extra = r'''
    @Test
    fun recognizesBillPaymentAndFeeAsExpense() {
        val bill = BankSmsParser.parse(
            "BankMellat",
            "پرداخت قبض مبلغ 850,000 ریال",
            9L
        )
        assertNotNull(bill)
        assertEquals(TransactionType.EXPENSE, bill!!.type)

        val fee = BankSmsParser.parse(
            "Melli",
            "کارمزد 25,000 ریال از حساب شما کسر شد",
            10L
        )
        assertNotNull(fee)
        assertEquals(TransactionType.EXPENSE, fee!!.type)
    }

    @Test
    fun recognizesProfitAndGroupDepositAsIncome() {
        val profit = BankSmsParser.parse(
            "Saman",
            "واریز سود سپرده مبلغ 2,000,000 ریال",
            11L
        )
        assertNotNull(profit)
        assertEquals(TransactionType.INCOME, profit!!.type)

        val groupDeposit = BankSmsParser.parse(
            "Pasargad",
            "واریز گروهی مبلغ 5,000,000 ریال",
            12L
        )
        assertNotNull(groupDeposit)
        assertEquals(TransactionType.INCOME, groupDeposit!!.type)
    }

    @Test
    fun recognizesReceiveAsIncomeAndKeepsBalanceOutOfAmount() {
        val parsed = BankSmsParser.parse(
            "BankMellat",
            "دریافت 3,000,000 ریال به حساب شما مانده 20,000,000 ریال",
            13L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(300_000L, parsed.amountToman)
    }

    @Test
    fun giftCardAndAtmActAsBankContextSignals() {
        val parsed = BankSmsParser.parse(
            "30009999",
            "برداشت از کارت هدیه در خودپرداز مبلغ 700,000 ریال مانده 1,000,000 ریال",
            14L
        )
        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
    }
'''
if "recognizesBillPaymentAndFeeAsExpense" not in ttext:
    ttext = ttext.replace("\n}\n", extra + "\n}\n")
    test.write_text(ttext, encoding="utf-8")


# v0.2.7: transaction edit/delete, centered FAB, expanded SMS tutorial
# Also fixes v0.2.6 fee parsing: "کارمزد" must not be mistaken for a generic "رمز" security SMS.
parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
ptext = parser.read_text(encoding="utf-8")
ptext = ptext.replace(
'''        "رمز", "رمز پویا", "رمز دوم", "یکبار مصرف", "یک بار مصرف",''',
'''        "رمز پویا", "رمزپویا", "رمز دوم", "یکبار مصرف", "یک بار مصرف",'''
)
ptext = ptext.replace(
'''    private fun isSecurityMessage(text: String): Boolean =
        securityWords.any { text.contains(it, ignoreCase = true) }''',
'''    private fun isSecurityMessage(text: String): Boolean =
        securityWords.any { text.contains(it, ignoreCase = true) } ||
            Regex("(^|[^\\\\p{L}\\\\p{N}])رمز([^\\\\p{L}\\\\p{N}]|$)")
                .containsMatchIn(text)'''
)
parser.write_text(ptext, encoding="utf-8")

# DAO: update and delete transactions without changing the Room schema.
daos = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/data/Daos.kt")
dtext = daos.read_text(encoding="utf-8")
dao_anchor = '''    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET categoryId = :categoryId, status = 'CONFIRMED' WHERE id = :transactionId")
    suspend fun categorize(transactionId: Long, categoryId: Long)
'''
dao_new = '''    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transactions SET categoryId = :categoryId, status = 'CONFIRMED' WHERE id = :transactionId")
    suspend fun categorize(transactionId: Long, categoryId: Long)
'''
if dao_anchor not in dtext:
    raise SystemExit("TransactionDao anchor not found for v0.2.7")
daos.write_text(dtext.replace(dao_anchor, dao_new), encoding="utf-8")

repo_file = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/data/AppRepository.kt")
rtext = repo_file.read_text(encoding="utf-8")
repo_anchor = '''    suspend fun categorize(transactionId: Long, categoryId: Long) = transactions.categorize(transactionId, categoryId)
    suspend fun getTransaction(id: Long): TransactionEntity? = transactions.getById(id)
'''
repo_new = '''    suspend fun categorize(transactionId: Long, categoryId: Long) = transactions.categorize(transactionId, categoryId)
    suspend fun updateTransaction(transaction: TransactionEntity) = transactions.update(transaction)
    suspend fun deleteTransaction(id: Long) = transactions.deleteById(id)
    suspend fun getTransaction(id: Long): TransactionEntity? = transactions.getById(id)
'''
if repo_anchor not in rtext:
    raise SystemExit("Repository anchor not found for v0.2.7")
repo_file.write_text(rtext.replace(repo_anchor, repo_new), encoding="utf-8")

vm = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/MainViewModel.kt")
vtext = vm.read_text(encoding="utf-8")
vm_anchor = '''    fun categorize(transactionId: Long, categoryId: Long) {
        viewModelScope.launch { repo.categorize(transactionId, categoryId) }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = repo.getTransaction(id)
'''
vm_new = '''    fun categorize(transactionId: Long, categoryId: Long) {
        viewModelScope.launch { repo.categorize(transactionId, categoryId) }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch { repo.updateTransaction(transaction) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repo.deleteTransaction(id) }
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = repo.getTransaction(id)
'''
if vm_anchor not in vtext:
    raise SystemExit("ViewModel anchor not found for v0.2.7")
vm.write_text(vtext.replace(vm_anchor, vm_new), encoding="utf-8")

app_text = app.read_text(encoding="utf-8")

# Center the main + button above the bottom navigation.
app_text = app_text.replace(
'''            floatingActionButton = {
                if (showBottom) FloatingActionButton(onClick = { navController.navigate("add") }) { Text("+") }
            }
''',
'''            floatingActionButton = {
                if (showBottom) FloatingActionButton(onClick = { navController.navigate("add") }) { Text("+") }
            },
            floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center
'''
)

# Back button and navigation route for transaction editing.
app_text = app_text.replace(
'''                        if (route.startsWith("categorize/") || route == "add") {''',
'''                        if (route.startsWith("categorize/") || route.startsWith("edit/") || route == "add") {'''
)

app_text = app_text.replace(
'''                composable("add") { AddTransactionScreen(viewModel) { navController.popBackStack() } }
                composable("categorize/{id}") { entry ->''',
'''                composable("add") { AddTransactionScreen(viewModel) { navController.popBackStack() } }
                composable("edit/{id}") { entry ->
                    val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                    EditTransactionScreen(viewModel, id) { navController.popBackStack() }
                }
                composable("categorize/{id}") { entry ->'''
)

app_text = app_text.replace(
'''    route == "add" -> "ثبت دستی"
    route.startsWith("categorize/") -> "دسته‌بندی تراکنش"''',
'''    route == "add" -> "ثبت دستی"
    route.startsWith("edit/") -> "ویرایش تراکنش"
    route.startsWith("categorize/") -> "دسته‌بندی تراکنش"'''
)

# Recent transactions: confirmed items open edit; unclassified items keep the fast categorize flow.
app_text = app_text.replace(
'''            TransactionRow(transaction) {
                if (transaction.status == TransactionStatus.UNCLASSIFIED) navController.navigate("categorize/${transaction.id}")
            }''',
'''            TransactionRow(transaction) {
                if (transaction.status == TransactionStatus.UNCLASSIFIED) {
                    navController.navigate("categorize/${transaction.id}")
                } else {
                    navController.navigate("edit/${transaction.id}")
                }
            }'''
)

# Transactions list: tapping any row opens the edit/delete screen.
app_text = app_text.replace(
'''            TransactionRow(item) {
                if (item.status == TransactionStatus.UNCLASSIFIED) navController.navigate("categorize/${item.id}")
            }''',
'''            TransactionRow(item) {
                navController.navigate("edit/${item.id}")
            }'''
)

edit_screen = r'''
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionScreen(viewModel: MainViewModel, transactionId: Long, onDone: () -> Unit) {
    var transaction by remember(transactionId) { mutableStateOf<TransactionEntity?>(null) }
    var type by remember(transactionId) { mutableStateOf(TransactionType.EXPENSE) }
    var amount by remember(transactionId) { mutableStateOf("") }
    var description by remember(transactionId) { mutableStateOf("") }
    var selectedCategory by remember(transactionId) { mutableStateOf<Long?>(null) }
    var selectedDateMs by remember(transactionId) { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val expenses by viewModel.expenseCategories.collectAsState()
    val incomes by viewModel.incomeCategories.collectAsState()
    val categories = if (type == TransactionType.EXPENSE) expenses else incomes

    LaunchedEffect(transactionId) {
        val loaded = viewModel.getTransaction(transactionId)
        transaction = loaded
        if (loaded != null) {
            type = loaded.type
            amount = loaded.amountToman.toString()
            description = loaded.description.orEmpty()
            selectedCategory = loaded.categoryId
            selectedDateMs = loaded.dateTimeEpochMs
        }
    }

    val current = transaction ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("در حال بارگذاری…") }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("مبلغ، نوع، دسته، تاریخ و توضیح را می‌توانید اصلاح کنید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == TransactionType.EXPENSE,
                    onClick = { type = TransactionType.EXPENSE; selectedCategory = null },
                    label = { Text("هزینه") }
                )
                FilterChip(
                    selected = type == TransactionType.INCOME,
                    onClick = { type = TransactionType.INCOME; selectedCategory = null },
                    label = { Text("درآمد") }
                )
            }
        }
        item {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.toAsciiDigits().filter(Char::isDigit) },
                label = { Text("مبلغ (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("تاریخ وقوع تراکنش: ${formatDateJalali(selectedDateMs)}")
            }
        }
        item { Text("دسته", fontWeight = FontWeight.Bold) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category.id,
                        onClick = { selectedCategory = category.id },
                        label = { Text("${category.iconKey} ${category.name}") }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("توضیح (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    val parsed = amount.toLongOrNull() ?: return@Button
                    viewModel.updateTransaction(
                        current.copy(
                            amountToman = parsed,
                            type = type,
                            dateTimeEpochMs = selectedDateMs,
                            categoryId = selectedCategory,
                            status = if (selectedCategory == null) TransactionStatus.UNCLASSIFIED else TransactionStatus.CONFIRMED,
                            description = description.trim().takeIf { it.isNotEmpty() }
                        )
                    )
                    onDone()
                },
                enabled = (amount.toLongOrNull() ?: 0L) > 0L,
                modifier = Modifier.fillMaxWidth()
            ) { Text("ذخیره تغییرات") }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("حذف تراکنش")
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDateMs = state.selectedDateMillis ?: selectedDateMs
                    showDatePicker = false
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("انصراف") } }
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف تراکنش") },
            text = { Text("این تراکنش برای همیشه از برنامه حذف شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(current.id)
                    confirmDelete = false
                    onDone()
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
            }
        )
    }
}

'''

categorize_marker = '''@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorizeScreen'''
if edit_screen.strip() not in app_text:
    if categorize_marker not in app_text:
        raise SystemExit("CategorizeScreen marker not found for edit screen insertion")
    app_text = app_text.replace(categorize_marker, edit_screen + categorize_marker)

old_tutorial = '''@Composable
private fun TutorialScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("آموزش استفاده از دخل‌وخرج", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("۱) در تنظیمات، مجوز پیامک و اعلان را فعال کنید تا پیامک‌های بانکی جدید به‌صورت خودکار ثبت شوند.") }
        item { Text("۲) پس از دریافت پیامک بانکی، برای شما اعلان نمایش داده می‌شود تا دسته درآمد یا هزینه را انتخاب کنید.") }
        item { Text("۳) اگر همان لحظه فرصت نداشتید، تراکنش با وضعیت «بدون دسته» ذخیره می‌شود و بعداً می‌توانید آن را تکمیل کنید.") }
        item { Text("۴) برای ثبت هزینه یا درآمدی که پیامک ندارد، از دکمه + استفاده کنید و مبلغ، دسته، تاریخ وقوع و توضیح را وارد نمایید.") }
        item { Text("۵) از بخش دسته‌ها می‌توانید دسته‌های دلخواه درآمد و هزینه را اضافه یا غیرفعال کنید.") }
        item { Text("۶) در گزارش‌ها، بازه‌های امروز، این هفته، این ماه و امسال بر اساس تاریخ شمسی نمایش داده می‌شوند.") }
        item { Text("نکته: بازیابی پیامک‌های قدیمی، قفل ایمنی و پشتیبان‌گیری هنوز فعال نیست و در نسخه‌های بعدی اضافه می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
'''
new_tutorial = '''@Composable
private fun TutorialScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("آموزش استفاده از دخل‌وخرج", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item { Text("فعال‌سازی پیامک بانکی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Text("۱) داخل برنامه به «تنظیمات» بروید و در بخش «پیامک و اطلاع‌رسانی» روی «فعال‌سازی مجوزها» بزنید. مجوز SMS را روی Allow / اجازه دادن قرار دهید.") }
        item { Text("۲) اگر مجوز از داخل برنامه فعال نشد یا قبلاً رد شده بود، در گوشی‌های سامسونگ و بیشتر گوشی‌های اندرویدی این مسیر را باز کنید: Settings → Apps → دخل‌وخرج → Permissions → SMS → Allow.") }
        item { Text("۳) برای اعلان دسته‌بندی نیز در Android 13 و بالاتر این مسیر را بررسی کنید: Settings → Apps → دخل‌وخرج → Notifications → Allow notifications.") }
        item { Text("۴) بعد از فعال‌سازی مجوز، برنامه فقط پیامک‌های بانکی جدید را دریافت می‌کند. پیامک‌هایی که قبل از فعال‌سازی در گوشی بوده‌اند وارد برنامه نمی‌شوند.") }
        item { Text("۵) برای آزمایش، پس از فعال شدن مجوز یک تراکنش واقعی انجام دهید یا یک پیامک بانکی جدید دریافت کنید. نتیجه باید در «تراکنش‌ها» نمایش داده شود.") }
        item { Text("۶) اگر پیامک جدید در برنامه دیده نشد، دوباره Settings → Apps → دخل‌وخرج → Permissions را باز کنید و مطمئن شوید SMS روی Allow است؛ سپس برنامه را یک‌بار ببندید و دوباره باز کنید.") }

        item { Text("کار با تراکنش‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        item { Text("۷) پیامک بانکی شناسایی‌شده به‌صورت تراکنش ذخیره می‌شود. اگر دسته انتخاب نشده باشد، وضعیت «بدون دسته» دارد و می‌توانید بعداً دسته را تعیین کنید.") }
        item { Text("۸) برای ثبت دستی هزینه یا درآمد، دکمه + که در وسط پایین صفحه قرار دارد را بزنید و مبلغ، نوع، دسته، تاریخ و توضیح را وارد کنید.") }
        item { Text("۹) برای ویرایش یا حذف، وارد «تراکنش‌ها» شوید و روی تراکنش موردنظر بزنید. سپس اطلاعات را اصلاح و «ذخیره تغییرات» را انتخاب کنید یا از «حذف تراکنش» استفاده کنید.") }
        item { Text("۱۰) از بخش دسته‌ها می‌توانید دسته‌های دلخواه درآمد و هزینه را اضافه یا غیرفعال کنید. گزارش‌ها نیز بازه‌های امروز، این هفته، این ماه و امسال را بر اساس تاریخ شمسی نمایش می‌دهند.") }

        item { Text("نکته امنیتی: رمز پویا، کد ورود و پیامک‌های امنیتی به‌عنوان تراکنش ثبت نمی‌شوند. متن کامل پیامک هم برای گزارش‌ها ذخیره نمی‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("نکته: بازیابی پیامک‌های قدیمی، قفل ایمنی و پشتیبان‌گیری هنوز فعال نیست و در نسخه‌های بعدی اضافه می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
'''
if old_tutorial not in app_text:
    raise SystemExit("Tutorial block not found for v0.2.7")
app_text = app_text.replace(old_tutorial, new_tutorial)

app_text = app_text.replace('item { Text("نسخه ۰.۲.۶") }', 'item { Text("نسخه ۰.۲.۷") }')
app.write_text(app_text, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 8", "versionCode = 9")
gtext = gtext.replace('versionName = "0.2.6"', 'versionName = "0.2.7"')

# Myket currently requires Target SDK API 34+. This project already targets API 36.
if "targetSdk = 36" not in gtext:
    raise SystemExit("Expected targetSdk = 36; verify Myket target SDK compliance before release")
gradle.write_text(gtext, encoding="utf-8")
