#!/usr/bin/env python3
"""UI smoke test for TrazYar using a real Android emulator, not simulated JS UI.
Checks launch/navigation, all guided jobs, the four tools, installer and journal UI.
Sensor/calibration accuracy and actual emitted audio still need physical testing.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.trazyar.level"
APK = "TrazYarInstaller/app/build/outputs/apk/debug/app-debug.apk"
PASSED = []

def run(*args, timeout=60, check=True):
    p = subprocess.run(list(args), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       text=True, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(f"Command {args}: exit={p.returncode}: {p.stdout[-1200:]}")
    return p.stdout

def adb(*args, **kwargs):
    return run("adb", *args, **kwargs)

def snapshot():
    last_error = ""
    # On slower boots UIAutomator can run before userdata mounts or the
    # accessibility service is fully initialized. Retry and use /data/local/tmp.
    location = "/data/local/tmp/trazyar_ui.xml"
    for attempt in range(8):
        try:
            adb("shell", "rm", "-f", location, timeout=12)
            output = adb("shell", "uiautomator", "dump", location, timeout=65)
            raw = adb("exec-out", "cat", location, timeout=20)
            start = raw.find("<?xml")
            if start < 0:
                start = raw.find("<hierarchy")
            if start < 0:
                raise RuntimeError("not written; dump said: " + output[:240]
                    + " read: " + raw[:180])
            tree = ET.fromstring(raw[start:])
            return [n.attrib for n in tree.iter("node")]
        except Exception as e:
            last_error = str(e)
            print("UI inspection retry", attempt+1, ":", last_error[:200],flush=True)
            adb("shell","input","keyevent","KEYCODE_WAKEUP",check=False)
            adb("shell","input","keyevent","82",check=False)
            time.sleep(2)
    raise RuntimeError("Could not inspect native Android UI: " + last_error)

def described(nodes):
    labels = []
    for n in nodes:
        label = n.get("text","") or n.get("content-desc","")
        if label:
            labels.append(label[:100])
    return " / ".join(labels[-32:])

def node_with(fragment, nodes=None):
    nodes = nodes if nodes is not None else snapshot()
    for n in nodes:
        if fragment in n.get("text","") or fragment in n.get("content-desc",""):
            return n
    raise AssertionError(f"No UI element with {fragment!r}: {described(nodes)}")

def verify(fragment, note=None):
    nodes = snapshot()
    node_with(fragment, nodes)
    PASSED.append(note or fragment)
    print("PASS:", note or fragment, flush=True)

def verify_scrolling(fragment, note=None, max_swipes=5):
    for attempt in range(max_swipes+1):
        nodes = snapshot()
        if any(fragment in n.get("text","") or fragment in n.get("content-desc","") for n in nodes):
            PASSED.append(note or fragment)
            print("PASS:",note or fragment,flush=True)
            return
        adb("shell","input","swipe","500","1350","500","450","370")
        time.sleep(.6)
    raise AssertionError("Could not find after scrolling: "+fragment)

def tap(fragment, delay=1.0):
    nodes = snapshot()
    n = node_with(fragment, nodes)
    coords = re.findall(r"\d+", n.get("bounds",""))
    if len(coords)!=4:
        raise AssertionError(f"Element {fragment!r} has no touch bounds {n.get('bounds')}")
    a,b,c,d = map(int, coords)
    if c<=a or d<=b:
        raise AssertionError(f"Element {fragment!r} not visible/touchable")
    print("TAP:",fragment,"at",((a+c)//2,(b+d)//2),
          "clickable",n.get("clickable"),"bounds",n.get("bounds"),flush=True)
    adb("shell", "input", "tap", str((a+c)//2), str((b+d)//2))
    time.sleep(delay)

def back(delay=0.8):
    adb("shell","input","keyevent","KEYCODE_BACK")
    time.sleep(delay)

def main():
    if not os.path.isfile(APK):
        raise RuntimeError("APK missing: "+APK)
    adb("wait-for-device", timeout=90)
    adb("install","-r",APK,timeout=100)
    adb("shell","pm","clear",PKG)
    adb("logcat","-c")
    # ADB reports boot completed before the launcher and accessibility APIs are
    # always ready, especially without an AVD snapshot.
    adb("shell","input","keyevent","KEYCODE_WAKEUP",check=False)
    adb("shell","input","keyevent","82",check=False)
    # This explicit flag only affects debuggable APKs and prevents continuous UI updates.
    adb("shell","am","start","-W","-n",PKG+"/.MainActivity",
        "--ez","smoke_ui_verification","true",timeout=35)
    time.sleep(6.5)
    verify("مرکز آموزش", "first-run introduction and training center")
    verify("معرفی تراز یار","offline app introduction")
    tap("فهرست ۱۲ موضوع")
    verify("آموزش گونیا","all 12 topics list visible")
    back()
    tap("گونیا")
    verify("۹۰ درجه","illustrated square training chapter")
    # Android BACK can dismiss an overlay without leaving the help activity.
    # Use the explicit, accessible navigation control instead.
    tap("بازگشت به برنامه")
    post_back = snapshot()
    if not any("از نوع کارتان شروع کنید" in x.get("text","") for x in post_back):
        print("GUIDE BACK DIAGNOSTIC: explicit back did not show home;",
              described(post_back),flush=True)
        print("ACTIVITY STACK:",adb("shell","dumpsys","activity","activities")[-1800:],flush=True)
        # Continue cross-feature smoke tests using an explicit MainActivity launch.
        adb("shell","am","start","-W","-n",PKG+"/.MainActivity",
            "--activity-clear-top",timeout=35)
        time.sleep(1.4)
    verify("از نوع کارتان شروع کنید","home project based help")
    tap("از نوع کارتان شروع کنید")
    verify("راهنمای انجام کار","project-based learning screen")
    for phrase in ["ماشین لباس‌شویی","کابینت یا میز","قفسه و شلف",
                   "در یا ستون","زاویهٔ ۹۰","رمپ یا مسیر آب"]:
        tap(phrase,0.55)
        verify("مرحله 1 از","project selection "+phrase)
    tap("ماشین لباس‌شویی",0.5)
    tap("انجام شد، بعدی",0.55)
    verify("مرحله 2 از","next project step")
    tap("مرحله قبل",0.55)
    verify("مرحله 1 از","previous project step")
    tap("ورود مستقیم به نصاب‌یار",0.9)
    verify("نصاب‌یار حرفه‌ای","installer assistant opens")
    verify_scrolling("ثبت وضعیت قبل","installer before measurement action")
    back()
    verify("راهنمای انجام کار","return to walkthrough")
    back()
    verify("معرفی برنامه و مرکز آموزش", "main home returned")
    tap("معرفی برنامه و مرکز آموزش")
    verify("مرکز آموزش","dedicated help center opens")
    tap("زاویه‌سنج")
    verify("نسبت به افق","protractor help")
    tap("باز کردن زاویه‌سنج")
    verify("زاویه‌سنج","protractor launch from help")
    # switching tools via exported Main activity extra mimics launch intent
    for mode,label in [(0,"تراز حبابی"),(1,"شیب‌سنج"),(2,"زاویه‌سنج"),(3,"گونیا")]:
        adb("shell","am","start","-n",PKG+"/.MainActivity","--ei","guide_tool",str(mode))
        time.sleep(.55)
        nodes=snapshot()
        node_with(label,nodes)
        PASSED.append("tool navigation "+label)
        print("PASS: tool navigation",label,flush=True)
    tap("معرفی برنامه و مرکز آموزش")
    tap("فهرست ۱۲ موضوع")
    verify_scrolling("قفل عدد و دفترچه","journal and freeze documentation")
    back()
    tap("نصاب‌یار")
    verify("چهار پایه","four-corner estimate guide")
    back()
    # Final real-time launch without test-only sensor suppression:
    # inspect stability while the app starts its normal sensor and audio backend.
    adb("shell","am","force-stop",PKG)
    adb("shell","pm","clear",PKG)
    adb("shell","am","start","-W","-n",PKG+"/.MainActivity",timeout=35)
    time.sleep(4)
    live_pid=adb("shell","pidof",PKG,check=False)
    if not live_pid.strip():
        raise AssertionError("normal live-sensor launch terminated")
    PASSED.append("normal launch with sensors/audio loop enabled (crash check)")
    print("PASS: normal launch with sensors/audio loop enabled",flush=True)
    # verify app remains alive and no fatal exceptions
    ps=adb("shell","pidof",PKG,check=False)
    if not ps.strip():
        raise AssertionError("app process has unexpectedly exited")
    log=adb("logcat","-d","-v","brief",timeout=25)
    fatals=[line for line in log.splitlines() if "FATAL EXCEPTION" in line
            or "ANR in "+PKG in line]
    if fatals:
        raise AssertionError("Android runtime errors: "+"\n".join(fatals[:5]))
    PASSED.append("no uncaught Android crash/ANR")
    print("PASS: no uncaught Android crash/ANR",flush=True)
    print("EMULATOR SMOKE PASS:",len(PASSED),"checks",flush=True)
    print("NOTE: sensor accuracy, true calibration, sound output and Samsung-specific "
          "behaviour require a physical handset.",flush=True)

if __name__=="__main__":
    try:
        main()
    except Exception as ex:
        print("EMULATOR SMOKE FAILED:",ex,flush=True)
        sys.exit(1)
