package com.xpmini.fichero;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BLE = 1001;
    private static final int REQ_FILE = 1002;

    private static final UUID SERVICE_UUID = UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_UUID = UUID.fromString("00002af1-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_UUID = UUID.fromString("00002af0-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Object notifyLock = new Object();
    private final ByteArrayOutputStream notifyBuffer = new ByteArrayOutputStream();

    private FrameLayout root;
    private WebView webView;
    private WebView printWebView;
    private ValueCallback<Uri[]> fileCallback;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private BluetoothGattCharacteristic notifyChar;
    private String pendingConnectId;
    private boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(21, 24, 25));

        // Android 15+ draws apps edge-to-edge by default. Keep the editor out of
        // the status/navigation bars and display cutout while preserving the
        // full WebView area inside those safe bounds.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
        ViewCompat.requestApplyInsets(root);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.rgb(21, 24, 25));
            getWindow().setNavigationBarColor(Color.rgb(21, 24, 25));
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setBackgroundColor(Color.WHITE);
        webView.addJavascriptInterface(new NativeBridge(), "FicheroAndroid");

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equals(uri.getHost())) {
                    return false;
                }
                String scheme = uri.getScheme();
                if ("data".equals(scheme) || "blob".equals(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;

                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }

                try {
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE || fileCallback == null) return;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    private boolean hasBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, REQ_BLE);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQ_BLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_BLE || pendingConnectId == null) return;
        if (hasBlePermissions()) beginScan();
        else {
            String id = pendingConnectId;
            pendingConnectId = null;
            rejectJs(id, "Bluetooth permission denied");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void beginScan() {
        if (pendingConnectId == null) return;
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            String id = pendingConnectId;
            pendingConnectId = null;
            rejectJs(id, "Bluetooth is turned off");
            return;
        }

        scanner = bluetoothAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            String id = pendingConnectId;
            pendingConnectId = null;
            rejectJs(id, "Bluetooth LE scanner unavailable");
            return;
        }

        scanning = true;
        scanner.startScan(scanCallback);
        main.postDelayed(() -> {
            if (!scanning || pendingConnectId == null) return;
            stopScan();
            String id = pendingConnectId;
            pendingConnectId = null;
            rejectJs(id, "FICHERO printer not found");
        }, 12000);
    }

    @SuppressWarnings("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanning) {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
        }
        scanning = false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        @SuppressWarnings("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name;
            try { name = device.getName(); } catch (Exception e) { name = null; }
            if (name == null) {
                try { name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null; }
                catch (Exception ignored) { name = null; }
            }
            if (name == null) return;
            if (!(name.startsWith("FICHERO") || name.startsWith("D11s_"))) return;

            stopScan();
            try {
                gatt = device.connectGatt(MainActivity.this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } catch (Exception e) {
                String id = pendingConnectId;
                pendingConnectId = null;
                rejectJs(id, "Unable to connect: " + e.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (pendingConnectId == null) return;
            String id = pendingConnectId;
            pendingConnectId = null;
            stopScan();
            rejectJs(id, "Bluetooth scan failed (" + errorCode + ")");
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressWarnings("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    bluetoothGatt.requestMtu(247);
                } catch (Exception ignored) {}
                bluetoothGatt.discoverServices();
                return;
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeChar = null;
                notifyChar = null;
                if (pendingConnectId != null) {
                    String id = pendingConnectId;
                    pendingConnectId = null;
                    rejectJs(id, "Printer disconnected during connection");
                } else {
                    evalJs("window.__ficheroNative && window.__ficheroNative.emitDisconnect();");
                }
            }
        }

        @Override
        @SuppressWarnings("MissingPermission")
        public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect("GATT service discovery failed (" + status + ")");
                return;
            }

            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                failConnect("FICHERO BLE service not found");
                return;
            }

            writeChar = service.getCharacteristic(WRITE_UUID);
            notifyChar = service.getCharacteristic(NOTIFY_UUID);
            if (writeChar == null || notifyChar == null) {
                failConnect("FICHERO BLE characteristics not found");
                return;
            }

            bluetoothGatt.setCharacteristicNotification(notifyChar, true);
            BluetoothGattDescriptor cccd = notifyChar.getDescriptor(CCCD_UUID);
            if (cccd == null) {
                failConnect("Notification descriptor not found");
                return;
            }

            boolean started;
            if (Build.VERSION.SDK_INT >= 33) {
                started = bluetoothGatt.writeDescriptor(
                        cccd,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothGatt.GATT_SUCCESS;
            } else {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                started = bluetoothGatt.writeDescriptor(cccd);
            }
            if (!started) failConnect("Could not enable printer notifications");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt bluetoothGatt, BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnect("Could not enable notifications (" + status + ")");
                return;
            }
            String id = pendingConnectId;
            pendingConnectId = null;
            if (id == null) return;
            String name = "FICHERO";
            try {
                if (bluetoothGatt.getDevice().getName() != null) name = bluetoothGatt.getDevice().getName();
            } catch (Exception ignored) {}
            resolveJs(id, name);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
            onNotify(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt bluetoothGatt,
                BluetoothGattCharacteristic characteristic,
                byte[] value
        ) {
            onNotify(value);
        }
    };

    private void onNotify(byte[] value) {
        if (value == null || value.length == 0) return;
        synchronized (notifyLock) {
            notifyBuffer.write(value, 0, value.length);
            notifyLock.notifyAll();
        }
    }

    private void failConnect(String message) {
        String id = pendingConnectId;
        pendingConnectId = null;
        if (id != null) rejectJs(id, message);
        closeGatt();
    }

    @SuppressWarnings("MissingPermission")
    private void closeGatt() {
        stopScan();
        BluetoothGatt local = gatt;
        gatt = null;
        writeChar = null;
        notifyChar = null;
        if (local != null) {
            try { local.disconnect(); } catch (Exception ignored) {}
            try { local.close(); } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("MissingPermission")
    private void writeRaw(byte[] data) throws Exception {
        BluetoothGatt localGatt = gatt;
        BluetoothGattCharacteristic localWrite = writeChar;
        if (localGatt == null || localWrite == null) throw new Exception("Not connected");

        boolean accepted;
        if (Build.VERSION.SDK_INT >= 33) {
            accepted = localGatt.writeCharacteristic(
                    localWrite,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothGatt.GATT_SUCCESS;
        } else {
            localWrite.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            localWrite.setValue(data);
            accepted = localGatt.writeCharacteristic(localWrite);
        }

        if (!accepted) throw new Exception("Bluetooth write rejected");
        Thread.sleep(12);
    }

    private byte[] sendAndMaybeWait(byte[] data, boolean wait, int timeoutMs) throws Exception {
        if (wait) {
            synchronized (notifyLock) {
                notifyBuffer.reset();
            }
        }

        writeRaw(data);
        if (!wait) return new byte[0];

        long deadline = System.currentTimeMillis() + Math.max(100, timeoutMs);
        synchronized (notifyLock) {
            while (notifyBuffer.size() == 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) break;
                notifyLock.wait(remaining);
            }
        }

        Thread.sleep(50);
        synchronized (notifyLock) {
            return notifyBuffer.toByteArray();
        }
    }

    private void resolveJs(String id, String value) {
        String script = "window.__ficheroNative && window.__ficheroNative.resolve("
                + JSONObject.quote(id) + "," + JSONObject.quote(value == null ? "" : value) + ");";
        evalJs(script);
    }

    private void rejectJs(String id, String error) {
        String script = "window.__ficheroNative && window.__ficheroNative.reject("
                + JSONObject.quote(id) + "," + JSONObject.quote(error == null ? "Error" : error) + ");";
        evalJs(script);
    }

    private void evalJs(String script) {
        main.post(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    private void toast(String text) {
        main.post(() -> Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show());
    }

    private void saveBase64File(String filename, String mime, String base64Data) {
        io.execute(() -> {
            try {
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                String safeName = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safeName);
                    values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Fichero");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create output file");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("Could not open output file");
                        out.write(bytes);
                    }
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Fichero");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create export directory");
                    try (FileOutputStream out = new FileOutputStream(new File(dir, safeName))) {
                        out.write(bytes);
                    }
                }
                toast("Saved: " + safeName);
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        });
    }

    private void printHtmlNative(String html) {
        main.post(() -> {
            if (printWebView != null) {
                root.removeView(printWebView);
                printWebView.destroy();
            }

            printWebView = new WebView(MainActivity.this);
            printWebView.getSettings().setJavaScriptEnabled(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(2, 2);
            root.addView(printWebView, lp);

            printWebView.setWebViewClient(new WebViewClient() {
                private boolean printed = false;

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (printed) return;
                    printed = true;
                    PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter = view.createPrintDocumentAdapter("Fichero label");
                    manager.print(
                            "Fichero label",
                            adapter,
                            new PrintAttributes.Builder()
                                    .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                                    .build()
                    );
                }
            });
            printWebView.loadDataWithBaseURL(
                    "https://appassets.androidplatform.net/assets/web/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
            );
        });
    }

    public final class NativeBridge {
        @JavascriptInterface
        public void connect(String requestId) {
            main.post(() -> {
                if (pendingConnectId != null) {
                    rejectJs(requestId, "Connection already in progress");
                    return;
                }
                closeGatt();
                pendingConnectId = requestId;
                if (!hasBlePermissions()) requestBlePermissions();
                else beginScan();
            });
        }

        @JavascriptInterface
        public void disconnect() {
            main.post(MainActivity.this::closeGatt);
        }

        @JavascriptInterface
        public void send(String requestId, String base64, boolean wait, int timeout) {
            io.execute(() -> {
                try {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    byte[] response = sendAndMaybeWait(data, wait, timeout);
                    resolveJs(requestId, Base64.encodeToString(response, Base64.NO_WRAP));
                } catch (Exception e) {
                    rejectJs(requestId, e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void sendChunked(String requestId, String base64, int chunkSize, int delayMs) {
            io.execute(() -> {
                try {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    int size = Math.max(20, Math.min(220, chunkSize));
                    for (int i = 0; i < data.length; i += size) {
                        writeRaw(Arrays.copyOfRange(data, i, Math.min(data.length, i + size)));
                        if (delayMs > 0) Thread.sleep(delayMs);
                    }
                    resolveJs(requestId, "");
                } catch (Exception e) {
                    rejectJs(requestId, e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void saveBase64(String filename, String mime, String base64Data) {
            saveBase64File(filename, mime, base64Data);
        }

        @JavascriptInterface
        public void printHtml(String html) {
            printHtmlNative(html);
        }
    }

    @Override
    protected void onDestroy() {
        closeGatt();
        io.shutdownNow();
        if (webView != null) webView.destroy();
        if (printWebView != null) printWebView.destroy();
        super.onDestroy();
    }
}
