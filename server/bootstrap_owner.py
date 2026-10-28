"""Run inside the server container to create the first private owner account."""
import os, secrets, sqlite3, getpass, time
from pathlib import Path
from passlib.context import CryptContext

data_dir = Path(os.getenv("HALIMEO_DATA_DIR", "/data"))
data_dir.mkdir(parents=True, exist_ok=True)
db_path = data_dir / "halimeo.sqlite3"
# Keep this script usable before the API has ever been started.
with sqlite3.connect(db_path) as c:
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
email = input("Owner email: ").strip().lower()
name = input("Display name: ").strip()
password = getpass.getpass("Password (minimum 12 characters): ")
if len(password) < 12:
    raise SystemExit("Password too short.")
uid = secrets.token_urlsafe(18)
pw_hash = CryptContext(schemes=["bcrypt"], deprecated="auto").hash(password)
with sqlite3.connect(db_path) as c:
    c.execute("INSERT INTO users(id,email,display_name,password_hash,created_at) VALUES(?,?,?,?,?)",
              (uid,email,name,pw_hash,int(time.time())))
print("Owner created. Set HALIMEO_OWNER_USER_ID to this ID in server/.env:")
print(uid)
