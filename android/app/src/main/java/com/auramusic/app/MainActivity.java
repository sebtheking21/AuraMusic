package com.auramusic.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private static final String AURA_URL = "https://sebtheking21.github.io/AuraMusic/";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF090C15);
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

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectMobileUi(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        webView.loadUrl(AURA_URL);
    }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name)) {
            byte[] bytes = new byte[input.available()];
            int offset = 0;
            int read;
            while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                offset += read;
            }
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void injectMobileUi(WebView view) {
        String css = readAsset("mobile.css");
        String js = readAsset("mobile.js");
        if (css.isEmpty() && js.isEmpty()) return;

        String css64 = Base64.encodeToString(css.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String js64 = Base64.encodeToString(js.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String script = "(function(){"
                + "var s=document.createElement('style');"
                + "s.id='aura-mobile-style';"
                + "s.textContent=atob('" + css64 + "');"
                + "var old=document.getElementById('aura-mobile-style');"
                + "if(old) old.remove();"
                + "document.head.appendChild(s);"
                + "var j=document.createElement('script');"
                + "j.textContent=atob('" + js64 + "');"
                + "document.body.appendChild(j);"
                + "})();";
        view.evaluateJavascript(script, null);
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
