#!/usr/bin/env python3
"""Production APK manifest and permission auditing: reject accidental debug releases."""
import os,subprocess,zipfile,re
apk="TrazYarInstaller/app/build/outputs/apk/release/app-release-unsigned.apk"
assert os.path.isfile(apk) and os.path.getsize(apk)>25000,"Missing or empty release APK"
sdk=os.environ["ANDROID_HOME"]
tool=os.path.join(sdk,"build-tools","35.0.0","aapt")
badging=subprocess.check_output([tool,"dump","badging",apk],text=True)
perms=subprocess.check_output([tool,"dump","permissions",apk],text=True)
xml=subprocess.check_output([tool,"dump","xmltree",apk,"AndroidManifest.xml"],text=True)
assert "name='com.trazyar.level'" in badging,badging[:200]
assert "versionCode='20'" in badging,badging[:200]
assert "versionName='4.0'" in badging,badging[:200]
assert "sdkVersion:'26'" in badging,badging[:200]
assert "targetSdkVersion:'34'" in badging,badging[:200]
assert "android.permission.VIBRATE" in perms,"Expected vibration permission missing"
for forbidden in ["android.permission.INTERNET","android.permission.CAMERA",
                  "android.permission.READ_CONTACTS","android.permission.ACCESS_FINE_LOCATION",
                  "android.permission.READ_EXTERNAL_STORAGE",
                  "android.permission.WRITE_EXTERNAL_STORAGE"]:
    assert forbidden not in perms, "Unexpected permission: "+forbidden
assert "android:debuggable" not in xml or "android:debuggable(0x0101000f)=(type 0x12)0x0" in xml,    "Production build is debuggable"
with zipfile.ZipFile(apk) as z:
    names=z.namelist()
    assert "classes.dex" in names,"No code"
    assert any(x.startswith("res/") for x in names),"No launcher/icon resources"
print("TrazYar v4 paid release checks passed: APK id/version, target SDK, debuggability, no INTERNET/camera/location; manifest permissions:",perms.strip())
