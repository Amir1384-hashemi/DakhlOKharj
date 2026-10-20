#!/usr/bin/env python3
"""Configure Flutter Android production signing from CI-only credentials.

Required in environment (do NOT commit actual values):
ENGINEERING_CALCULATOR_KEYSTORE_BASE64
ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD
ENGINEERING_CALCULATOR_KEY_ALIAS
ENGINEERING_CALCULATOR_KEY_PASSWORD

This script runs AFTER flutter create. No signing keys or credentials are stored
in GitHub source control or bundled into APK/AAB assets.
"""
import base64
import os
from pathlib import Path
app = Path("omranyar_flutter/android/app/build.gradle.kts")
android_dir = app.parents[1]
if not app.is_file():
    raise SystemExit("Expected Android Gradle Kotlin file is missing.")
names = ("ENGINEERING_CALCULATOR_KEYSTORE_BASE64","ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD",
         "ENGINEERING_CALCULATOR_KEY_ALIAS","ENGINEERING_CALCULATOR_KEY_PASSWORD")
if any(not os.getenv(k) for k in names):
    raise SystemExit("Missing release signing credentials in GitHub Actions Secrets.")
key = base64.b64decode(os.environ[names[0]], validate=True)
if len(key) < 100:
    raise SystemExit("Keystore is too short.")
(android_dir/"upload-keystore.jks").write_bytes(key)
for k in names[1:]:
    if "\n" in os.environ[k] or "\r" in os.environ[k]:
        raise SystemExit(f"Unsupported newline in {k}.")
(android_dir/"key.properties").write_text(
    "storeFile=../upload-keystore.jks\n"
    f"storePassword={os.environ[names[1]]}\n"
    f"keyAlias={os.environ[names[2]]}\n"
    f"keyPassword={os.environ[names[3]]}\n", encoding="utf-8")
raw=app.read_text(encoding="utf-8")
old='signingConfig = signingConfigs.getByName("debug")'
if raw.count(old)!=1:
    raise SystemExit("Expected Flutter default debug release signer not found.")
if raw.count("android {")!=1:
    raise SystemExit("Android Gradle configuration not recognized.")
inject="""
val paidStoreKeystoreProperties = java.util.Properties()
val paidStoreKeystorePropertiesFile = rootProject.file("key.properties")
paidStoreKeystorePropertiesFile.inputStream().use { paidStoreKeystoreProperties.load(it) }

"""
raw=raw.replace("android {",inject+"""android {
    signingConfigs {
        create("release") {
            keyAlias = paidStoreKeystoreProperties["keyAlias"] as String
            keyPassword = paidStoreKeystoreProperties["keyPassword"] as String
            storeFile = file(paidStoreKeystoreProperties["storeFile"] as String)
            storePassword = paidStoreKeystoreProperties["storePassword"] as String
        }
    }
""",1)
raw=raw.replace(old,'signingConfig = signingConfigs.getByName("release")',1)
app.write_text(raw,encoding="utf-8")
print("Private production keystore injected; Android release signing configured.")
