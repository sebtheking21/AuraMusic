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
 private static final String AURA_URL="https://sebtheking21.github.io/AuraMusic/";
 private static final int NOTIFICATION_REQUEST=42, FILE_CHOOSER_REQUEST=43, PLAYLIST_FILE_REQUEST=44;
 private WebView webView; private ValueCallback<Uri[]> filePathCallback; private boolean recovering=false; private int crashCount=0; private boolean safeMode=false;
 @SuppressLint("SetJavaScriptEnabled") @Override protected void onCreate(Bundle state){super.onCreate(state);setupWindow();setupWebView();requestNotificationPermission();webView.loadUrl(AURA_URL);}
 private void setupWindow(){Window w=getWindow();w.setStatusBarColor(0xFF000000);w.setNavigationBarColor(0xFF000000);if(Build.VERSION.SDK_INT>=23)w.getDecorView().setSystemUiVisibility(0);if(Build.VERSION.SDK_INT>=30)w.setDecorFitsSystemWindows(true);}
 @SuppressLint("SetJavaScriptEnabled") private void setupWebView(){
  webView=new WebView(this);webView.setBackgroundColor(0xFF000000);if(Build.VERSION.SDK_INT>=26)webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);
  int id=getResources().getIdentifier("status_bar_height","dimen","android");int h=id>0?getResources().getDimensionPixelSize(id):dp(24);webView.setPadding(0,h,0,0);webView.setClipToPadding(false);setContentView(webView);
  webView.addJavascriptInterface(new NativeMediaBridge(),"AndroidAura");WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setBuiltInZoomControls(false);s.setDisplayZoomControls(false);s.setSupportZoom(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setCacheMode(WebSettings.LOAD_DEFAULT);CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
  webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(filePathCallback!=null)filePathCallback.onReceiveValue(null);filePathCallback=cb;try{Intent i=p.createIntent();i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,FILE_CHOOSER_REQUEST);return true;}catch(Exception e){filePathCallback=null;return false;}}});
  webView.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){super.onPageFinished(v,url);injectMobileUi(v,safeMode);}@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return false;}@Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail d){recoverFromRendererCrash(v,d);return true;}});
 }
 private void recoverFromRendererCrash(WebView dead,RenderProcessGoneDetail detail){if(recovering)return;recovering=true;crashCount++;safeMode=true;try{if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(dead!=null){ViewGroup p=dead.getParent() instanceof ViewGroup?(ViewGroup)dead.getParent():null;if(p!=null)p.removeView(dead);dead.stopLoading();dead.destroy();}webView=null;setupWebView();if(crashCount>=3){safeMode=true;Toast.makeText(this,"Aura switched to stability mode after a WebView crash",Toast.LENGTH_LONG).show();}webView.postDelayed(()->webView.loadUrl(AURA_URL+"?safe=1&n="+System.currentTimeMillis()),250);}finally{recovering=false;}}
 private class NativeMediaBridge{
  @JavascriptInterface public void play(String u,String t,String a,String art){sendPlayback(PlaybackService.ACTION_PLAY,u,t,a,art);}
  @JavascriptInterface public void pause(){sendPlayback(PlaybackService.ACTION_PAUSE,null,null,null,null);}
  @JavascriptInterface public void playPause(){sendPlayback(PlaybackService.ACTION_PLAY_PAUSE,null,null,null,null);}
  @JavascriptInterface public void next(){sendPlayback(PlaybackService.ACTION_NEXT,null,null,null,null);}
  @JavascriptInterface public void previous(){sendPlayback(PlaybackService.ACTION_PREVIOUS,null,null,null,null);}
  @JavascriptInterface public void pickPlaylistFile(){runOnUiThread(()->{try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/json","text/plain"});startActivityForResult(i,PLAYLIST_FILE_REQUEST);}catch(Exception e){Toast.makeText(MainActivity.this,"Unable to open playlist picker",Toast.LENGTH_SHORT).show();}});}
 }
 private void sendPlayback(String action,String url,String title,String artist,String art){Intent i=new Intent(this,PlaybackService.class);i.setAction(action);if(url!=null)i.putExtra(PlaybackService.EXTRA_URL,url);if(title!=null)i.putExtra(PlaybackService.EXTRA_TITLE,title);if(artist!=null)i.putExtra(PlaybackService.EXTRA_ARTIST,artist);if(art!=null)i.putExtra(PlaybackService.EXTRA_ART,art);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==FILE_CHOOSER_REQUEST){if(filePathCallback==null)return;Uri[] r=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null)r=new Uri[]{data.getData()};filePathCallback.onReceiveValue(r);filePathCallback=null;return;}if(req==PLAYLIST_FILE_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null&&webView!=null)try{String json=readUriText(data.getData());String b64=Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('aura-native-playlist-file',{detail:atob('"+b64+"')}));",null);}catch(Exception e){Toast.makeText(this,"Could not read playlist file",Toast.LENGTH_SHORT).show();}}
 private String readUriText(Uri uri)throws IOException{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("No stream");byte[] b=new byte[8192];int n;long total=0;while((n=in.read(b))!=-1){total+=n;if(total>10*1024*1024)throw new IOException("Playlist file too large");out.write(b,0,n);}return out.toString(StandardCharsets.UTF_8.name());}}
 private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQUEST);}
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
 private String asset(String name){try(InputStream in=getAssets().open(name)){byte[] b=new byte[in.available()];int n=in.read(b);return new String(b,0,Math.max(0,n),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
 private void addScript(StringBuilder x,String id,String code){String b=Base64.encodeToString(code.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);x.append("var e=document.getElementById('").append(id).append("');if(e)e.remove();var s=document.createElement('script');s.id='").append(id).append("';s.textContent=atob('").append(b).append("');document.body.appendChild(s);");}
 private void addStyle(StringBuilder x,String id,String code){String b=Base64.encodeToString(code.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);x.append("var e=document.getElementById('").append(id).append("');if(e)e.remove();var s=document.createElement('style');s.id='").append(id).append("';s.textContent=atob('").append(b).append("');document.head.appendChild(s);");}
 private void injectMobileUi(WebView v,boolean safe){StringBuilder x=new StringBuilder("(function(){");addStyle(x,"aura-mobile-style",asset("mobile.css"));addScript(x,"aura-mobile-script",asset("mobile.js"));addStyle(x,"aura-player-fix-style",asset("mobile-player-fix.css"));addScript(x,"aura-player-fix-script",asset("mobile-player-fix.js"));addScript(x,"aura-native-playback-script",asset("native-playback-bridge.js"));if(!safe){addScript(x,"aura-playlist-transfer-script",asset("playlist-transfer.js"));addScript(x,"aura-spotify-browser-script",asset("spotify-browser.js"));addScript(x,"aura-youtube-import-fix",asset("youtube-import-fix.js"));}x.append("})();");v.evaluateJavascript(x.toString(),null);}
 @Override protected void onDestroy(){if(filePathCallback!=null){filePathCallback.onReceiveValue(null);filePathCallback=null;}if(webView!=null){ViewGroup p=webView.getParent() instanceof ViewGroup?(ViewGroup)webView.getParent():null;if(p!=null)p.removeView(webView);webView.stopLoading();webView.destroy();webView=null;}super.onDestroy();}
 @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}