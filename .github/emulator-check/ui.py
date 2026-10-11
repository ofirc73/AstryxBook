"""Find and press on-screen elements by their text (uiautomator dump), for run.sh.

  ui.py tap TEXT          tap the first element whose text or description contains TEXT
  ui.py longpress TEXT    long-press it
  ui.py exists TEXT       exit 0 if it's on screen
  ui.py scrollto TEXT     swipe up until it's on screen (exit 1 if never)
  ui.py texts             print every visible text, one per line
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml")
        start = raw.find("<?xml")
        if start >= 0:
            try:
                return list(ET.fromstring(raw[start:]).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    return []


def label(node):
    return (node.get("text") or "") + " " + (node.get("content-desc") or "")


def find(text):
    lowered = text.lower()
    for node in nodes():
        if lowered in label(node).lower():
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main():
    cmd, text = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
    if cmd == "texts":
        for node in nodes():
            if label(node).strip():
                print(label(node).strip())
        return 0
    if cmd == "scrollto":
        for _ in range(12):
            if find(text):
                return 0
            adb("shell", "input", "swipe", "540", "1800", "540", "900", "400")
            time.sleep(1)
        print(f"not found: {text}")
        return 1
    point = find(text)
    if point is None:
        print(f"not found: {text}")
        return 1
    x, y = map(str, point)
    if cmd == "tap":
        adb("shell", "input", "tap", x, y)
    elif cmd == "longpress":
        adb("shell", "input", "swipe", x, y, x, y, "1500")
    print(f"{cmd} '{text}' at {x},{y}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
