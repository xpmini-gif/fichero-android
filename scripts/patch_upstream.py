#!/usr/bin/env python3
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
upstream = root.parent / "upstream"

client_src = root / "web-patches" / "client.android.ts"
client_dst = upstream / "web" / "src" / "lib" / "fichero" / "client.ts"
shutil.copy2(client_src, client_dst)

file_utils = upstream / "web" / "src" / "utils" / "file_utils.ts"
text = file_utils.read_text()

download_marker = """  static async downloadBase64Web(filename: string, mime: string, base64Data: string) {
    const byteChars = atob(base64Data);
"""
download_replacement = """  static async downloadBase64Web(filename: string, mime: string, base64Data: string) {
    const android = (globalThis as any).FicheroAndroid;
    if (android?.saveBase64) {
      android.saveBase64(filename, mime, base64Data);
      return;
    }

    const byteChars = atob(base64Data);
"""
if download_marker not in text:
    raise SystemExit("downloadBase64Web patch point not found")
text = text.replace(download_marker, download_replacement, 1)

print_marker = """    const iframe = document.createElement("iframe");
"""
print_replacement = """    const android = (globalThis as any).FicheroAndroid;
    if (android?.printHtml) {
      android.printHtml(html);
      return;
    }

    const iframe = document.createElement("iframe");
"""
if print_marker not in text:
    raise SystemExit("printImageUrls patch point not found")
text = text.replace(print_marker, print_replacement, 1)
file_utils.write_text(text)

vite = upstream / "web" / "vite.config.ts"
text = vite.read_text()
old = '  base: process.env.GITHUB_PAGES ? "/fichero-printer/" : "/",'
new = '  base: "./",'
if old not in text:
    raise SystemExit("Vite base patch point not found")
vite.write_text(text.replace(old, new, 1))

# The upstream UI intentionally refuses widths below 900px. On Android we keep
# the exact desktop editor/functionality, but expose it through a 1000px virtual
# viewport that WebView scales to the device. Landscape orientation is especially
# comfortable, while portrait remains fully usable with pinch zoom.
index_html = upstream / "web" / "index.html"
text = index_html.read_text()
old_viewport = '<meta name="viewport" content="width=device-width, initial-scale=1.0" />'
new_viewport = '<meta name="viewport" content="width=1000, initial-scale=1.0, minimum-scale=0.25, maximum-scale=3.0, user-scalable=yes" />'
if old_viewport not in text:
    raise SystemExit("Viewport patch point not found")
index_html.write_text(text.replace(old_viewport, new_viewport, 1))

print("Android patches applied")
