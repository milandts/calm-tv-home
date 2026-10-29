#!/usr/bin/env python3
"""Prüft, ob die in der Doku genannten Dateien noch existieren und ob jedes `paths`-Muster der Regeln noch Dateien trifft.

Aufruf: python3 scripts/check-doc-refs.py [Projektordner]      (Exit-Code 1 bei toten Verweisen)

Geprüft werden CLAUDE.md, docs/*.md (ohne docs/ideen), .claude/rules/*.md. Als Dateiverweis gilt jede in Backticks gesetzte Angabe mit
bekannter Endung. Ein Name ohne Ordner (`Glass.java`) genügt, wenn es die Datei irgendwo im Projekt gibt. Das Skript prüft nur, ob Verweise
noch existieren, nicht ob die Beschreibung dazu noch stimmt; das bleibt Sache von Code-Kommentaren und Prüfungen.
Ausnahmen (bewusst genannte Dateien, die nicht im Repo liegen) stehen in IGNORIERT mit Begründung.
"""
import fnmatch
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
ENDUNGEN = ("java", "xml", "sh", "py", "md", "json", "swift", "txt", "apk", "keystore", "pass")
SKIP_DIRS = {".git", "work", "out", "__pycache__"}
IGNORIERT = {
    "out/TVHome.apk": "Bauergebnis, nicht versioniert",
    "out/TVHomeDream.apk": "Bauergebnis, nicht versioniert",
    "tvhome.keystore": "Signaturschlüssel, liegt bewusst außerhalb des Repos",
    "keystore.pass": "Kennwortdatei, liegt bewusst außerhalb des Repos",
    "credits.txt": "entsteht beim Bau in launcher/assets/wallpapers",
    "tvhome-sicherung.json": "Sicherungsdatei auf dem Gerät",
    "launcher/assets/wallpapers": "von setup.sh geladen",
}


def project_files():
    return [p for p in ROOT.rglob("*") if p.is_file() and not (set(p.relative_to(ROOT).parts) & SKIP_DIRS)]


FILES = project_files()
REL = [str(p.relative_to(ROOT)) for p in FILES]
NAMES = {p.name for p in FILES}


def doc_files():
    found = [ROOT / "CLAUDE.md"] + sorted((ROOT / "docs").glob("*.md")) + sorted((ROOT / ".claude" / "rules").glob("*.md"))
    return [p for p in found if p.exists()]


def references(text):
    for match in re.finditer(r"`([^`\n]+)`", text):
        token = match.group(1).strip().rstrip(".,:;")
        if re.search(r"[\s*{}<>$=…()|]|://|^[~@/-]", token) or "#" in token:
            continue
        if token.rsplit(".", 1)[-1] in ENDUNGEN and "." in token:
            yield token


def exists(token):
    while token.startswith("../"):   # Verweise aus docs/ auf den Projektstamm
        token = token[3:]
    if token in IGNORIERT or any(token.endswith("/" + k) or token.endswith(k) for k in IGNORIERT if "/" not in k):
        return True
    if "/" in token:
        return token in REL or any(r.endswith("/" + token) for r in REL)
    return token in NAMES


def rule_patterns(path):
    text = path.read_text(encoding="utf-8")
    head = re.match(r"---\n(.*?)\n---", text, re.S)
    return re.findall(r'^\s*-\s*"([^"]+)"', head.group(1), re.M) if head else []


def matches(pattern):
    regex = re.compile(fnmatch.translate(pattern).replace(".*", ".*"))
    simple = pattern.replace("/**", "/*") if pattern.endswith("/**") else pattern
    return any(fnmatch.fnmatch(r, pattern) or fnmatch.fnmatch(r, simple) or regex.match(r) for r in REL)


problems = []
for doc in doc_files():
    for token in sorted(set(references(doc.read_text(encoding="utf-8")))):
        if not exists(token):
            problems.append("%s: toter Verweis `%s`" % (doc.relative_to(ROOT), token))
for rule in sorted((ROOT / ".claude" / "rules").glob("*.md")):
    for pattern in rule_patterns(rule):
        if not matches(pattern):
            problems.append("%s: das Muster \"%s\" trifft keine Datei" % (rule.relative_to(ROOT), pattern))
print("\n".join(problems) if problems else "OK: Doku-Verweise und paths-Muster stimmen")
sys.exit(1 if problems else 0)
