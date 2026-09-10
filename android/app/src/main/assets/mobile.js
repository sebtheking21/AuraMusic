(function(){
  'use strict';

  var PLAYLIST_KEY='aura_playlists_v3';
  var OFFLINE_DB='aura_offline_v1';
  var navItems=[];
  var libraryOpen=false;
  var offlineAudio=null;
  var offlineCurrent=null;

  function isMobile(){return window.matchMedia('(max-width:900px)').matches;}
  function norm(s){return(s||'').toString().replace(/\s+/g,' ').trim();}
  function esc(s){return String(s||'').replace(/[&<>'"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','\"':'&quot;'}[c];});}
  function attr(s){return esc(s).replace(/javascript:/gi,'');}
  function icon(label){var l=label.toLowerCase();if(l==='home')return'fa-house';if(l==='new')return'fa-sparkles';if(l==='radio')return'fa-radio';if(l==='library')return'fa-music';if(l==='search')return'fa-magnifying-glass';return'fa-music';}

  function readPlaylists(){try{return JSON.parse(localStorage.getItem(PLAYLIST_KEY)||localStorage.getItem('aura_playlists_v2')||'[]');}catch(e){return[];}}
  function savePlaylists(a){localStorage.setItem(PLAYLIST_KEY,JSON.stringify(a));}
  function toast(msg){var o=document.querySelector('.aura-toast');if(o)o.remove();var t=document.createElement('div');t.className='aura-toast';t.textContent=msg;document.body.appendChild(t);setTimeout(function(){if(t.parentNode)t.remove();},2600);}

  function getPlayer(){return document.querySelector('.player-bar,.bottom-player,#player-bar,#bottom-player,.player-container');}
  function captureSong(){
    var p=getPlayer(),title='Current song',artist='Unknown artist',thumb='';
    if(p){
      var te=p.querySelector('.player-title,.track-title,.song-title,[class*="title"]'),ae=p.querySelector('.player-artist,.track-artist,.song-artist,[class*="artist"]'),im=p.querySelector('img');
      if(te)title=norm(te.textContent)||title;if(ae)artist=norm(ae.textContent)||artist;if(im)thumb=im.currentSrc||im.src||'';
    }
    return{title:title,artist:artist,thumb:thumb,added:Date.now()};
  }
  function choosePlaylist(){
    var a=readPlaylists();if(!a.length){var n=prompt('Name your first playlist');if(!n)return;a=[{id:Date.now()+'',name:norm(n),songs:[]}];savePlaylists(a);}
    var choices=a.map(function(p,i){return(i+1)+'. '+p.name+' ('+p.songs.length+')';}).join('\n');var ans=prompt('Add to which playlist?\n\n'+choices+'\n\nType the playlist number');var i=parseInt(ans,10)-1;if(isNaN(i)||!a[i])return;
    a[i].songs.push(captureSong());savePlaylists(a);renderLibrary();toast('Added to '+a[i].name);
  }

  /* Offline storage uses IndexedDB and only caches a directly accessible audio URL.
     It intentionally does not extract/download audio from YouTube pages. */
  function db(){return new Promise(function(resolve,reject){var r=indexedDB.open(OFFLINE_DB,1);r.onupgradeneeded=function(){r.result.createObjectStore('tracks',{keyPath:'id'});};r.onsuccess=function(){resolve(r.result);};r.onerror=function(){reject(r.error);};});}
  function offlineAll(){return db().then(function(d){return new Promise(function(res,rej){var q=d.transaction('tracks','readonly').objectStore('tracks').getAll();q.onsuccess=function(){res(q.result||[]);};q.onerror=function(){rej(q.error);};});}).catch(function(){return[];});}
  function findDirectAudio(){var p=getPlayer(),a=p&&p.querySelector('audio');if(!a)a=document.querySelector('audio');if(!a)return null;return a.currentSrc||a.src||null;}
  async function downloadCurrentOffline(){
    var src=findDirectAudio();var song=captureSong();
    if(!src||/youtube\.com|youtu\.be/i.test(src)){toast('Offline downloads need a direct audio/local upload source.');return;}
    if(/^blob:|^data:/i.test(src)){toast('This track cannot be saved from this player.');return;}
    toast('Saving for offline…');
    try{var res=await fetch(src);if(!res.ok)throw new Error('download failed');var blob=await res.blob();var d=await db();await new Promise(function(resolve,reject){var q=d.transaction('tracks','readwrite').objectStore('tracks').put({id:src,url:src,title:song.title,artist:song.artist,thumb:song.thumb,blob:blob,added:Date.now()});q.onsuccess=resolve;q.onerror=function(){reject(q.error);};});toast('Saved for offline');renderLibrary();}
    catch(e){toast('Could not save this track offline.');}
  }
  function deleteOffline(id){db().then(function(d){d.transaction('tracks','readwrite').objectStore('tracks').delete(id);}).then(renderLibrary);}
  async function playOffline(id){
    var d=await db();var rec=await new Promise(function(res,rej){var q=d.transaction('tracks','readonly').objectStore('tracks').get(id);q.onsuccess=function(){res(q.result);};q.onerror=function(){rej(q.error);};});
    if(!rec)return;if(offlineAudio){offlineAudio.pause();offlineAudio.src='';}
    offlineAudio=new Audio(URL.createObjectURL(rec.blob));offlineAudio.preload='auto';offlineCurrent=rec;await offlineAudio.play();
    if('mediaSession'in navigator){navigator.mediaSession.metadata=new MediaMetadata({title:rec.title,artist:rec.artist||'Unknown artist',album:'Aura Music · Offline',artwork:rec.thumb?[{src:rec.thumb,sizes:'512x512'}]:[]});}
    toast('Playing offline');
  }
  async function renderOffline(){
    var list=await offlineAll(),box=document.querySelector('.aura-offline-list');if(!box)return;
    if(!list.length){box.innerHTML='<div class="aura-empty">No offline downloads yet.<br>Save a direct audio/local track with the download button.</div>';return;}
    box.innerHTML=list.map(function(x){return'<div class="aura-offline-row"><img class="cover" src="'+attr(x.thumb||'')+'" onerror="this.style.visibility=\'hidden\'" alt=""><div class="meta"><div class="name">'+esc(x.title)+'</div><div class="sub">'+esc(x.artist)+'</div></div><button class="aura-offline-play" data-id="'+attr(x.id)+'"><i class="fa-solid fa-play"></i></button><button class="aura-offline-delete" data-id="'+attr(x.id)+'"><i class="fa-solid fa-trash"></i></button></div>';}).join('');
    box.querySelectorAll('.aura-offline-play').forEach(function(b){b.onclick=function(){playOffline(b.dataset.id);};});
    box.querySelectorAll('.aura-offline-delete').forEach(function(b){b.onclick=function(){deleteOffline(b.dataset.id);};});
  }

  function addPlayerButtons(){
    var p=getPlayer();if(!p)return;
    if(!p.querySelector('.mobile-player-add')){var b=document.createElement('button');b.className='mobile-player-add';b.title='Add to playlist';b.innerHTML='<i class="fa-solid fa-plus"></i>';b.onclick=function(e){e.preventDefault();e.stopPropagation();choosePlaylist();};p.appendChild(b);}
    if(!p.querySelector('.mobile-player-download')){var d=document.createElement('button');d.className='mobile-player-download';d.title='Save offline';d.innerHTML='<i class="fa-solid fa-arrow-down"></i>';d.onclick=function(e){e.preventDefault();e.stopPropagation();downloadCurrentOffline();};p.appendChild(d);}
  }

  function songMarkup(s,pl){return'<div class="aura-library-item"><img class="cover" src="'+attr(s.thumb||'')+'" onerror="this.style.visibility=\'hidden\'" alt=""><div class="meta"><div class="name">'+esc(s.title)+'</div><div class="sub">'+esc(s.artist)+' · '+esc(pl)+'</div></div></div>';}
  async function renderLibrary(){
    var sheet=document.querySelector('.aura-library-sheet');if(!sheet)return;var a=readPlaylists(),all=[];a.forEach(function(p){p.songs.forEach(function(s){all.push({playlist:p.name,song:s});});});var recent=all.slice().sort(function(x,y){return(y.song.added||0)-(x.song.added||0);}).slice(0,8);
    sheet.innerHTML='<div class="aura-library-head"><div class="aura-library-title">Your Library</div><button class="aura-library-close"><i class="fa-solid fa-xmark"></i></button></div>'+
      '<div class="aura-library-section"><h3>Playlists</h3><button class="aura-new-playlist"><i class="fa-solid fa-plus"></i>&nbsp; New Playlist</button></div>'+
      '<div class="aura-library-section">'+(a.length?a.map(function(p){var c=p.songs[0]&&p.songs[0].thumb;return'<div class="aura-playlist-card" data-playlist="'+attr(p.id)+'">'+(c?'<img class="aura-playlist-icon" src="'+attr(c)+'" alt="">':'<div class="aura-playlist-icon"><i class="fa-solid fa-music"></i></div>')+'<div class="meta"><div class="name">'+esc(p.name)+'</div><div class="sub">'+p.songs.length+' song'+(p.songs.length===1?'':'s')+'</div></div><i class="fa-solid fa-chevron-right" style="color:#8e8e93"></i></div>';}).join(''):'<div class="aura-empty">No playlists yet.</div>')+'</div>'+
      '<div class="aura-library-section"><h3>Recently Added</h3>'+(recent.length?recent.map(function(x){return songMarkup(x.song,x.playlist);}).join(''):'<div class="aura-empty">Songs you add to playlists appear here.</div>')+'</div>'+
      '<div class="aura-library-section"><h3>Offline Downloads</h3><div class="aura-offline-list"></div></div>';
    sheet.querySelector('.aura-library-close').onclick=closeLibrary;
    sheet.querySelector('.aura-new-playlist').onclick=function(){var n=prompt('Playlist name');if(!n)return;var x=readPlaylists();x.push({id:Date.now()+'',name:norm(n),songs:[]});savePlaylists(x);renderLibrary();toast('Playlist created');};
    sheet.querySelectorAll('.aura-playlist-card').forEach(function(c){c.onclick=function(){var x=readPlaylists().find(function(p){return p.id===c.dataset.playlist;});if(x)showPlaylist(x);};});
    renderOffline();
  }
  function showPlaylist(p){var s=document.querySelector('.aura-library-sheet');if(!s)return;s.innerHTML='<div class="aura-library-head"><div class="aura-library-title">'+esc(p.name)+'</div><button class="aura-library-close"><i class="fa-solid fa-arrow-left"></i></button></div>'+(p.songs.length?p.songs.slice().reverse().map(function(x){return songMarkup(x,p.name);}).join(''):'<div class="aura-empty">This playlist is empty.</div>');s.querySelector('.aura-library-close').onclick=renderLibrary;}

  function openLibrary(){var s=document.querySelector('.aura-library-sheet');if(!s)return;libraryOpen=true;s.classList.add('open');renderLibrary();updateActive();}
  function closeLibrary(){var s=document.querySelector('.aura-library-sheet');if(s)s.classList.remove('open');libraryOpen=false;updateActive();}
  function setupLibrary(){if(!document.querySelector('.aura-library-sheet')){var s=document.createElement('section');s.className='aura-library-sheet';document.body.appendChild(s);}}

  function setupMobileNav(){
    if(!isMobile()||document.querySelector('.mobile-bottom-nav'))return;
    var originals=[].slice.call(document.querySelectorAll('.sidebar .nav-item'));
    function find(words){return originals.find(function(e){var t=norm(e.textContent).toLowerCase();return words.some(function(w){return t.includes(w);});});}
    var home=find(['home']),news=find(['new','discover']),radio=find(['radio']),library=find(['library','playlist']),search=find(['search']);
    var defs=[{label:'Home',original:home},{label:'New',original:news},{label:'Radio',original:radio},{label:'Library',original:library,custom:true},{label:'Search',original:search}];
    var nav=document.createElement('nav');nav.className='mobile-bottom-nav';
    defs.forEach(function(d){var b=document.createElement('button');b.type='button';b.innerHTML='<i class="fa-solid '+icon(d.label)+'"></i><span>'+d.label+'</span>';b.onclick=function(){if(d.custom){openLibrary();return;}if(d.original){d.original.click();closeLibrary();updateActive();}};nav.appendChild(b);});
    document.body.appendChild(nav);updateActive();
  }
  function updateActive(){var n=document.querySelector('.mobile-bottom-nav');if(!n)return;var bs=[].slice.call(n.querySelectorAll('button'));bs.forEach(function(b){b.classList.remove('active');});if(libraryOpen){var l=bs.find(function(b){return norm(b.textContent)==='Library';});if(l)l.classList.add('active');return;}var originals=[].slice.call(document.querySelectorAll('.sidebar .nav-item'));var active=originals.find(function(e){return e.classList.contains('active');});if(active){var txt=norm(active.textContent).toLowerCase();var b=bs.find(function(x){return txt.includes(norm(x.textContent).toLowerCase());});if(b)b.classList.add('active');}else if(bs[0])bs[0].classList.add('active');}

  function setupMediaSession(){
    if(!('mediaSession'in navigator))return;
    function button(terms){return[].slice.call(document.querySelectorAll('button,[role="button"]')).find(function(e){var t=(e.getAttribute('aria-label')||e.title||e.textContent||'').toLowerCase();return terms.some(function(x){return t.includes(x);});});}
    try{navigator.mediaSession.setActionHandler('play',function(){var b=button(['play','resume']);if(b)b.click();});navigator.mediaSession.setActionHandler('pause',function(){var b=button(['pause','stop']);if(b)b.click();});navigator.mediaSession.setActionHandler('nexttrack',function(){var b=button(['next','skip']);if(b)b.click();});navigator.mediaSession.setActionHandler('previoustrack',function(){var b=button(['previous','back']);if(b)b.click();});}catch(e){}
    updateMediaMetadata();
  }
  function updateMediaMetadata(){if(!('mediaSession'in navigator))return;var p=getPlayer();if(!p)return;var t=p.querySelector('.player-title,.track-title,.song-title,[class*="title"]'),a=p.querySelector('.player-artist,.track-artist,.song-artist,[class*="artist"]'),i=p.querySelector('img'),title=norm(t&&t.textContent),artist=norm(a&&a.textContent);if(title)navigator.mediaSession.metadata=new MediaMetadata({title:title,artist:artist||'Unknown artist',album:'Aura Music',artwork:i&&i.src?[{src:i.src,sizes:'512x512'}]:[]});}

  function init(){if(!isMobile())return;setupLibrary();setupMobileNav();addPlayerButtons();setupMediaSession();updateActive();setTimeout(addPlayerButtons,700);setTimeout(setupMobileNav,1100);}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
  setInterval(function(){if(isMobile()){setupMobileNav();addPlayerButtons();updateActive();updateMediaMetadata();}},1800);
  document.addEventListener('visibilitychange',function(){if(!document.hidden){setTimeout(init,250);}});
})();
