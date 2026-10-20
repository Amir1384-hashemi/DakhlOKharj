#!/usr/bin/env python3
"""Set Android production signing from developer-owned GitHub Secrets.

Requires:
- ENGINEERING_CALCULATOR_KEYSTORE_BASE64
- ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD
Optional (autodetected/defaulted for Windows-native PKCS12):
- ENGINEERING_CALCULATOR_KEY_ALIAS
- ENGINEERING_CALCULATOR_KEY_PASSWORD

Never commit the keystore, passwords, or encoded key bytes to the repository.
"""
import base64
import os
import subprocess
from pathlib import Path

app = Path("omranyar_flutter/android/app/build.gradle.kts")
android_dir = app.parents[1]
if not app.is_file():
    raise SystemExit("Expected Android Gradle Kotlin file is missing.")

encoded = os.getenv("ENGINEERING_CALCULATOR_KEYSTORE_BASE64")
store_password = os.getenv("ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD")
if not encoded or not store_password:
    raise SystemExit("Missing signing keystore/password GitHub Actions Secrets.")
if any("\n" in os.environ.get(k, "") or "\r" in os.environ.get(k, "") for k in (
    "ENGINEERING_CALCULATOR_KEYSTORE_PASSWORD",
    "ENGINEERING_CALCULATOR_KEY_ALIAS",
    "ENGINEERING_CALCULATOR_KEY_PASSWORD",
)):
    raise SystemExit("Newlines are not supported in signing credentials.")
try:
    key = base64.b64decode(encoded, validate=True)
except (ValueError, base64.binascii.Error):
    raise SystemExit("Signing key secret is not valid Base64.") from None
if len(key) < 500:
    raise SystemExit("Keystore is too small to be valid.")

# JKS uses magic FEEDFEED; Windows-exported PFX is a DER PKCS12 sequence.
# Java 17 also supports PKCS12 via Android Gradle's storeType setting.
store_type = "JKS" if key[:4] == bytes.fromhex("FEEDFEED") else "PKCS12"
key_filename = "upload-keystore.jks" if store_type == "JKS" else "upload-keystore.p12"
key_path = android_dir / key_filename
key_path.write_bytes(key)

key_alias = os.getenv("ENGINEERING_CALCULATOR_KEY_ALIAS")
if not key_alias:
    # In Windows PFX, the private-key alias can vary. Resolve it from the actual
    # keystore to avoid asking the owner to install Java locally.
    result = subprocess.run(
        ["keytool", "-list", "-keystore", str(key_path.resolve()),
         "-storetype", store_type, "-storepass", store_password],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise SystemExit("Unable to inspect the signing key; check the store password.")
    matches = [
        line.split(",", 1)[0].strip()
        for line in result.stdout.splitlines()
        if "PrivateKeyEntry" in line
    ]
    if len(matches) != 1 or not matches[0]:
        raise SystemExit("Expected exactly one PrivateKeyEntry in the signing file.")
    key_alias = matches[0]

key_password = os.getenv("ENGINEERING_CALCULATOR_KEY_PASSWORD") or store_password
(android_dir / "key.properties").write_text(
    "storeFile=../" + key_filename + "\n"
    "storeType=" + store_type + "\n"
    "storePassword=" + store_password + "\n"
    "keyAlias=" + key_alias + "\n"
    "keyPassword=" + key_password + "\n", encoding="utf-8",
)
raw = app.read_text(encoding="utf-8")
old = 'signingConfig = signingConfigs.getByName("debug")'
if raw.count(old) != 1 or raw.count("android {") != 1:
    raise SystemExit("Unexpected Flutter Android Gradle template.")
inject = """
val paidStoreKeystoreProperties = Properties()
val paidStoreKeystorePropertiesFile = rootProject.file("key.properties")
paidStoreKeystorePropertiesFile.inputStream().use { paidStoreKeystoreProperties.load(it) }

"""
raw = "import java.util.Properties\n" + raw
raw = raw.replace("android {", inject + """android {
    signingConfigs {
        create("release") {
            keyAlias = paidStoreKeystoreProperties["keyAlias"] as String
            keyPassword = paidStoreKeystoreProperties["keyPassword"] as String
            storeFile = file(paidStoreKeystoreProperties["storeFile"] as String)
            storePassword = paidStoreKeystoreProperties["storePassword"] as String
            storeType = paidStoreKeystoreProperties["storeType"] as String
        }
    }
""", 1)
raw = raw.replace(old, 'signingConfig = signingConfigs.getByName("release")', 1)
app.write_text(raw, encoding="utf-8")
print("Production signing configuration successfully prepared (" + store_type + ").")
