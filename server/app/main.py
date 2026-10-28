import os, sqlite3, secrets, hashlib, time
from pathlib import Path
from typing import Optional, List
from fastapi import FastAPI, HTTPException, Depends, Header
from fastapi.responses import FileResponse
from pydantic import BaseModel, EmailStr, Field
from passlib.context import CryptContext
from jose import jwt

DATA_DIR = Path(os.getenv("HALIMEO_DATA_DIR", "/data"))
DATA_DIR.mkdir(parents=True, exist_ok=True)
DB_PATH = DATA_DIR / "halimeo.sqlite3"
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
    CREATE INDEX IF NOT EXISTS idx_entries_updated ON entries(updated_at);
    """)

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
class SyncIn(BaseModel):
    changes: List[Entry] = Field(default_factory=list, max_length=500)
    lastSyncAt: Optional[int] = None

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
            old = c.execute("SELECT updated_at FROM entries WHERE id=?", (e.id,)).fetchone()
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
      "memoryDate":r["memory_date"],"createdAt":r["created_at"],"updatedAt":r["updated_at"],"deletedAt":r["deleted_at"]} for r in rows],
      "serverTime":now}

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
