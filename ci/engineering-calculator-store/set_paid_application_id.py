#!/usr/bin/env python3
"""Assign a distinct, stable paid app identifier; keep Flutter Activity namespace unchanged."""
from pathlib import Path
p=Path("omranyar_flutter/android/app/build.gradle.kts")
s=p.read_text(encoding="utf-8")
old='applicationId = "ir.omranyar.omran_yar"'
new='applicationId = "ir.omranyar.engineeringcalculator"'
if s.count(old)!=1:
    raise SystemExit("Expected unique Android applicationId is absent; inspect Flutter template before releasing.")
p.write_text(s.replace(old,new,1),encoding="utf-8")
print("Paid-store applicationId: ir.omranyar.engineeringcalculator")
