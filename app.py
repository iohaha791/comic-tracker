import os
import sqlite3
import uuid
from flask import Flask, render_template, request, redirect, url_for, g

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "comics.db")
UPLOAD_DIR = os.path.join(BASE_DIR, "static", "uploads")
ALLOWED_EXT = {"png", "jpg", "jpeg", "webp", "gif"}

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024  # 16MB upload cap


# ---------- database helpers ----------

def get_db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(exception=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db():
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS comics (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            description TEXT,
            last_chapter TEXT,
            cover_filename TEXT,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
        """
    )
    # migration for databases created before updated_at existed
    try:
        conn.execute("ALTER TABLE comics ADD COLUMN updated_at TEXT")
        conn.execute(
            "UPDATE comics SET updated_at = COALESCE(created_at, CURRENT_TIMESTAMP) "
            "WHERE updated_at IS NULL"
        )
    except sqlite3.OperationalError:
        pass  # column already exists
    conn.commit()
    conn.close()


def touch_updated_at(db, comic_id):
    db.execute(
        "UPDATE comics SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?",
        (comic_id,),
    )


def allowed_file(filename):
    return "." in filename and filename.rsplit(".", 1)[1].lower() in ALLOWED_EXT


def save_cover(file_storage):
    """Save an uploaded cover image and return its stored filename, or None."""
    if not file_storage or file_storage.filename == "":
        return None
    if not allowed_file(file_storage.filename):
        return None
    ext = file_storage.filename.rsplit(".", 1)[1].lower()
    filename = f"{uuid.uuid4().hex}.{ext}"
    file_storage.save(os.path.join(UPLOAD_DIR, filename))
    return filename


def delete_cover(filename):
    if not filename:
        return
    path = os.path.join(UPLOAD_DIR, filename)
    if os.path.exists(path):
        os.remove(path)


# ---------- routes ----------

@app.route("/")
def home():
    query = request.args.get("q", "").strip()
    db = get_db()
    if query:
        like = f"%{query}%"
        rows = db.execute(
            "SELECT * FROM comics WHERE title LIKE ? OR description LIKE ? "
            "ORDER BY title COLLATE NOCASE ASC",
            (like, like),
        ).fetchall()
    else:
        rows = db.execute(
            "SELECT * FROM comics ORDER BY title COLLATE NOCASE ASC"
        ).fetchall()
    return render_template("index.html", comics=rows, query=query)


@app.route("/add", methods=["GET", "POST"])
def add():
    if request.method == "POST":
        title = request.form.get("title", "").strip()
        description = request.form.get("description", "").strip()
        last_chapter = request.form.get("last_chapter", "").strip()

        if not title:
            return render_template(
                "form.html",
                mode="add",
                comic=request.form,
                error="Title can't be empty.",
            )

        cover_filename = save_cover(request.files.get("cover"))

        db = get_db()
        db.execute(
            "INSERT INTO comics (title, description, last_chapter, cover_filename, "
            "updated_at) VALUES (?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
            (title, description, last_chapter, cover_filename),
        )
        db.commit()
        return redirect(url_for("home"))

    return render_template("form.html", mode="add", comic={}, error=None)


@app.route("/edit/<int:comic_id>", methods=["GET", "POST"])
def edit(comic_id):
    db = get_db()
    comic = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    if comic is None:
        return redirect(url_for("home"))

    if request.method == "POST":
        title = request.form.get("title", "").strip()
        description = request.form.get("description", "").strip()
        last_chapter = request.form.get("last_chapter", "").strip()

        if not title:
            return render_template(
                "form.html",
                mode="edit",
                comic={**dict(comic), **request.form},
                error="Title can't be empty.",
            )

        new_cover = save_cover(request.files.get("cover"))
        cover_filename = comic["cover_filename"]
        if new_cover:
            delete_cover(comic["cover_filename"])
            cover_filename = new_cover
        elif request.form.get("remove_cover") == "1":
            delete_cover(comic["cover_filename"])
            cover_filename = None

        db.execute(
            "UPDATE comics SET title = ?, description = ?, last_chapter = ?, "
            "cover_filename = ?, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') "
            "WHERE id = ?",
            (title, description, last_chapter, cover_filename, comic_id),
        )
        db.commit()
        return redirect(url_for("home"))

    return render_template("form.html", mode="edit", comic=comic, error=None)


@app.route("/delete/<int:comic_id>", methods=["POST"])
def delete(comic_id):
    db = get_db()
    comic = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    if comic is not None:
        delete_cover(comic["cover_filename"])
        db.execute("DELETE FROM comics WHERE id = ?", (comic_id,))
        db.commit()
    return redirect(url_for("home"))


# ---------- JSON API (used by the Android app for offline sync) ----------

def comic_to_dict(row):
    return {
        "id": row["id"],
        "title": row["title"],
        "description": row["description"] or "",
        "last_chapter": row["last_chapter"] or "",
        "cover_path": (
            f"/static/uploads/{row['cover_filename']}" if row["cover_filename"] else None
        ),
        "updated_at": row["updated_at"],
    }


@app.route("/api/health")
def api_health():
    return {"status": "ok"}


@app.route("/api/comics", methods=["GET", "POST"])
def api_comics():
    db = get_db()
    if request.method == "GET":
        rows = db.execute("SELECT * FROM comics ORDER BY title COLLATE NOCASE ASC").fetchall()
        return {"comics": [comic_to_dict(r) for r in rows]}

    data = request.get_json(silent=True) or {}
    title = (data.get("title") or "").strip()
    if not title:
        return {"error": "title is required"}, 400
    description = (data.get("description") or "").strip()
    last_chapter = (data.get("last_chapter") or "").strip()

    cur = db.execute(
        "INSERT INTO comics (title, description, last_chapter, updated_at) "
        "VALUES (?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
        (title, description, last_chapter),
    )
    db.commit()
    row = db.execute("SELECT * FROM comics WHERE id = ?", (cur.lastrowid,)).fetchone()
    return comic_to_dict(row), 201


@app.route("/api/comics/<int:comic_id>", methods=["PUT", "DELETE"])
def api_comic_detail(comic_id):
    db = get_db()
    row = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    if row is None:
        return {"error": "not found"}, 404

    if request.method == "DELETE":
        delete_cover(row["cover_filename"])
        db.execute("DELETE FROM comics WHERE id = ?", (comic_id,))
        db.commit()
        return {"status": "deleted"}

    data = request.get_json(silent=True) or {}
    title = (data.get("title") or row["title"]).strip()
    if not title:
        return {"error": "title is required"}, 400
    description = data.get("description", row["description"] or "")
    last_chapter = data.get("last_chapter", row["last_chapter"] or "")

    db.execute(
        "UPDATE comics SET title = ?, description = ?, last_chapter = ?, "
        "updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?",
        (title, description, last_chapter, comic_id),
    )
    db.commit()
    row = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    return comic_to_dict(row)


@app.route("/api/comics/<int:comic_id>/cover", methods=["POST"])
def api_comic_cover(comic_id):
    db = get_db()
    row = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    if row is None:
        return {"error": "not found"}, 404

    new_filename = save_cover(request.files.get("cover"))
    if new_filename is None:
        return {"error": "no valid image file provided"}, 400

    delete_cover(row["cover_filename"])
    db.execute(
        "UPDATE comics SET cover_filename = ?, "
        "updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?",
        (new_filename, comic_id),
    )
    db.commit()
    row = db.execute("SELECT * FROM comics WHERE id = ?", (comic_id,)).fetchone()
    return comic_to_dict(row)


if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0", debug=True, port=5050)
