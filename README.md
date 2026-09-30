# Shelf Android — Standalone App

A fully native, standalone Android app. It does not talk to any server —
everything (titles, descriptions, last chapter read, cover art) lives only
on the phone, in a local SQLite database and local image files.

## What changed from the WebView version
This is a ground-up rewrite, not an update:
- **Package renamed** from `com.ilham.shelf` to `com.shelf.app`. Android
  treats this as a completely different app — it will install *alongside*
  the old WebView-based app rather than replacing it. If you don't want
  both, uninstall the old one manually. None of its data carries over
  automatically (different app = different sandboxed storage), but that's
  fine here since this version doesn't need any server-synced data anyway.
- The WebView, server URL setup screen, JSON API client, and sync manager
  are all gone. There is no "offline vs online" mode anymore — it's always
  the same native screens.
- Same visual language as the Shelf website: dark ink background, warm
  paper-colored cover placeholders, red accent, serif titles, 3-column grid.
- Same app icon as before (`shelf_icon.png`), untouched.

## Features
- **Home**: 3-column grid of covers, titles, and last chapter read. Search
  bar filters by title/description live as you type. "+ ADD" opens a new
  comic form with a smooth slide-up entrance.
- **Tapping a comic** opens its detail/edit screen with a shared-element
  zoom animation — the cover art grows smoothly from its grid position into
  the full detail view, and reverses the same way going back.
- **Detail/edit screen**: change title, last chapter, description, and
  cover art (picked from the phone's gallery, copied into the app's private
  storage). Delete with a confirmation prompt.
- Everything is stored locally via a plain SQLite database
  (`ShelfDatabase.java`) — no network permission, no internet dependency at
  all.

## Related
- [`main`](../../tree/main) – the Flask + SQLite server this app connects to, including the JSON API it syncs against.
- [`shelf-android`](https://github.com/iohaha791/comic-tracker/tree/shelf-android) – A native Android wrapper around the Shelf comic tracker website. It loads the site in desktop-site mode, and falls back to a local offline cache (with sync back to the server) when the server isn't reachable.
