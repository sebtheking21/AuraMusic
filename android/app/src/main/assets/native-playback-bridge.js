(function(){
  'use strict';
  if (window.__auraNativePlaybackBridge) return;
  window.__auraNativePlaybackBridge = true;
  var nativeApi = window.AndroidAura;
  if (!nativeApi) return;

  function isNativeCandidate(url){
    if (!url) return false;
    var u = String(url).toLowerCase().split('?')[0].split('#')[0];
    if (u.indexOf('blob:') === 0 || u.indexOf('javascript:') === 0) return false;
    if (u.indexOf('youtube.com/watch') >= 0 || u.indexOf('youtu.be/') >= 0 || u.indexOf('youtube-nocookie.com') >= 0) return false;
    if (!(u.indexOf('http://') === 0 || u.indexOf('https://') === 0 || u.indexOf('file://') === 0 || u.indexOf('content://') === 0)) return false;
    // Only hand clearly audio media to Media3. This prevents the Android bridge from
    // swallowing normal WebView playback URLs that the website can play itself.
    return /\.(mp3|m4a|aac|wav|ogg|oga|opus|flac|webm|m3u8)$/i.test(u);
  }

  function text(sel){ var el=document.querySelector(sel); return el ? (el.textContent || '').trim() : ''; }
  function currentInfo(){
    var title=text('.player-title')||text('#player-title')||text('.now-playing-title')||text('.current-song-title')||'';
    var artist=text('.player-artist')||text('#player-artist')||text('.now-playing-artist')||text('.current-song-artist')||'';
    var art='';
    var img=document.querySelector('.player-artwork img, #player-artwork img, .player-artwork, #player-artwork, .now-playing-art img');
    if(img) art=img.currentSrc||img.src||'';
    return {title:title,artist:artist,art:art};
  }

  var lastSrc='';
  var internal=false;
  function sendPlay(media){
    if(internal)return;
    var src=media.currentSrc||media.src||'';
    if(!isNativeCandidate(src))return;
    if(src===lastSrc&&nativeApi.playPause){try{nativeApi.playPause();}catch(e){}return;}
    lastSrc=src;
    var info=currentInfo(media);
    try{nativeApi.play(src,info.title,info.artist,info.art);}catch(e){}
    try{internal=true;media.pause();}catch(e){}finally{internal=false;}
  }
  function sendPause(media){
    if(internal)return;
    var src=media.currentSrc||media.src||'';
    if(!isNativeCandidate(src))return;
    try{nativeApi.pause();}catch(e){}
  }

  var originalPlay=HTMLMediaElement.prototype.play;
  HTMLMediaElement.prototype.play=function(){
    sendPlay(this);
    var src=this.currentSrc||this.src||'';
    if(isNativeCandidate(src))return Promise.resolve();
    return originalPlay.apply(this,arguments);
  };
  var originalPause=HTMLMediaElement.prototype.pause;
  HTMLMediaElement.prototype.pause=function(){
    sendPause(this);
    var src=this.currentSrc||this.src||'';
    if(isNativeCandidate(src))return;
    return originalPause.apply(this,arguments);
  };
  function watch(media){
    if(!media||media.__auraNativeWatched)return;
    media.__auraNativeWatched=true;
    media.addEventListener('play',function(){sendPlay(media);});
    media.addEventListener('pause',function(){sendPause(media);});
    media.addEventListener('emptied',function(){lastSrc='';});
    media.addEventListener('loadedmetadata',function(){if(media.autoplay&&!media.paused)sendPlay(media);});
  }
  function scan(){document.querySelectorAll('audio,video').forEach(watch);}
  scan();
  new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});
})();
