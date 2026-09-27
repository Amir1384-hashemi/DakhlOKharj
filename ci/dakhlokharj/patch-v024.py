from pathlib import Path

# v0.2.4: make bank-SMS detection more tolerant across common Iranian bank formats.
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
        "واریز", "واریزی", "بستانکار", "افزایش موجودی", "وصول",
        "دریافت وجه", "واریز وجه", "انتقال به حساب", "انتقال به کارت"
    )

    private val expenseWords = listOf(
        "برداشت", "خرید", "پرداخت", "کسر", "بدهکار", "برداشت وجه",
        "انتقال وجه", "انتقال از حساب", "انتقال از کارت", "کارت به کارت",
        "پرداخت قبض", "خرید شارژ"
    )

    private val securityWords = listOf(
        "رمز پویا", "رمز یکبار مصرف", "رمز یکبارمصرف", "رمز دوم",
        "کد تایید", "کد تأیید", "کد فعال سازی", "کد فعال‌سازی",
        "کد ورود", "otp"
    )

    private val financialContextWords = listOf(
        "حساب", "کارت", "سپرده", "مانده", "موجودی", "ریال", "تومان",
        "تومن", "بانک", "واریز", "برداشت", "خرید", "پرداخت", "انتقال"
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
        "سپه" to "بانک سپه",
        "سینا" to "بانک سینا",
        "دی" to "بانک دی",
        "گردشگری" to "بانک گردشگری",
        "خاورمیانه" to "بانک خاورمیانه",
        "کارآفرین" to "بانک کارآفرین",
        "سرمایه" to "بانک سرمایه",
        "توسعه تعاون" to "بانک توسعه تعاون",
        "پست بانک" to "پست بانک ایران",
        "صنعت و معدن" to "بانک صنعت و معدن",
        "رسالت" to "بانک قرض الحسنه رسالت",
        "مهر ایران" to "بانک قرض الحسنه مهر ایران"
    )

    fun parse(sender: String?, body: String, timestampMs: Long): ParsedBankSms? {
        val normalized = normalize(body)
        if (normalized.isBlank()) return null
        if (securityWords.any { normalized.contains(it, ignoreCase = true) }) return null

        val type = detectType(normalized) ?: return null
        val amount = extractAmountToman(normalized) ?: return null
        if (amount <= 0L) return null

        val senderNormalized = normalize(sender.orEmpty())
        val bankSearchText = "$senderNormalized $normalized"
        val bankName = knownBanks.entries.firstOrNull { bankSearchText.contains(it.key, ignoreCase = true) }?.value
            ?: sender?.takeIf { it.isNotBlank() }

        val last4 = extractLast4(normalized)
        val fingerprint = sha256(sender.orEmpty() + "|" + body + "|" + timestampMs)

        return ParsedBankSms(amount, type, bankName, last4, fingerprint)
    }

    private fun detectType(text: String): TransactionType? {
        if (text.contains("به حساب شما") || text.contains("به کارت شما") || text.contains("به سپرده شما")) {
            return TransactionType.INCOME
        }
        if (text.contains("از حساب شما") || text.contains("از کارت شما") || text.contains("از سپرده شما")) {
            return TransactionType.EXPENSE
        }

        val incomeIndex = incomeWords.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()
        val expenseIndex = expenseWords.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()

        if (incomeIndex != null || expenseIndex != null) {
            return when {
                incomeIndex == null -> TransactionType.EXPENSE
                expenseIndex == null -> TransactionType.INCOME
                incomeIndex < expenseIndex -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
        }

        if (financialContextWords.any { text.contains(it, ignoreCase = true) }) {
            val sign = Regex("""(?:^|\s)([+-])\s*[0-9][0-9,٬،./ ]{2,}""").find(text)
                ?.groupValues?.getOrNull(1)
            if (sign == "+") return TransactionType.INCOME
            if (sign == "-") return TransactionType.EXPENSE
        }

        return null
    }

    private fun extractAmountToman(text: String): Long? {
        val currency = """(ریال|تومان|تومن|irr|irt|rial|rls|toman|tmn|ر)"""

        val labelled = Regex(
            """(?:مبلغ(?:\s+تراکنش)?|مقدار)\s*[:：\-]?\s*([+\-]?\s*[0-9][0-9,٬،./ ]*)\s*$currency?""",
            RegexOption.IGNORE_CASE
        )
        labelled.find(text)?.let { match ->
            toToman(match.groupValues[1], match.groupValues.getOrNull(2))?.let { return it }
        }

        val allCurrencyAmounts = Regex(
            """([+\-]?\s*[0-9][0-9,٬،./ ]*)\s*$currency""",
            RegexOption.IGNORE_CASE
        ).findAll(text)

        for (match in allCurrencyAmounts) {
            if (isBalanceOrIdentifierContext(text, match.range.first)) continue
            toToman(match.groupValues[1], match.groupValues.getOrNull(2))?.let { return it }
        }

        val transactionNearNumber = Regex(
            """(?:واریز|واریزی|برداشت|خرید|پرداخت|کسر|بستانکار|بدهکار|انتقال(?:\s+وجه)?|کارت\s+به\s+کارت)[^0-9]{0,28}([+\-]?\s*[0-9][0-9,٬،./ ]*)""",
            RegexOption.IGNORE_CASE
        )
        transactionNearNumber.find(text)?.let { match ->
            val numberStart = match.range.first + match.value.indexOf(match.groupValues[1])
            if (!isBalanceOrIdentifierContext(text, numberStart)) {
                toToman(match.groupValues[1], null)?.let { return it }
            }
        }

        val fallback = Regex("""[+\-]?\s*[0-9][0-9,٬،./ ]{2,}""")
        for (match in fallback.findAll(text)) {
            if (isBalanceOrIdentifierContext(text, match.range.first)) continue
            if (looksLikeDate(match.value)) continue
            val digits = match.value.filter { it.isDigit() }
            if (digits.length < 3) continue
            toToman(match.value, null)?.let { return it }
        }

        return null
    }

    private fun toToman(rawNumber: String, rawCurrency: String?): Long? {
        val digits = rawNumber.filter { it.isDigit() }
        val value = digits.toLongOrNull() ?: return null
        if (value <= 0L) return null

        val currency = rawCurrency.orEmpty().lowercase()
        return when (currency) {
            "تومان", "تومن", "irt", "toman", "tmn" -> value
            else -> value / 10L
        }
    }

    private fun isBalanceOrIdentifierContext(text: String, numberStart: Int): Boolean {
        val before = text.substring(maxOf(0, numberStart - 26), numberStart)
        val blocked = listOf(
            "مانده", "موجودی", "قابل برداشت", "شماره حساب", "شماره کارت",
            "حساب:", "حساب :", "کارت:", "کارت :", "سپرده:", "سپرده :"
        )
        return blocked.any { before.contains(it) }
    }

    private fun looksLikeDate(raw: String): Boolean {
        val compact = raw.replace(" ", "")
        return Regex("""\d{2,4}/\d{1,2}/\d{1,2}""").matches(compact)
    }

    private fun extractLast4(text: String): String? {
        val masked = Regex("""[*xX•\-]{2,}\s*([0-9]{4})(?![0-9])""").find(text)
        if (masked != null) return masked.groupValues[1]

        val direct = Regex("""(?:کارت|حساب|سپرده)[^0-9]{0,18}([0-9]{4})(?![0-9])""").find(text)
        if (direct != null) return direct.groupValues[1]

        val fullCard = Regex("""(?:کارت)[^0-9]{0,12}([0-9][0-9\- ]{14,24})""").find(text)
        if (fullCard != null) {
            val digits = fullCard.groupValues[1].filter { it.isDigit() }
            if (digits.length >= 4) return digits.takeLast(4)
        }

        return null
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
                    'ي' -> 'ی'
                    'ك' -> 'ک'
                    else -> ch
                }
            )
        }
    }
        .replace('\u200c', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
''', encoding="utf-8")

# Preserve a boundary between multipart SMS segments so words/numbers are not glued together.
receiver = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsReceiver.kt")
rtext = receiver.read_text()
rtext = rtext.replace(
    'val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }',
    'val body = messages.joinToString(separator = "\\n") { it.messageBody.orEmpty() }'
)
receiver.write_text(rtext)

# Expand parser coverage with regression tests for common bank-SMS styles.
tests = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
tests.write_text(r'''package ir.dakhlokharj.app.sms

import ir.dakhlokharj.app.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsParserTest {
    @Test
    fun parsesExpenseInRial() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "برداشت از کارت ****1234 مبلغ 850,000 ریال",
            timestampMs = 1000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(85_000L, parsed.amountToman)
        assertEquals("1234", parsed.accountLast4)
    }

    @Test
    fun parsesPersianDigitsIncome() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "واریز مبلغ ۵,۰۰۰,۰۰۰ ریال به حساب ****۵۶۷۸",
            timestampMs = 2000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(500_000L, parsed.amountToman)
        assertEquals("5678", parsed.accountLast4)
    }

    @Test
    fun ignoresOtp() {
        assertNull(BankSmsParser.parse("BANK", "رمز پویای شما ۴۸۳۹۱۲ است", 3000L))
    }

    @Test
    fun parsesOutgoingTransfer() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "انتقال وجه از حساب شما\nمبلغ: 1,250,000 ریال\nمانده: 8,750,000 ریال",
            timestampMs = 4000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(125_000L, parsed.amountToman)
    }

    @Test
    fun parsesIncomingCardToCard() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "مبلغ ۲,۰۰۰,۰۰۰ ریال به حساب شما واریز شد",
            timestampMs = 5000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(200_000L, parsed.amountToman)
    }

    @Test
    fun parsesPurchaseWithoutAmountLabel() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "خرید 450,000 ریال کارت ****4321 مانده 5,000,000 ریال",
            timestampMs = 6000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(45_000L, parsed.amountToman)
        assertEquals("4321", parsed.accountLast4)
    }

    @Test
    fun parsesUnlabelledRialAmountBeforeBalance() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "برداشت\n850,000 ریال\nمانده 9,250,000 ریال",
            timestampMs = 7000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(85_000L, parsed.amountToman)
    }

    @Test
    fun keepsTomanAmountAsToman() {
        val parsed = BankSmsParser.parse(
            sender = "BANK",
            body = "واریز مبلغ 120,000 تومان به حساب شما",
            timestampMs = 8000L
        )
        requireNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(120_000L, parsed.amountToman)
    }

    @Test
    fun ignoresSecurityCodeEvenWhenItContainsPaymentWord() {
        assertNull(
            BankSmsParser.parse(
                "BANK",
                "رمز یکبار مصرف پرداخت شما 123456 است",
                9000L
            )
        )
    }
}
''', encoding="utf-8")

# Bump the store version.
gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text()
gtext = gtext.replace("versionCode = 5", "versionCode = 6")
gtext = gtext.replace('versionName = "0.2.3"', 'versionName = "0.2.4"')
gradle.write_text(gtext)

app = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/AppUi.kt")
atext = app.read_text()
atext = atext.replace('item { Text("نسخه ۰.۲.۳") }', 'item { Text("نسخه ۰.۲.۴") }')
app.write_text(atext)
