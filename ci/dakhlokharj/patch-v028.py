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
