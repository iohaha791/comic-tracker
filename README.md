# Shelf — a local comic bookmark tracker

A small local website for keeping track of the comics/manga you're reading:
title, description, last chapter read, and a cover image you upload yourself.

## Setup

Requires Python 3.9+.

```bash
cd comic-tracker
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Then open **http://127.0.0.1:5050** in your browser.

A `comics.db` SQLite file and a `static/uploads/` folder are created automatically
on first run — that's where your data and cover images live. Back up `comics.db`
and `static/uploads/` together if you want to keep a copy of your library.

## What it does

- **Home** — a 3-column grid of your comics: cover art, title, and last chapter
  read underneath. Click any card to edit it.
- **Search** — filters by title or description as you search.
- **Add** (`+ Add comic`) — title, description, last chapter read, and an
  optional cover image (png/jpg/jpeg/webp/gif).
- **Edit** — same fields, pre-filled. You can replace the cover, remove it, or
  delete the comic entirely (with a confirmation prompt).

## Running it like a desktop app (Windows)

Once you've done the one-time `pip install -r requirements.txt` above, you don't
need to open a terminal again:

1. **Double-click `Start Shelf.vbs`.** No console window appears — it starts
   the server quietly in the background and opens your browser to Shelf after
   about a second. Double-clicking it again just opens a new tab instead of
   starting a second copy.
2. **To close it**, double-click `Stop Shelf.bat` (or open Task Manager,
   find `pythonw.exe`, and End Task).
3. **To have it start automatically when you log into Windows, without a
   browser tab popping open:**
   - Press `Win + R`, type `shell:startup`, press Enter — this opens your
     personal Startup folder.
   - Right-click **`Start Shelf (Silent).vbs`** → *Create shortcut*, then
     drag that shortcut into the Startup folder.
   - From then on, Shelf starts quietly in the background every time you log
     in — no console, no browser tab. Whenever you actually want to look at
     it, just open your browser and go to `http://127.0.0.1:5050` (worth
     bookmarking). Double-clicking `Start Shelf.vbs` still opens a tab
     automatically, same as before — that one's for on-demand use, not
     login.

Want a proper desktop icon? Right-click `Start Shelf.vbs` → *Create shortcut*,
then drag that shortcut onto your Desktop and rename it "Shelf". You can also
right-click the shortcut → *Properties* → *Change Icon* to give it a custom
icon.

## Installing it as an app on Android

Shelf can be installed as a real home-screen app icon (no browser address
bar, opens full-screen) — the same way Jellyfin's or Plex's web app works.

1. Make sure your PC (running Shelf) and your phone are on the same Wi-Fi.
2. On your phone, open **Chrome** and go to `http://192.168.1.23:5050`
   (using your PC's actual IP from the "Access from other devices" section
   below).
3. Tap the **⋮** menu in Chrome → **"Add to Home screen"** (or you may see
   **"Install app"** show up directly — either one works). Confirm the name
   ("Shelf" by default) and tap **Add**.
4. An icon appears on your home screen. Tapping it opens Shelf full-screen,
   with its own icon and no browser UI — just like a native app.

A couple of things worth knowing:
- This only works while your PC is on and running Shelf (via
  `Start Shelf (Silent).vbs` or however you've set it up) and your phone is
  on the same network — it's not accessible when you're out and about,
  unless you separately set up something like a VPN back to your home
  network.
- The icon is tied to the address it was installed from. If your PC's IP
  changes later (see the note about DHCP below), the icon will stop working
  and you'll need to remove it and add it again from the new address.

## Access from other devices on your network

Shelf listens on all network interfaces, so any device on the same Wi-Fi/LAN
(phone, tablet, another computer) can open it too — not just the PC it's
running on.

1. On the Windows PC running Shelf, open Command Prompt and run `ipconfig`.
   Look for **IPv4 Address** (something like `192.168.1.23`).
2. On the other device, browse to `http://192.168.1.23:5050` (use your own
   IP). Bookmark it there if you like.
3. **First time only:** Windows Firewall will likely pop up asking whether to
   allow Python through on private networks — click **Allow access**. If you
   miss that prompt, search Start Menu for "Allow an app through Windows
   Firewall" and enable Python for Private networks.

A few things worth knowing:
- Your PC's IP can change (e.g. after a router restart) unless you've set a
  static IP or DHCP reservation for it — if the address stops working,
  re-check it with `ipconfig`.
- This has no login/password, so anyone on your network can view and edit
  your library. Fine for a home network; don't do this on a shared/public
  Wi-Fi network or expose the port to the internet.

## Notes

- This is meant to run on your own machine (`127.0.0.1`), not to be exposed to
  the internet as-is — it has no login/auth.
- Cover art with no upload falls back to a plain title card, so the grid still
  looks fine before you add images.
