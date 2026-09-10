package com.auramusic.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String AURA_URL = "https://sebtheking21.github.io/AuraMusic/";
    private static final int NOTIFICATION_REQUEST = 42;
    private static final int FILE_CHOOSER_REQUEST = 43;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        int statusBarId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int statusBarHeight = statusBarId > 0 ? getResources().getDimensionPixelSize(statusBarId) : dp(24);
        webView.setPadding(0, statusBarHeight, 0, 0);
        webView.setClipToPadding(false);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMobileUi(view);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
        });

        requestNotificationPermission();
        startPlaybackService();
        webView.loadUrl(AURA_URL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void startPlaybackService() {
        Intent intent = new Intent(this, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name)) {
            byte[] bytes = new byte[input.available()];
            int offset = 0, read;
            while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) > 0) offset += read;
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException e) { return ""; }
    }

    private void injectMobileUi(WebView view) {
        String css = readAsset("mobile.css"), js = readAsset("mobile.js"), transfer = readAsset("playlist-transfer.js");
        if (css.isEmpty() && js.isEmpty() && transfer.isEmpty()) return;
        String css64 = Base64.encodeToString(css.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String js64 = Base64.encodeToString(js.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String transfer64 = Base64.encodeToString(transfer.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String script = "(function(){"
                + "var old=document.getElementById('aura-mobile-style');if(old)old.remove();"
                + "var s=document.createElement('style');s.id='aura-mobile-style';s.textContent=atob('"+css64+"');document.head.appendChild(s);"
                + "var oldj=document.getElementById('aura-mobile-script');if(oldj)oldj.remove();"
                + "var j=document.createElement('script');j.id='aura-mobile-script';j.textContent=atob('"+js64+"');document.body.appendChild(j);"
                + "var oldt=document.getElementById('aura-playlist-transfer-script');if(oldt)oldt.remove();"
                + "var t=document.createElement('script');t.id='aura-playlist-transfer-script';t.textContent=atob('"+transfer64+"');document.body.appendChild(t);"
                + "})();";
        view.evaluateJavascript(script, null);
    }

    @Override protected void onDestroy() {
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        if (webView != null) { webView.onPause(); webView.destroy(); webView = null; }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
