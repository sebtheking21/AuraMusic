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
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String AURA_URL = "https://sebtheking21.github.io/AuraMusic/";
    private static final int NOTIFICATION_REQUEST = 42;
    private static final int FILE_CHOOSER_REQUEST = 43;
    private static final int PLAYLIST_FILE_REQUEST = 44;
    private boolean recoveringRenderer = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        setupWebView();
        requestNotificationPermission();
        startPlaybackService();
        webView.clearCache(true);
        webView.clearHistory();
        webView.loadUrl(AURA_URL + "?app=" + System.currentTimeMillis());
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF000000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
        webView.setPadding(0, h, 0, 0);
        webView.setClipToPadding(false);
        setContentView(webView);
        webView.addJavascriptInterface(new NativeMediaBridge(), "AndroidAura");
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
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(picker, FILE_CHOOSER_REQUEST);
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
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                recoverFromRendererCrash(view);
                return true;
            }
        });
    }

    private void recoverFromRendererCrash(WebView deadView) {
        if (recoveringRenderer) return;
        recoveringRenderer = true;
        try {
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            if (deadView != null) {
                ViewGroup parent = deadView.getParent() instanceof ViewGroup ? (ViewGroup) deadView.getParent() : null;
                if (parent != null) parent.removeView(deadView);
                deadView.stopLoading();
                deadView.destroy();
            }
            webView = null;
            setupWebView();
            webView.clearCache(true);
            webView.clearHistory();
            webView.loadUrl(AURA_URL + "?recovery=" + System.currentTimeMillis());
            Toast.makeText(this, "Aura recovered from a WebView error", Toast.LENGTH_SHORT).show();
        } finally { recoveringRenderer = false; }
    }

    private class NativeMediaBridge {
        @JavascriptInterface public void play(String url, String title, String artist, String art) { sendPlayback(PlaybackService.ACTION_PLAY, url, title, artist, art); }
        @JavascriptInterface public void pause() { sendPlayback(PlaybackService.ACTION_PAUSE, null, null, null, null); }
        @JavascriptInterface public void playPause() { sendPlayback(PlaybackService.ACTION_PLAY_PAUSE, null, null, null, null); }
        @JavascriptInterface public void next() { sendPlayback(PlaybackService.ACTION_NEXT, null, null, null, null); }
        @JavascriptInterface public void previous() { sendPlayback(PlaybackService.ACTION_PREVIOUS, null, null, null, null); }
        @JavascriptInterface public void pickPlaylistFile() {
            runOnUiThread(() -> {
                try {
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType("application/json");
                    picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain"});
                    startActivityForResult(picker, PLAYLIST_FILE_REQUEST);
                } catch (Exception e) { Toast.makeText(MainActivity.this, "Unable to open playlist picker", Toast.LENGTH_SHORT).show(); }
            });
        }
    }

    private void sendPlayback(String action, String url, String title, String artist, String art) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        if (url != null) intent.putExtra(PlaybackService.EXTRA_URL, url);
        if (title != null) intent.putExtra(PlaybackService.EXTRA_TITLE, title);
        if (artist != null) intent.putExtra(PlaybackService.EXTRA_ARTIST, artist);
        if (art != null) intent.putExtra(PlaybackService.EXTRA_ART, art);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            return;
        }
        if (requestCode == PLAYLIST_FILE_REQUEST) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null || webView == null) return;
            try {
                String json = readUriText(data.getData());
                String b64 = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                String js = "window.dispatchEvent(new CustomEvent('aura-native-playlist-file',{detail:atob('" + b64 + "')}));";
                webView.evaluateJavascript(js, null);
            } catch (Exception e) { Toast.makeText(this, "Could not read playlist file", Toast.LENGTH_SHORT).show(); }
        }
    }

    private String readUriText(Uri uri) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("No stream");
            byte[] buffer = new byte[8192]; int n; long total = 0;
            while ((n = in.read(buffer)) != -1) { total += n; if (total > 10 * 1024 * 1024) throw new IOException("Playlist file is too large"); out.write(buffer, 0, n); }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void startPlaybackService() { Intent intent = new Intent(this, PlaybackService.class); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent); }

    private String readAsset(String name) {
        try (InputStream input = getAssets().open(name)) {
            byte[] bytes = new byte[input.available()]; int offset = 0, read;
            while (offset < bytes.length && (read = input.read(bytes, offset, bytes.length - offset)) > 0) offset += read;
            return new String(bytes, 0, offset, StandardCharsets.UTF_8);
        } catch (IOException e) { return ""; }
    }

    private void injectMobileUi(WebView view) {
        String css = readAsset("mobile.css"), js = readAsset("mobile.js"), transfer = readAsset("playlist-transfer.js"), fixCss = readAsset("mobile-player-fix.css"), fixJs = readAsset("mobile-player-fix.js"), nativeJs = readAsset("native-playback-bridge.js"), spotifyJs = readAsset("spotify-browser.js"), ytFixJs = readAsset("youtube-import-fix.js");
        String css64 = Base64.encodeToString(css.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), js64 = Base64.encodeToString(js.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), transfer64 = Base64.encodeToString(transfer.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), fixCss64 = Base64.encodeToString(fixCss.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), fixJs64 = Base64.encodeToString(fixJs.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), native64 = Base64.encodeToString(nativeJs.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), spotify64 = Base64.encodeToString(spotifyJs.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP), ytFix64 = Base64.encodeToString(ytFixJs.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String script = "(function(){" +
                "var old=document.getElementById('aura-mobile-style');if(old)old.remove();var s=document.createElement('style');s.id='aura-mobile-style';s.textContent=atob('"+css64+"');document.head.appendChild(s);"+
                "var oldj=document.getElementById('aura-mobile-script');if(oldj)oldj.remove();var j=document.createElement('script');j.id='aura-mobile-script';j.textContent=atob('"+js64+"');document.body.appendChild(j);"+
                "var oldt=document.getElementById('aura-playlist-transfer-script');if(oldt)oldt.remove();var t=document.createElement('script');t.id='aura-playlist-transfer-script';t.textContent=atob('"+transfer64+"');document.body.appendChild(t);"+
                "var oldfc=document.getElementById('aura-player-fix-style');if(oldfc)oldfc.remove();var fc=document.createElement('style');fc.id='aura-player-fix-style';fc.textContent=atob('"+fixCss64+"');document.head.appendChild(fc);"+
                "var oldfj=document.getElementById('aura-player-fix-script');if(oldfj)oldfj.remove();var fj=document.createElement('script');fj.id='aura-player-fix-script';fj.textContent=atob('"+fixJs64+"');document.body.appendChild(fj);"+
                "var oldnp=document.getElementById('aura-native-playback-script');if(oldnp)oldnp.remove();var np=document.createElement('script');np.id='aura-native-playback-script';np.textContent=atob('"+native64+"');document.body.appendChild(np);"+
                "var olds=document.getElementById('aura-spotify-browser-script');if(olds)olds.remove();var sp=document.createElement('script');sp.id='aura-spotify-browser-script';sp.textContent=atob('"+spotify64+"');document.body.appendChild(sp);"+
                "var oldyt=document.getElementById('aura-youtube-import-fix');if(oldyt)oldyt.remove();var y=document.createElement('script');y.id='aura-youtube-import-fix';y.textContent=atob('"+ytFix64+"');document.body.appendChild(y);})();";
        view.evaluateJavascript(script, null);
    }

    @Override protected void onDestroy() {
        if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
        if (webView != null) { ViewGroup parent = webView.getParent() instanceof ViewGroup ? (ViewGroup) webView.getParent() : null; if (parent != null) parent.removeView(webView); webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
}
