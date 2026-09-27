from pathlib import Path

# v0.2.8 patch

parser = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/BankSmsParser.kt")
ptext = parser.read_text(encoding="utf-8")
ptext = ptext.replace(
    '        return when (currency) {\n            "تومان" -> value\n            "ریال" -> value / 10L\n            else -> value / 10L\n        }',
    '        return when (currency) {\n            "تومان" -> value * 10L\n            "ریال" -> value\n            else -> value\n        }'
)
if '"ریال" -> value' not in ptext:
    raise SystemExit("Rial parser patch failed")
parser.write_text(ptext, encoding="utf-8")

for money_ui_file in [
    Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/AppUi.kt"),
    Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/Formatters.kt"),
    Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/sms/TransactionNotifier.kt"),
]:
    if money_ui_file.exists():
        mtext = money_ui_file.read_text(encoding="utf-8").replace("تومان", "ریال")
        money_ui_file.write_text(mtext, encoding="utf-8")

app = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/ui/AppUi.kt")
app_text = app.read_text(encoding="utf-8")

old_bottom = '''                    NavigationBar {
                        bottomDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Text(destination.icon) },
                                label = { Text(destination.label) }
                            )
                        }
                    }'''
new_bottom = '''                    NavigationBar {
                        bottomDestinations.forEachIndexed { index, destination ->
                            NavigationBarItem(
                                selected = route == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Text(destination.icon) },
                                label = { Text(destination.label) }
                            )
                            if (index == 1) {
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { navController.navigate("add") },
                                    icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
                                    label = { Text("ثبت", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }'''
if old_bottom not in app_text:
    raise SystemExit("Bottom navigation anchor not found")
app_text = app_text.replace(old_bottom, new_bottom)

old_fab = '''            },
            floatingActionButton = {
                if (showBottom) FloatingActionButton(onClick = { navController.navigate("add") }) { Text("+") }
            },
            floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center
'''
if old_fab not in app_text:
    raise SystemExit("Floating action button anchor not found")
app_text = app_text.replace(old_fab, "            }\n")

app_text = app_text.replace(
    "دکمه + که در وسط پایین صفحه قرار دارد",
    "دکمه + که بین «تراکنش‌ها» و «گزارش» در نوار پایین قرار دارد"
)
app_text = app_text.replace('item { Text("نسخه ۰.۲.۷") }', 'item { Text("نسخه ۰.۲.۸") }')
app.write_text(app_text, encoding="utf-8")

db_file = Path("dakhlokharj/app/src/main/java/ir/dakhlokharj/app/data/AppDatabase.kt")
db_text = db_file.read_text(encoding="utf-8")
db_text = db_text.replace(
    "import androidx.room.RoomDatabase\nimport androidx.room.TypeConverters",
    "import androidx.room.RoomDatabase\nimport androidx.room.TypeConverters\nimport androidx.room.migration.Migration\nimport androidx.sqlite.db.SupportSQLiteDatabase"
)
db_text = db_text.replace(
    "    version = 1,\n    exportSchema = false",
    "    version = 2,\n    exportSchema = false"
)
db_text = db_text.replace(
    '            ).build().also { INSTANCE = it }\n        }\n    }\n}',
    '            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }\n        }\n\n        private val MIGRATION_1_2 = object : Migration(1, 2) {\n            override fun migrate(db: SupportSQLiteDatabase) {\n                db.execSQL("UPDATE transactions SET amountToman = amountToman * 10")\n            }\n        }\n    }\n}'
)
if "version = 2" not in db_text or "MIGRATION_1_2" not in db_text:
    raise SystemExit("Database migration patch failed")
db_file.write_text(db_text, encoding="utf-8")

test = Path("dakhlokharj/app/src/test/java/ir/dakhlokharj/app/sms/BankSmsParserTest.kt")
ttext = test.read_text(encoding="utf-8")
ttext = ttext.replace("assertEquals(125_000L, parsed.amountToman)", "assertEquals(1_250_000L, parsed.amountToman)")
ttext = ttext.replace("assertEquals(250_000L, parsed.amountToman)", "assertEquals(2_500_000L, parsed.amountToman)")
ttext = ttext.replace("assertEquals(400_000L, parsed.amountToman)", "assertEquals(4_000_000L, parsed.amountToman)")
ttext = ttext.replace("assertEquals(300_000L, parsed.amountToman)", "assertEquals(3_000_000L, parsed.amountToman)")
rial_test = r"""
    @Test
    fun keepsRialAmountAndConvertsTomanToRial() {
        val rial = BankSmsParser.parse(
            "BankMellat",
            "خرید مبلغ 1,250,007 ریال",
            15L
        )
        assertNotNull(rial)
        assertEquals(1_250_007L, rial!!.amountToman)

        val toman = BankSmsParser.parse(
            "BankMellat",
            "خرید مبلغ 125,000 تومان",
            16L
        )
        assertNotNull(toman)
        assertEquals(1_250_000L, toman!!.amountToman)
    }
"""
if "keepsRialAmountAndConvertsTomanToRial" not in ttext:
    ttext = ttext.replace("\n}\n", rial_test + "\n}\n")
test.write_text(ttext, encoding="utf-8")

gradle = Path("dakhlokharj/app/build.gradle.kts")
gtext = gradle.read_text(encoding="utf-8")
gtext = gtext.replace("versionCode = 9", "versionCode = 10")
gtext = gtext.replace('versionName = "0.2.7"', 'versionName = "0.2.8"')
gradle.write_text(gtext, encoding="utf-8")
