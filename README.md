# Fichero Offline for Android

Offline Android wrapper for [Fichero](https://github.com/casper-aurai/fichero-printer).
Pinned upstream: c7e060f111ae830e3db2fde403170ef3df3b7ddb.

## 0.1.5 — Material 3 interface

- Material 3 color roles, system light/dark preference, rounded controls and panels.
- 48 dp button targets, 80 dp bottom navigation with selected indicators.
- Polish editor navigation, preview, print and connection actions.
- Visible keyboard focus, reduced motion support and accessible page zoom.
- System bar appearance follows the system theme; keyboard insets protect the editor.
- Existing editor, BLE bridge, local project storage, imports and exports retained.
- No INTERNET permission and no remote fonts, styles or scripts added.

## Build

GitHub Actions checks the pinned Svelte editor with `npm run check`, builds
the web bundle and assembles debug/release APKs. It checks the final release
manifest for absence of INTERNET permission. Pull requests also run this build.

The debug APK is installable. Release APKs use the existing signing secrets
when configured; otherwise the release APK needs signing. An in-place update
requires the same certificate as the installed version. Export important
projects before changing installation certificates.

## Device validation

A successful build does not validate physical BLE printing. Check connection,
text/image/QR editing, preview, printing, import/export, light/dark mode,
keyboard visibility, rotation, and a small phone display on a real device.
