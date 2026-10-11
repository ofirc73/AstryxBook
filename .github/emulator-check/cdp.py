"""Evaluate JavaScript in the app's main WebView over the DevTools protocol, for run.sh.

  cdp.py PACKAGE 'expression'

Debug builds always allow WebView debugging. Prints the JSON value of the expression
(awaited if it's a promise), run with a user gesture so it may open a file chooser.
"""
import json
import subprocess
import sys
import time
import urllib.request

import websocket

PORT = 9333


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def page_socket(package):
    pid = adb("shell", "pidof", package).strip().split(" ")[0]
    if not pid:
        raise SystemExit(f"{package} is not running")
    adb("forward", "--remove-all")
    adb("forward", f"tcp:{PORT}", f"localabstract:webview_devtools_remote_{pid}")
    for _ in range(10):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json", timeout=5) as response:
                targets = json.load(response)
            pages = [t for t in targets if t.get("type") == "page" and t.get("webSocketDebuggerUrl")]
            # The main view (not the Messages layer): the first visible Facebook page.
            pages.sort(key=lambda t: '"visible":true' not in t.get("description", ""))
            if pages:
                return pages[0]["webSocketDebuggerUrl"]
        except OSError:
            pass
        time.sleep(1)
    raise SystemExit("no DevTools page")


def main():
    package, expression = sys.argv[1], sys.argv[2]
    ws = websocket.create_connection(page_socket(package), timeout=30, suppress_origin=True)
    ws.send(json.dumps({
        "id": 1,
        "method": "Runtime.evaluate",
        "params": {
            "expression": expression,
            "awaitPromise": True,
            "returnByValue": True,
            "userGesture": True,
        },
    }))
    while True:
        message = json.loads(ws.recv())
        if message.get("id") == 1:
            break
    result = message.get("result", {})
    if "exceptionDetails" in result:
        print("exception:", json.dumps(result["exceptionDetails"].get("exception", {}).get("description")))
    else:
        print(json.dumps(result.get("result", {}).get("value")))


if __name__ == "__main__":
    main()
