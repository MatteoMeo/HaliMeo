#!/usr/bin/env python3
"""Create short Italian release notes from the actual Git range."""
import os, re, subprocess
from collections import defaultdict

def git(*args):
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL).strip()

def clean(value):
    return re.sub(r"\s+", " ", value or "").strip()

def main():
    current = os.environ["CURRENT_SHA"]
    version = os.environ["VERSION_NAME"]
    build = os.environ["BUILD_NUMBER"]
    channel = os.getenv("RELEASE_CHANNEL", "Alpha")
    before = os.getenv("BEFORE_SHA", "")
    tags = git("tag", "--list", f"{channel.lower()}-build-*", "--sort=-version:refname").splitlines()
    previous = tags[0] if tags else ""
    if previous:
        base = previous
    elif before and before != "0000000000000000000000000000000000000000":
        base = before
    else:
        parents = git("rev-list", "--parents", "-n", "1", current).split()
        base = parents[1] if len(parents) > 1 else current

    subjects = git("log", "--format=%s", f"{base}..{current}").splitlines() if base != current else []
    files = git("diff", "--name-only", f"{base}..{current}").splitlines() if base != current else []
    groups, seen = defaultdict(list), set()

    def add(group, text):
        text = clean(text)
        if text and text.casefold() not in seen:
            groups[group].append(text)
            seen.add(text.casefold())

    translations = [
        (r"^restore stable diary feed.*", "Ripristinata la visualizzazione stabile del Diario dopo i problemi del precedente aggiornamento."),
        (r"^fix (.*)", r"Corretto: \1."),
        (r"^add (.*)", r"Aggiunto: \1."),
        (r"^implement (.*)", r"Introdotto: \1."),
        (r"^improve (.*)", r"Migliorato: \1."),
        (r"^optimi[sz]e (.*)", r"Ottimizzato: \1."),
        (r"^refactor (.*)", r"Riorganizzato il codice di \1 per semplificarne la manutenzione."),
        (r"^update (.*)", r"Aggiornato: \1."),
        (r"^remove (.*)", r"Rimosso: \1."),
    ]
    for raw in subjects:
        subject = clean(raw)
        conventional = re.match(r"^(feat|fix|perf|refactor|docs|build|chore|test)(?:\([^)]*\))?!?:\s*(.+)$", subject, re.I)
        kind = conventional.group(1).lower() if conventional else ""
        detail = conventional.group(2) if conventional else subject
        low = subject.lower()
        if kind == "feat" or low.startswith(("add ", "implement ", "introduce ")):
            category = "✨ Novità"
        elif kind == "fix" or low.startswith(("fix ", "restore ", "prevent ", "avoid ")):
            category = "🛠 Correzioni"
        elif kind == "perf" or low.startswith(("optimize ", "optimise ", "improve performance", "speed up ")):
            category = "⚡ Miglioramenti"
        elif kind in ("docs", "build", "chore", "test"):
            category = "🔧 Manutenzione"
        else:
            category = "⚡ Miglioramenti"
        translated = None
        for pattern, replacement in translations:
            if re.match(pattern, detail, re.I):
                translated = re.sub(pattern, replacement, detail, flags=re.I)
                break
        if not translated:
            translated = detail.rstrip(".") + "."
            translated = translated[:1].upper() + translated[1:]
        add(category, translated)

    if not groups:
        areas = set()
        for path in files:
            p = path.lower()
            if p.startswith("app/"): areas.add("app")
            elif p.startswith("server/"): areas.add("server")
            elif p.startswith(".github/"): areas.add("release")
            elif p.startswith("docs/"): areas.add("docs")
        labels = {
            "app": "Interventi sull'app Android e sulla sua interfaccia.",
            "server": "Aggiornamenti ai servizi e alla gestione dei dati.",
            "release": "Migliorato il processo di compilazione e pubblicazione.",
            "docs": "Aggiornata la documentazione del progetto.",
        }
        for area in sorted(areas): add("🔧 Manutenzione", labels[area])
    if not groups: add("🔧 Manutenzione", "Aggiornamenti interni e preparazione della nuova versione.")

    lines = [f"# 🌙 HaliMeo — {channel} {version}", "", f"> **Build {build}** · Canale **{channel}** · Android", "", "Ecco cosa cambia in questa versione:", ""]
    for title in ["✨ Novità", "⚡ Miglioramenti", "🛠 Correzioni", "🔧 Manutenzione"]:
        if groups[title]:
            lines += [f"## {title}", ""]
            lines += [f"- {item}" for item in groups[title][:8]]
            lines.append("")
    lines += ["---", "", "## 📦 Download", "", f"APK: HaliMeo-{version}-build{build}-debug.apk", "", "🧪 **Canale Alpha:** versione preliminare destinata ai test.", "🔐 Firma verificata prima della pubblicazione.", ""]
    with open("CHANGELOG.md", "w", encoding="utf-8") as out: out.write("\n".join(lines))
    print("\n".join(lines))

if __name__ == "__main__": main()
