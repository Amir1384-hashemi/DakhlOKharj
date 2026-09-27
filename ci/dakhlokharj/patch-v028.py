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
