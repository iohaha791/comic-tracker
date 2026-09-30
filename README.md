# Shelf Android — WebView Client

A native Android wrapper around the [Shelf](../../tree/main) comic tracker website. It loads the site in desktop-site mode, and falls back to a local offline cache (with sync back to the server) when the server isn't reachable.

## Requires the Shelf server (see the `main` branch)

This app is a **client only** — it has no comic data or logic of its own. It needs the Flask server from this repo's [`main` branch](../../tree/main) running somewhere on your network before it's useful.

1. Set up and run the server from `main` first (see that branch's README for setup, including `pip install -r requirements.txt` and `python launcher.py`).
2. Confirm it's reachable — visit `http://<server-ip>:5050/api/health` in a browser and see `{"status": "ok"}`.
3. Then build and run this app, and enter that same IP and port on first launch.

If the server isn't running or isn't reachable, this app still works in a limited offline mode (see below), but nothing here functions standalone.

## Features
- First launch asks for the server's IP/hostname and port, then remembers it.
- **Desktop-site mode**: renders the site as desktop Chrome would, not a mobile layout.
- **Offline cache + sync**: pulls the latest comics (titles, chapters, covers) into a local cache every time it launches online. If the server's unreachable at launch, it falls back to a simple native list backed by that cache — you can still add/edit/delete there, including covers, and those changes push to the server automatically the next time the app launches with the server reachable.
- While online, editing goes straight through the website's own pages in the WebView, same as using the site in a browser.

## Related
- [`main`](../../tree/main) — the Flask + SQLite server this app connects to, including the JSON API it syncs against.
