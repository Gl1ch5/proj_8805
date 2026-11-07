package ru.gl1ch5.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

public final class MainActivity extends Activity {

    private static final int LOCATION_REQUEST = 1001;
    private static final String START_URL = "file:///android_asset/weather.html";

    private WebView webView;
    private WeatherBridge bridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        configureWebView(webView);

        bridge = new WeatherBridge(this);
        webView.addJavascriptInterface(bridge, "Native");

        // Всегда грузим локальный HTML заново: restoreState для file:// URL
        // может восстановить пустую страницу без повторного запроса данных.
        webView.loadUrl(START_URL);

        if (!hasLocationPermission()) {
            requestLocationPermission();
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handleUrl(Uri.parse(url));
            }

            private boolean handleUrl(Uri uri) {
                String scheme = uri.getScheme();
                if ("file".equals(scheme) || "about".equals(scheme) || "data".equals(scheme)) {
                    return false;
                }
                if ("https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (ActivityNotFoundException ignored) {
                        // Браузера нет: просто не переходим.
                    }
                }
                return true;
            }
        });

        view.setWebChromeClient(new WebChromeClient());
        view.setBackgroundColor(0xFF0B1020);
        view.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    }

    boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    void requestLocationPermission() {
        runOnUiThread(() -> requestPermissions(
                new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                LOCATION_REQUEST
        ));
    }

    /** Отправляет JSON-строку в WebView на главном потоке. */
    void deliver(String json) {
        if (webView == null) {
            return;
        }
        final String script = "window.__onNativeData(" + JSONObject.quote(json) + ")";
        webView.post(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    void deliverError(String message) {
        try {
            deliver(new JSONObject().put("type", "error").put("message", message).toString());
        } catch (Exception e) {
            deliver("{\"type\":\"error\",\"message\":\"Ошибка данных\"}");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        try {
            deliver(new JSONObject()
                    .put("type", "permission")
                    .put("granted", granted)
                    .toString());
        } catch (Exception ignored) {
            // Сообщение некритично.
        }
        if (!granted) {
            Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
        webView.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.resumeTimers();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (bridge != null) {
            bridge.shutdown();
            bridge = null;
        }
        webView.destroy();
        webView = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
