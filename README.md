# Fichero Offline for Android

Offline Android wrapper for the open-source Fichero D11s label-printer project.

The app bundles the upstream web label designer and adds a native Android Bluetooth LE bridge, so printing works without Web Bluetooth and without the Android INTERNET permission.

Upstream project: https://github.com/casper-aurai/fichero-printer  
Pinned upstream revision: c7e060f111ae830e3db2fde403170ef3df3b7ddb

## Features

- Full upstream label designer UI
- Text, images, QR codes, barcodes and vector objects
- Drag/resize/rotate, layers, undo/redo and local persistence
- Print preview, threshold/Atkinson/Bayer processing and inversion
- Copies, density, label type and offsets
- Native BLE connection to FICHERO / D11s printers
- Battery/status information and heartbeat
- Import files through Android's document picker
- Export PNG/JSON to Downloads/Fichero
- Android system print hand-off
- No INTERNET permission; app content is bundled locally

## Build

GitHub Actions builds an installable debug APK on every push to main. The workflow first builds the pinned upstream Svelte app, patches Android-specific file export/system print hooks, then packages the result in a local-only WebView shell.

## Notes

The BLE protocol implementation and web editor come from the upstream Fichero project. This repository contains the Android bridge and build/package integration.
