import os, sqlite3, secrets, hashlib, time, uuid, mimetypes
from pathlib import Path
from dotenv import load_dotenv
from typing import Optional, List
from fastapi import FastAPI, HTTPException, Depends, Header, UploadFile, File
from fastapi.responses import FileResponse
from pydantic import BaseModel, EmailStr, Field
from passlib.context import CryptContext
from jose import jwt

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

DATA_DIR = Path(os.getenv("HALIMEO_DATA_DIR", "/data"))
DATA_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH = DATA_DIR / "halimeo.sqlite3"
PHOTO_DIR = DATA_DIR / "photos"
PHOTO_DIR.mkdir(parents=True, exist_ok=True)
MAX_PHOTO_BYTES = 15 * 1024 * 1024
PHOTO_TYPES = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}
JWT_SECRET = os.getenv("HALIMEO_JWT_SECRET", "")
OWNER_ID = os.getenv("HALIMEO_OWNER_USER_ID", "")
pwd = CryptContext(schemes=["bcrypt"], deprecated="auto")
app = FastAPI(title="HaliMeo API", version="1.0.0")

def db():
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA foreign_keys=ON")
    return c

with db() as c:
    c.executescript("""
    CREATE TABLE IF NOT EXISTS users(
      id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, display_name TEXT NOT NULL,
      password_hash TEXT NOT NULL, created_at INTEGER NOT NULL, disabled INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS invites(
      code_hash TEXT PRIMARY KEY, created_by TEXT NOT NULL, created_at INTEGER NOT NULL,
      expires_at INTEGER NOT NULL, used_at INTEGER, used_by TEXT
    );
    CREATE TABLE IF NOT EXISTS entries(
      id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL,
      category TEXT NOT NULL, memory_date TEXT NOT NULL, created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL, deleted_at INTEGER
    );
    CREATE TABLE IF NOT EXISTS audit_log(
      id INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL, action TEXT NOT NULL, at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS entry_likes(
      entry_id TEXT NOT NULL, user_id TEXT NOT NULL, created_at INTEGER NOT NULL,
      PRIMARY KEY(entry_id,user_id), FOREIGN KEY(entry_id) REFERENCES entries(id) ON DELETE CASCADE,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS entry_comments(
      id TEXT PRIMARY KEY, entry_id TEXT NOT NULL, user_id TEXT NOT NULL, body TEXT NOT NULL,
      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER,
      FOREIGN KEY(entry_id) REFERENCES entries(id) ON DELETE CASCADE,
      FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
    );
    CREATE TABLE IF NOT EXISTS albums(
      id TEXT PRIMARY KEY, created_by TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
      created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, deleted_at INTEGER,
      FOREIGN KEY(created_by) REFERENCES users(id)
    );
    CREATE TABLE IF NOT EXISTS album_entries(
      album_id TEXT NOT NULL, entry_id TEXT NOT NULL, added_by TEXT NOT NULL, added_at INTEGER NOT NULL,
      PRIMARY KEY(album_id,entry_id), FOREIGN KEY(album_id) REFERENCES albums(id) ON DELETE CASCADE,
      FOREIGN KEY(entry_id) REFERENCES entries(id) ON DELETE CASCADE,
      FOREIGN KEY(added_by) REFERENCES users(id)
    );
    CREATE INDEX IF NOT EXISTS idx_entries_updated ON entries(updated_at);
    CREATE INDEX IF NOT EXISTS idx_comments_entry ON entry_comments(entry_id,created_at);
    CREATE INDEX IF NOT EXISTS idx_album_entries_album ON album_entries(album_id,added_at);
    """)
    columns = {row[1] for row in c.execute("PRAGMA table_info(entries)").fetchall()}
    if "photo_updated_at" not in columns:
        c.execute("ALTER TABLE entries ADD COLUMN photo_updated_at INTEGER NOT NULL DEFAULT 0")
    if "photo_deleted_at" not in columns:
        c.execute("ALTER TABLE entries ADD COLUMN photo_deleted_at INTEGER")
    if "photo_filename" not in columns:
        c.execute("ALTER TABLE entries ADD COLUMN photo_filename TEXT")

def now_ms(): return int(time.time() * 1000)
def make_token(user):
    if len(JWT_SECRET) < 32:
        raise HTTPException(503, "Server non configurato: imposta HALIMEO_JWT_SECRET")
    now = int(time.time())
    return jwt.encode({"sub": user["id"], "iss": "halimeo", "iat": now, "exp": now + 7*86400}, JWT_SECRET, algorithm="HS256")

def current_user(authorization: Optional[str] = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Sessione richiesta")
    try:
        claims = jwt.decode(authorization[7:], JWT_SECRET, algorithms=["HS256"], issuer="halimeo")
    except Exception:
        raise HTTPException(401, "Sessione non valida o scaduta")
    with db() as c:
        row = c.execute("SELECT * FROM users WHERE id=? AND disabled=0", (claims.get("sub"),)).fetchone()
    if not row: raise HTTPException(401, "Account non disponibile")
    return dict(row)

def public_user(u): return {"userId": u["id"], "displayName": u["display_name"]}

class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=10, max_length=128)
class RedeemIn(LoginIn):
    inviteCode: str = Field(min_length=16, max_length=128)
    displayName: str = Field(min_length=1, max_length=60)
class InviteIn(BaseModel):
    expiresInDays: int = Field(default=7, ge=1, le=30)
class Entry(BaseModel):
    id: str = Field(min_length=1, max_length=80)
    title: str = Field(max_length=200)
    body: str = Field(max_length=30000)
    category: str = Field(default="Ricordo", max_length=80)
    memoryDate: str = Field(max_length=30)
    createdAt: int
    updatedAt: int
    deletedAt: Optional[int] = None
    photoUpdatedAt: int = 0
    photoDeletedAt: Optional[int] = None
class SyncIn(BaseModel):
    changes: List[Entry] = Field(default_factory=list, max_length=500)
    lastSyncAt: Optional[int] = None

class CommentIn(BaseModel):
    body: str = Field(min_length=1, max_length=4000)
    clientId: Optional[str] = Field(default=None, min_length=1, max_length=80)

class AlbumIn(BaseModel):
    title: str = Field(min_length=1, max_length=100)
    description: str = Field(default="", max_length=2000)

class AlbumEntryIn(BaseModel):
    entryId: str = Field(min_length=1, max_length=80)

@app.get("/v1/health")
def health(): return {"status":"ok","service":"halimeo","time":now_ms()}

@app.post("/v1/auth/login")
def login(data: LoginIn):
    with db() as c:
        row = c.execute("SELECT * FROM users WHERE email=? AND disabled=0", (data.email.lower(),)).fetchone()
    if not row or not pwd.verify(data.password, row["password_hash"]):
        raise HTTPException(401, "Email o password non corretti")
    return {"accessToken":make_token(dict(row)),"tokenType":"Bearer",**public_user(dict(row))}

@app.get("/v1/auth/me")
def me(user=Depends(current_user)):
    return {"userId": user["id"], "displayName": user["display_name"], "isOwner": bool(OWNER_ID and user["id"] == OWNER_ID)}


@app.post("/v1/auth/redeem-invite")
def redeem(data: RedeemIn):
    now = int(time.time())
    digest = hashlib.sha256(data.inviteCode.encode()).hexdigest()
    user_id = secrets.token_urlsafe(18)
    with db() as c:
        inv = c.execute("SELECT * FROM invites WHERE code_hash=?", (digest,)).fetchone()
        if not inv or inv["used_at"] is not None or inv["expires_at"] < now:
            raise HTTPException(400, "Invito non valido, scaduto o già utilizzato")
        if c.execute("SELECT 1 FROM users WHERE email=?", (data.email.lower(),)).fetchone():
            raise HTTPException(409, "Email già registrata")
        c.execute("INSERT INTO users(id,email,display_name,password_hash,created_at) VALUES(?,?,?,?,?)",
                  (user_id,data.email.lower(),data.displayName.strip(),pwd.hash(data.password),now))
        c.execute("UPDATE invites SET used_at=?,used_by=? WHERE code_hash=?", (now,user_id,digest))
        c.execute("INSERT INTO audit_log(user_id,action,at) VALUES(?,?,?)", (user_id,"invite_redeemed",now))
        user = c.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    return {"accessToken":make_token(dict(user)),"tokenType":"Bearer",**public_user(dict(user))}

@app.post("/v1/admin/invites")
def create_invite(data: InviteIn, user=Depends(current_user)):
    if not OWNER_ID or user["id"] != OWNER_ID:
        raise HTTPException(403, "Solo il proprietario può creare inviti")
    raw = secrets.token_urlsafe(24)
    digest = hashlib.sha256(raw.encode()).hexdigest()
    now = int(time.time())
    with db() as c:
        c.execute("INSERT INTO invites(code_hash,created_by,created_at,expires_at) VALUES(?,?,?,?)",
                  (digest,user["id"],now,now+data.expiresInDays*86400))
    return {"inviteCode":raw,"expiresAt":now+data.expiresInDays*86400,"uses":1}

@app.post("/v1/sync")
def sync(data: SyncIn, user=Depends(current_user)):
    now = now_ms()
    with db() as c:
        for e in data.changes:
            old = c.execute("SELECT updated_at,photo_updated_at,photo_deleted_at,photo_filename FROM entries WHERE id=?", (e.id,)).fetchone()
            if old and old["updated_at"] > e.updatedAt: continue
            c.execute("""
            INSERT INTO entries(id,owner_id,title,body,category,memory_date,created_at,updated_at,deleted_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET title=excluded.title,body=excluded.body,category=excluded.category,
            memory_date=excluded.memory_date,updated_at=excluded.updated_at,deleted_at=excluded.deleted_at
            WHERE entries.updated_at <= excluded.updated_at
            """, (e.id,user["id"],e.title,e.body,e.category,e.memoryDate,e.createdAt,e.updatedAt,e.deletedAt))
        cursor = data.lastSyncAt or 0
        rows = c.execute("SELECT * FROM entries WHERE updated_at > ? ORDER BY updated_at ASC LIMIT 1000", (cursor,)).fetchall()
    return {"changes":[{"id":r["id"],"title":r["title"],"body":r["body"],"category":r["category"],
      "memoryDate":r["memory_date"],"createdAt":r["created_at"],"updatedAt":r["updated_at"],"deletedAt":r["deleted_at"],"photoUpdatedAt":r["photo_updated_at"],"photoDeletedAt":r["photo_deleted_at"]} for r in rows],
      "serverTime":now}


def require_entry(c, entry_id):
    row = c.execute("SELECT id FROM entries WHERE id=? AND deleted_at IS NULL", (entry_id,)).fetchone()
    if not row: raise HTTPException(404, "Ricordo non trovato")
    return row

@app.get("/v1/social/feed")
def social_feed(user=Depends(current_user)):
    with db() as c:
        rows = c.execute("""
          SELECT e.*, u.display_name AS author_name,
            (SELECT COUNT(*) FROM entry_likes l WHERE l.entry_id=e.id) AS like_count,
            EXISTS(SELECT 1 FROM entry_likes l WHERE l.entry_id=e.id AND l.user_id=?) AS liked_by_me,
            (SELECT COUNT(*) FROM entry_comments cm WHERE cm.entry_id=e.id AND cm.deleted_at IS NULL) AS comment_count
          FROM entries e LEFT JOIN users u ON u.id=e.owner_id
          WHERE e.deleted_at IS NULL ORDER BY e.memory_date DESC,e.created_at DESC LIMIT 500
        """, (user["id"],)).fetchall()
        result = []
        for r in rows:
            comments = c.execute("""
              SELECT cm.id,cm.body,cm.created_at AS createdAt,cm.updated_at AS updatedAt,
                cm.user_id AS userId,u.display_name AS authorName
              FROM entry_comments cm JOIN users u ON u.id=cm.user_id
              WHERE cm.entry_id=? AND cm.deleted_at IS NULL ORDER BY cm.created_at ASC LIMIT 100
            """, (r["id"],)).fetchall()
            result.append({
              "id":r["id"],"title":r["title"],"body":r["body"],"category":r["category"],
              "memoryDate":r["memory_date"],"createdAt":r["created_at"],"updatedAt":r["updated_at"],
              "authorId":r["owner_id"],"authorName":r["author_name"] or "Account disattivato",
              "likeCount":r["like_count"],"likedByMe":bool(r["liked_by_me"]),
              "commentCount":r["comment_count"],"comments":[dict(x) for x in comments]
            })
    return {"entries":result,"serverTime":now_ms()}

@app.post("/v1/entries/{entry_id}/like")
def like_entry(entry_id: str, user=Depends(current_user)):
    with db() as c:
        require_entry(c, entry_id)
        c.execute("INSERT OR IGNORE INTO entry_likes(entry_id,user_id,created_at) VALUES(?,?,?)",
                  (entry_id,user["id"],now_ms()))
        count = c.execute("SELECT COUNT(*) FROM entry_likes WHERE entry_id=?", (entry_id,)).fetchone()[0]
    return {"liked":True,"likeCount":count}

@app.delete("/v1/entries/{entry_id}/like")
def unlike_entry(entry_id: str, user=Depends(current_user)):
    with db() as c:
        require_entry(c, entry_id)
        c.execute("DELETE FROM entry_likes WHERE entry_id=? AND user_id=?", (entry_id,user["id"]))
        count = c.execute("SELECT COUNT(*) FROM entry_likes WHERE entry_id=?", (entry_id,)).fetchone()[0]
    return {"liked":False,"likeCount":count}

@app.get("/v1/entries/{entry_id}/comments")
def list_comments(entry_id: str, user=Depends(current_user)):
    with db() as c:
        require_entry(c, entry_id)
        rows = c.execute("""
          SELECT cm.id,cm.body,cm.created_at AS createdAt,cm.updated_at AS updatedAt,
            cm.user_id AS userId,u.display_name AS authorName
          FROM entry_comments cm JOIN users u ON u.id=cm.user_id
          WHERE cm.entry_id=? AND cm.deleted_at IS NULL ORDER BY cm.created_at ASC LIMIT 200
        """, (entry_id,)).fetchall()
    return {"comments":[dict(r) for r in rows]}

@app.post("/v1/entries/{entry_id}/comments")
def create_comment(entry_id: str, data: CommentIn, user=Depends(current_user)):
    now=now_ms()
    comment_id=data.clientId or uuid.uuid4().hex
    body=data.body.strip()
    if not body: raise HTTPException(422,"Il commento non può essere vuoto")
    with db() as c:
        require_entry(c, entry_id)
        existing = c.execute("SELECT entry_id,user_id FROM entry_comments WHERE id=?", (comment_id,)).fetchone()
        if existing and (existing["entry_id"] != entry_id or existing["user_id"] != user["id"]):
            raise HTTPException(409,"Identificativo commento già utilizzato")
        c.execute("INSERT OR IGNORE INTO entry_comments(id,entry_id,user_id,body,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                  (comment_id,entry_id,user["id"],body,now,now))
        row = c.execute("""
          SELECT cm.id,cm.entry_id,cm.body,cm.user_id,cm.created_at,cm.updated_at,u.display_name
          FROM entry_comments cm JOIN users u ON u.id=cm.user_id WHERE cm.id=?
        """, (comment_id,)).fetchone()
    return {"id":row["id"],"entryId":row["entry_id"],"body":row["body"],"userId":row["user_id"],
            "authorName":row["display_name"],"createdAt":row["created_at"],"updatedAt":row["updated_at"]}

@app.delete("/v1/comments/{comment_id}")
def delete_comment(comment_id: str, user=Depends(current_user)):
    now=now_ms()
    with db() as c:
        row=c.execute("SELECT user_id FROM entry_comments WHERE id=? AND deleted_at IS NULL",(comment_id,)).fetchone()
        if not row: raise HTTPException(404,"Commento non trovato")
        if row["user_id"] != user["id"] and (not OWNER_ID or user["id"] != OWNER_ID):
            raise HTTPException(403,"Non puoi eliminare questo commento")
        c.execute("UPDATE entry_comments SET deleted_at=?,updated_at=? WHERE id=?",(now,now,comment_id))
    return {"deleted":True}

@app.get("/v1/albums")
def list_albums(user=Depends(current_user)):
    with db() as c:
        rows=c.execute("""
          SELECT a.*,COUNT(ae.entry_id) AS entry_count
          FROM albums a LEFT JOIN album_entries ae ON ae.album_id=a.id
          WHERE a.deleted_at IS NULL GROUP BY a.id ORDER BY a.updated_at DESC
        """).fetchall()
    return {"albums":[{"id":r["id"],"title":r["title"],"description":r["description"],
      "createdBy":r["created_by"],"createdAt":r["created_at"],"updatedAt":r["updated_at"],
      "entryCount":r["entry_count"]} for r in rows]}

@app.post("/v1/albums")
def create_album(data: AlbumIn, user=Depends(current_user)):
    now=now_ms()
    album_id=uuid.uuid4().hex
    title=data.title.strip()
    if not title: raise HTTPException(422,"Il titolo dell'album non può essere vuoto")
    with db() as c:
        c.execute("INSERT INTO albums(id,created_by,title,description,created_at,updated_at) VALUES(?,?,?,?,?,?)",
                  (album_id,user["id"],title,data.description.strip(),now,now))
    return {"id":album_id,"title":title,"description":data.description.strip(),
            "createdBy":user["id"],"createdAt":now,"updatedAt":now,"entryCount":0}

@app.get("/v1/albums/{album_id}")
def get_album(album_id: str, user=Depends(current_user)):
    with db() as c:
        album=c.execute("SELECT * FROM albums WHERE id=? AND deleted_at IS NULL",(album_id,)).fetchone()
        if not album: raise HTTPException(404,"Album non trovato")
        rows=c.execute("""
          SELECT e.*,u.display_name AS author_name FROM album_entries ae
          JOIN entries e ON e.id=ae.entry_id LEFT JOIN users u ON u.id=e.owner_id
          WHERE ae.album_id=? AND e.deleted_at IS NULL ORDER BY ae.added_at DESC
        """,(album_id,)).fetchall()
    return {"id":album["id"],"title":album["title"],"description":album["description"],
      "createdBy":album["created_by"],"entries":[{"id":r["id"],"title":r["title"],"body":r["body"],
      "category":r["category"],"memoryDate":r["memory_date"],"authorId":r["owner_id"],
      "authorName":r["author_name"] or "Account disattivato"} for r in rows]}

@app.post("/v1/albums/{album_id}/entries")
def add_album_entry(album_id: str, data: AlbumEntryIn, user=Depends(current_user)):
    now=now_ms()
    with db() as c:
        album=c.execute("SELECT id FROM albums WHERE id=? AND deleted_at IS NULL",(album_id,)).fetchone()
        if not album: raise HTTPException(404,"Album non trovato")
        require_entry(c,data.entryId)
        c.execute("INSERT OR IGNORE INTO album_entries(album_id,entry_id,added_by,added_at) VALUES(?,?,?,?)",
                  (album_id,data.entryId,user["id"],now))
        c.execute("UPDATE albums SET updated_at=? WHERE id=?",(now,album_id))
    return {"added":True}

@app.delete("/v1/albums/{album_id}/entries/{entry_id}")
def remove_album_entry(album_id: str, entry_id: str, user=Depends(current_user)):
    with db() as c:
        c.execute("DELETE FROM album_entries WHERE album_id=? AND entry_id=?",(album_id,entry_id))
        c.execute("UPDATE albums SET updated_at=? WHERE id=?",(now_ms(),album_id))
    return {"removed":True}

@app.post("/v1/entries/{entry_id}/photo")
async def upload_entry_photo(entry_id: str, photoUpdatedAt: int, photo: UploadFile = File(...), user=Depends(current_user)):
    content_type = (photo.content_type or "").lower().split(";")[0].strip()
    if content_type not in PHOTO_TYPES:
        raise HTTPException(415, "Formato foto non supportato: usa JPG, PNG o WebP")
    payload = await photo.read(MAX_PHOTO_BYTES + 1)
    if not payload or len(payload) > MAX_PHOTO_BYTES:
        raise HTTPException(413, "La foto deve pesare al massimo 15 MB")
    if content_type == "image/jpeg" and not payload.startswith(b"\xff\xd8\xff"):
        raise HTTPException(400, "File JPEG non valido")
    if content_type == "image/png" and not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        raise HTTPException(400, "File PNG non valido")
    if content_type == "image/webp" and not (payload.startswith(b"RIFF") and payload[8:12] == b"WEBP"):
        raise HTTPException(400, "File WebP non valido")
    with db() as c:
        row = c.execute("SELECT id,photo_filename,photo_updated_at FROM entries WHERE id=?", (entry_id,)).fetchone()
        if not row:
            raise HTTPException(404, "Ricordo non trovato")
        if photoUpdatedAt < row["photo_updated_at"]:
            return {"ignored": True, "photoUpdatedAt": row["photo_updated_at"]}
        filename = uuid.uuid4().hex + PHOTO_TYPES[content_type]
        (PHOTO_DIR / filename).write_bytes(payload)
        stamp = max(photoUpdatedAt, now_ms())
        c.execute("UPDATE entries SET photo_filename=?,photo_updated_at=?,photo_deleted_at=NULL,updated_at=MAX(updated_at,?) WHERE id=?", (filename,stamp,now_ms(),entry_id))
        old_filename = row["photo_filename"]
    if old_filename and old_filename != filename:
        (PHOTO_DIR / old_filename).unlink(missing_ok=True)
    return {"photoUpdatedAt": stamp, "size": len(payload), "contentType": content_type}

@app.get("/v1/entries/{entry_id}/photo")
def download_entry_photo(entry_id: str, user=Depends(current_user)):
    with db() as c:
        row = c.execute("SELECT photo_filename,photo_updated_at FROM entries WHERE id=?", (entry_id,)).fetchone()
    if not row or not row["photo_filename"]:
        raise HTTPException(404, "Foto non disponibile")
    path = PHOTO_DIR / row["photo_filename"]
    if not path.is_file():
        raise HTTPException(404, "File foto non trovato sul server")
    return FileResponse(path, media_type=mimetypes.guess_type(path.name)[0] or "application/octet-stream")

@app.delete("/v1/entries/{entry_id}/photo")
def delete_entry_photo(entry_id: str, photoUpdatedAt: int, user=Depends(current_user)):
    with db() as c:
        row = c.execute("SELECT photo_filename,photo_updated_at FROM entries WHERE id=?", (entry_id,)).fetchone()
        if not row:
            raise HTTPException(404, "Ricordo non trovato")
        if photoUpdatedAt < row["photo_updated_at"]:
            return {"ignored": True, "photoUpdatedAt": row["photo_updated_at"]}
        stamp = max(photoUpdatedAt, now_ms())
        old_filename = row["photo_filename"]
        c.execute("UPDATE entries SET photo_filename=NULL,photo_updated_at=?,photo_deleted_at=?,updated_at=MAX(updated_at,?) WHERE id=?", (stamp,stamp,now_ms(),entry_id))
    if old_filename:
        (PHOTO_DIR / old_filename).unlink(missing_ok=True)
    return {"photoDeletedAt": stamp}

@app.get("/v1/admin/backup")
def server_backup(user=Depends(current_user)):
    if not OWNER_ID or user["id"] != OWNER_ID: raise HTTPException(403, "Solo il proprietario può fare il backup")
    with db() as c:
        entries=[dict(r) for r in c.execute("SELECT * FROM entries ORDER BY updated_at").fetchall()]
        users=[{"id":r["id"],"email":r["email"],"display_name":r["display_name"],"created_at":r["created_at"]} for r in c.execute("SELECT * FROM users").fetchall()]
    return {"format":"halimeo-server-backup-v1","createdAt":now_ms(),"users":users,"entries":entries}

@app.get("/v1/updates/latest.json")
def latest_update():
    p=DATA_DIR/"updates"/"latest.json"
    if not p.exists(): raise HTTPException(404,"Nessuna release pubblicata")
    return FileResponse(p,media_type="application/json")

@app.get("/v1/updates/{filename}")
def download_update(filename: str):
    if not filename.endswith(".apk") or "/" in filename or ".." in filename: raise HTTPException(400,"Nome file non valido")
    p=DATA_DIR/"updates"/filename
    if not p.exists(): raise HTTPException(404,"Release non trovata")
    return FileResponse(p,media_type="application/vnd.android.package-archive",filename=filename)
