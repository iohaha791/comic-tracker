"""
Starts the Shelf server without a visible console (when run with pythonw.exe)
and opens your browser to it automatically. If Shelf is already running,
just opens a new browser tab instead of starting a second copy.
"""
import socket
import sys
import threading
import webbrowser

from app import app, init_db

PORT = 5050
URL = f"http://127.0.0.1:{PORT}"
SILENT = "--no-browser" in sys.argv


def port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


if __name__ == "__main__":
    if port_in_use(PORT):
        if not SILENT:
            webbrowser.open(URL)
        sys.exit(0)

    init_db()
    if not SILENT:
        threading.Timer(1.2, lambda: webbrowser.open(URL)).start()
    app.run(host="0.0.0.0", port=PORT, debug=False, use_reloader=False)
