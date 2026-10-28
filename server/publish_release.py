#!/usr/bin/env python3
"""Create a release manifest draft; add an Ed25519 signature before production."""
import argparse, hashlib, json, shutil
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument("--apk",required=True)
p.add_argument("--version-code",type=int,required=True)
p.add_argument("--version-name",required=True)
p.add_argument("--public-url",required=True)
p.add_argument("--notes",default="")
p.add_argument("--output-dir",default="data/updates")
a=p.parse_args()
apk=Path(a.apk); out=Path(a.output_dir); out.mkdir(parents=True,exist_ok=True)
name=f"halimeo-{a.version_name}.apk"; target=out/name; shutil.copyfile(apk,target)
digest=hashlib.sha256(target.read_bytes()).hexdigest()
manifest={"versionCode":a.version_code,"versionName":a.version_name,
"apkUrl":a.public_url.rstrip("/")+"/v1/updates/"+name,"sha256":digest,
"releaseNotes":a.notes,"mandatory":False,"signatureEd25519":""}
(out/"latest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
print(f"Draft ready: {target}")
print(f"SHA-256: {digest}")
print("IMPORTANT: sign the canonical manifest with the offline Ed25519 release key before publishing.")
