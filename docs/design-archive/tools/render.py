#!/usr/bin/env python3
"""Render a .dc.html artboard to PNG locally with headless Chrome and a minimal template runtime."""
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
BLOB_MAP = ROOT / "tools" / "blobs.json"
CHROME = os.environ.get("CHROME_BIN", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")

RUNTIME = r"""
class DCLogic { constructor(p) { this.props = p || {}; } setState() {} forceUpdate() {} }
const HOLE = /\{\{\s*([^}]+?)\s*\}\}/g;
function get(path, scope) {
  if (path === 'true') return true;
  if (path === 'false') return false;
  return path.split('.').reduce((v, k) => (v == null ? v : v[k]), scope);
}
function str(text, scope) {
  return text.replace(HOLE, (_, p) => { const v = get(p, scope); return v == null ? '' : String(v); });
}
function whole(text, scope) {
  const m = text && text.match(/^\s*\{\{\s*([^}]+?)\s*\}\}\s*$/);
  return m ? get(m[1], scope) : text;
}
function walk(node, scope) {
  if (node.nodeType === 3) return [document.createTextNode(str(node.nodeValue, scope))];
  if (node.nodeType !== 1) return [];
  const tag = node.tagName.toLowerCase();
  const kids = (n, s) => [...n.childNodes].flatMap((c) => walk(c, s));
  if (tag === 'sc-for') {
    const list = whole(node.getAttribute('list'), scope) || [];
    const as = node.getAttribute('as');
    return list.flatMap((it, i) => { const s = Object.create(scope); s[as] = it; s.$index = i; return kids(node, s); });
  }
  if (tag === 'sc-if') return whole(node.getAttribute('value'), scope) ? kids(node, scope) : [];
  const el = node.cloneNode(false);
  for (const a of [...el.attributes]) {
    if (a.name.startsWith('on')) { el.removeAttribute(a.name); continue; }
    if (!a.value.includes('{{')) continue;
    const w = whole(a.value, scope);
    if (typeof w === 'boolean') { if (w) el.setAttribute(a.name, ''); else el.removeAttribute(a.name); }
    else el.setAttribute(a.name, str(a.value, scope));
  }
  kids(node, scope).forEach((c) => el.appendChild(c));
  return [el];
}
const vals = new Component({}).renderVals() || {};
const tpl = document.getElementById('tpl').content;
const out = document.getElementById('root');
[...tpl.childNodes].flatMap((c) => walk(c, vals)).forEach((n) => out.appendChild(n));
"""


def render(name: str, width: int, height: int) -> pathlib.Path:
    src = (ROOT / "source" / f"{name}.dc.html").read_text()
    body = re.search(r"<x-dc>(.*)</x-dc>", src, re.S).group(1)
    helmet = re.search(r"<helmet>(.*?)</helmet>", body, re.S).group(1)
    body = re.sub(r"<helmet>.*?</helmet>", "", body, flags=re.S)
    logic = re.search(r'<script type="text/x-dc"[^>]*>(.*?)</script>', src, re.S).group(1)
    for blob, file in json.loads(BLOB_MAP.read_text()).items():
        body = body.replace(f"/_blob/{blob}", (ROOT / "images" / file).as_uri())
        logic = logic.replace(f"/_blob/{blob}", (ROOT / "images" / file).as_uri())
    png = ROOT / "previews" / f"{name}.png"
    with tempfile.TemporaryDirectory() as work:
        html = pathlib.Path(work) / f"{name}.html"
        html.write_text(
            '<!doctype html><html lang="ko"><head><meta charset="utf-8">' + helmet + "</head><body>"
            '<div id="root"></div><template id="tpl">' + body + "</template>"
            "<script>class __P{}; </script><script>" + RUNTIME.split("const vals")[0] + logic + "\nconst vals" + RUNTIME.split("const vals")[1] + "</script></body></html>"
        )
        subprocess.run(
            [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--user-data-dir={pathlib.Path(work) / 'profile'}",
             "--allow-file-access-from-files", "--force-device-scale-factor=1", "--virtual-time-budget=8000",
             f"--window-size={width},{height}", f"--screenshot={png}", html.as_uri()],
            check=True, capture_output=True, timeout=120,
        )
    return png


if __name__ == "__main__":
    print(render(sys.argv[1], int(sys.argv[2]), int(sys.argv[3])))
