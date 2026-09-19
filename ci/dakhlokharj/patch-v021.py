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
