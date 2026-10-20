#!/usr/bin/env python3
"""Prepare store brand on the extracted Flutter source. No IAP/ads: paid upfront by store."""
from pathlib import Path
import re
ROOT = Path(__file__).resolve().parents[2] / "omranyar_flutter"
def replace(path, before, after, expected=1):
    p=ROOT/path
    txt=p.read_text(encoding="utf-8")
    count=txt.count(before)
    if count < expected:
        raise SystemExit(f"{p}: missing expected brand token {before!r} (found {count})")
    p.write_text(txt.replace(before,after), encoding="utf-8")
    print(f"{p}: {count} replacement(s)")

replace(Path("lib/main.dart"),"عمران‌یار","ماشین حساب مهندسی",2)
replace(Path("lib/main.dart"),"نسخه آزمایشی ۰٫۶","نسخه ۱٫۰")
replace(Path("tools/apply_branding.py"),"عمران‌یار","ماشین حساب مهندسی",2)
replace(Path("tools/apply_branding.py"),"OmranYar","Engineering Calculator",1)
replace(Path("pubspec.yaml"),"version: 0.7.0+5","version: 1.0.0+10")
replace(Path("test/qa_ui_smoke_test.dart"),"find.text('عمران‌یار')","find.text('ماشین حساب مهندسی')")
# Strict check: no obsolete display label remains in these production files.
assert "عمران‌یار" not in (ROOT/"lib/main.dart").read_text(encoding="utf-8")
print("Engineering Calculator | one-time paid download | branded source ready")
