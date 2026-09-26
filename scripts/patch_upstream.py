#!/usr/bin/env python3
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
upstream = root.parent / "upstream"

client_src = root / "web-patches" / "client.android.ts"
client_dst = upstream / "web" / "src" / "lib" / "fichero" / "client.ts"
shutil.copy2(client_src, client_dst)

component_patches = {
    root / "web-patches" / "AppShell.android.svelte": upstream / "web" / "src" / "components" / "shell" / "AppShell.svelte",
    root / "web-patches" / "PanelShell.android.svelte": upstream / "web" / "src" / "components" / "shell" / "PanelShell.svelte",
    root / "web-patches" / "Toolbar.android.svelte": upstream / "web" / "src" / "components" / "shell" / "Toolbar.svelte",
    root / "web-patches" / "PrinterStatus.android.svelte": upstream / "web" / "src" / "components" / "print" / "PrinterStatus.svelte",
}
for src, dst in component_patches.items():
    shutil.copy2(src, dst)

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

utils_file = upstream / "web" / "src" / "lib" / "fichero" / "utils.ts"
text = utils_file.read_text()
old_transport = '      webBluetooth: typeof navigator !== "undefined" && "bluetooth" in navigator,'
new_transport = '      webBluetooth: (typeof navigator !== "undefined" && "bluetooth" in navigator) || typeof (globalThis as any).FicheroAndroid !== "undefined",'
if old_transport not in text:
    raise SystemExit("Bluetooth transport patch point not found")
utils_file.write_text(text.replace(old_transport, new_transport, 1))

vite = upstream / "web" / "vite.config.ts"
text = vite.read_text()
old = '  base: process.env.GITHUB_PAGES ? "/fichero-printer/" : "/",'
new = '  base: "./",'
if old not in text:
    raise SystemExit("Vite base patch point not found")
vite.write_text(text.replace(old, new, 1))

# Native mobile layout: use the real device width and keep page zoom disabled.
# The editor canvas itself auto-fits whenever the available area changes.
index_html = upstream / "web" / "index.html"
text = index_html.read_text()
old_viewport = '<meta name="viewport" content="width=device-width, initial-scale=1.0" />'
new_viewport = '<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover" />'
if old_viewport not in text:
    raise SystemExit("Viewport patch point not found")
index_html.write_text(text.replace(old_viewport, new_viewport, 1))

print("Android patches applied")
