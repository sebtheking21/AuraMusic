(function(){
  'use strict';

  var STORAGE_KEY='aura_playlists_v2';
  var navItems=[];
  var libraryOpen=false;

  function isMobile(){ return window.matchMedia('(max-width: 900px)').matches; }
  function norm(s){ return (s||'').toString().replace(/\s+/g,' ').trim(); }
  function iconFor(label){
    var l=label.toLowerCase();
    if(l.includes('home')) return 'fa-house';
    if(l.includes('search')) return 'fa-magnifying-glass';
    if(l.includes('library')||l.includes('playlist')) return 'fa-book-open';
    if(l.includes('setting')) return 'fa-gear';
    if(l.includes('liked')||l.includes('favorite')) return 'fa-heart';
    return 'fa-music';
  }

  function readPlaylists(){
    try { return JSON.parse(localStorage.getItem(STORAGE_KEY)||'[]'); }
    catch(e){ return []; }
  }
  function savePlaylists(list){ localStorage.setItem(STORAGE_KEY,JSON.stringify(list)); }

  function toast(msg){
    var old=document.querySelector('.aura-toast'); if(old) old.remove();
    var t=document.createElement('div'); t.className='aura-toast'; t.textContent=msg;
    document.body.appendChild(t); setTimeout(function(){t.remove();},2200);
  }

  function getPlayer(){
    return document.querySelector('.player-bar,.bottom-player,#player-bar,#bottom-player,.player-container');
  }

  function captureSong(){
    var p=getPlayer();
    var title='Current song', artist='Unknown artist', thumb='';
    if(p){
      var titleEl=p.querySelector('.player-title,.track-title,.song-title,[class*="title"]');
      var artistEl=p.querySelector('.player-artist,.track-artist,.song-artist,[class*="artist"]');
      var img=p.querySelector('img');
      if(titleEl) title=norm(titleEl.textContent)||title;
      if(artistEl) artist=norm(artistEl.textContent)||artist;
      if(img) thumb=img.currentSrc||img.src||'';
      if(title==='Current song'){
        var txt=norm(p.textContent).split(' ');
        if(txt.length) title=txt.slice(0,Math.min(7,txt.length)).join(' ');
      }
    }
    return {title:title,artist:artist,thumb:thumb,added:Date.now()};
  }

  function choosePlaylist(){
    var list=readPlaylists();
    if(!list.length){
      var name=window.prompt('Name your first playlist');
      if(!name) return;
      list=[{id:Date.now().toString(),name:norm(name),songs:[]}];
      savePlaylists(list);
    }
    var choices=list.map(function(p,i){ return (i+1)+'. '+p.name+' ('+p.songs.length+')'; }).join('\n');
    var answer=window.prompt('Add to which playlist?\n\n'+choices+'\n\nType the playlist number');
    var idx=parseInt(answer,10)-1;
    if(isNaN(idx)||!list[idx]) return;
    var song=captureSong();
    list[idx].songs.push(song); savePlaylists(list);
    renderLibrary(); toast('Added to '+list[idx].name);
  }

  function addPlayerButton(){
    var p=getPlayer(); if(!p || p.querySelector('.mobile-player-add')) return;
    var b=document.createElement('button');
    b.className='mobile-player-add'; b.title='Add to playlist'; b.innerHTML='<i class="fa-solid fa-plus"></i>';
    b.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();choosePlaylist();});
    p.appendChild(b);
  }

  function renderLibrary(){
    var sheet=document.querySelector('.aura-library-sheet'); if(!sheet) return;
    var list=readPlaylists();
    var all=[]; list.forEach(function(p){p.songs.forEach(function(s){all.push({playlist:p.name,song:s});});});
    var recent=all.slice().sort(function(a,b){return (b.song.added||0)-(a.song.added||0);}).slice(0,8);
    var html='';
    html+='<div class="aura-library-head"><div class="aura-library-title">Your Library</div><button class="aura-library-close" aria-label="Close library"><i class="fa-solid fa-xmark"></i></button></div>';
    html+='<div class="aura-library-section"><h3>Playlists</h3><button class="aura-new-playlist"><i class="fa-solid fa-plus"></i>&nbsp; New playlist</button></div>';
    html+='<div class="aura-library-section">';
    if(!list.length) html+='<div class="aura-empty">No playlists yet. Make one and start saving songs.</div>';
    list.forEach(function(p){
      var cover=(p.songs[0]&&p.songs[0].thumb)||'';
      html+='<div class="aura-playlist-card" data-playlist="'+escapeAttr(p.id)+'">';
      if(cover) html+='<img class="aura-playlist-icon cover" src="'+escapeAttr(cover)+'" alt="">'; else html+='<div class="aura-playlist-icon"><i class="fa-solid fa-music"></i></div>';
      html+='<div class="meta"><div class="name">'+escapeHtml(p.name)+'</div><div class="sub">'+p.songs.length+' song'+(p.songs.length===1?'':'s')+'</div></div><i class="fa-solid fa-chevron-right" style="color:#777"></i></div>';
    });
    html+='</div>';
    html+='<div class="aura-library-section"><h3>Recently saved</h3>';
    if(!recent.length) html+='<div class="aura-empty">Songs you add to playlists will appear here.</div>';
    recent.forEach(function(x){ html+=songMarkup(x.song,x.playlist); });
    html+='</div>';
    sheet.innerHTML=html;
    sheet.querySelector('.aura-library-close').onclick=closeLibrary;
    sheet.querySelector('.aura-new-playlist').onclick=function(){
      var name=window.prompt('Playlist name'); if(!name) return;
      var arr=readPlaylists(); arr.push({id:Date.now().toString(),name:norm(name),songs:[]}); savePlaylists(arr); renderLibrary(); toast('Playlist created');
    };
    Array.prototype.forEach.call(sheet.querySelectorAll('.aura-playlist-card'),function(card){
      card.onclick=function(){
        var id=card.getAttribute('data-playlist'), arr=readPlaylists(), p=arr.find(function(x){return x.id===id;});
        if(!p) return;
        showPlaylist(p);
      };
    });
  }

  function songMarkup(s,playlist){
    return '<div class="aura-library-item"><img class="cover" src="'+escapeAttr(s.thumb||'')+'" onerror="this.style.visibility=\'hidden\'" alt=""><div class="meta"><div class="name">'+escapeHtml(s.title)+'</div><div class="sub">'+escapeHtml(s.artist)+' · '+escapeHtml(playlist)+'</div></div></div>';
  }

  function showPlaylist(p){
    var sheet=document.querySelector('.aura-library-sheet'); if(!sheet) return;
    var html='<div class="aura-library-head"><div class="aura-library-title">'+escapeHtml(p.name)+'</div><button class="aura-library-close"><i class="fa-solid fa-arrow-left"></i></button></div>';
    if(!p.songs.length) html+='<div class="aura-empty">This playlist is empty.</div>';
    p.songs.slice().reverse().forEach(function(s){html+=songMarkup(s,p.name);});
    sheet.innerHTML=html; sheet.querySelector('.aura-library-close').onclick=renderLibrary;
  }

  function escapeHtml(s){return String(s||'').replace(/[&<>'"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','\"':'&quot;'}[c];});}
  function escapeAttr(s){return escapeHtml(s).replace(/javascript:/gi,'');}

  function openLibrary(){
    var sheet=document.querySelector('.aura-library-sheet'); if(!sheet) return;
    libraryOpen=true; sheet.classList.add('open'); renderLibrary();
    var nav=document.querySelector('.mobile-bottom-nav'); if(nav) nav.setAttribute('aria-hidden','true');
  }
  function closeLibrary(){
    var sheet=document.querySelector('.aura-library-sheet'); if(sheet) sheet.classList.remove('open'); libraryOpen=false;
  }

  function setupLibrary(){
    if(document.querySelector('.aura-library-sheet')) return;
    var sheet=document.createElement('section'); sheet.className='aura-library-sheet'; sheet.setAttribute('aria-label','Your Library'); document.body.appendChild(sheet);
  }

  function setupMobileNav(){
    if(!isMobile() || document.querySelector('.mobile-bottom-nav')) return;
    var originals=[].slice.call(document.querySelectorAll('.sidebar .nav-item'));
    navItems=[];
    var find=function(words){return originals.find(function(el){var t=norm(el.textContent).toLowerCase(); return words.some(function(w){return t.includes(w);});});};
    var home=find(['home']), search=find(['search']), library=find(['library','playlist']), settings=find(['settings','setting']);
    var selected=[home,search].filter(Boolean);
    if(settings) selected.push(settings);
    navItems=selected.slice(0,2);
    var nav=document.createElement('nav'); nav.className='mobile-bottom-nav';
    var defs=[{label:'Home',original:home},{label:'Search',original:search},{label:'Library',original:library,custom:true},{label:'Settings',original:settings}];
    defs.forEach(function(d){
      var b=document.createElement('button'); b.type='button'; b.innerHTML='<i class="fa-solid '+iconFor(d.label)+'"></i><span>'+d.label+'</span>';
      b.addEventListener('click',function(){
        if(d.custom || d.label==='Library'){openLibrary();return;}
        if(d.original){d.original.click();closeLibrary();updateActive();}
      });
      nav.appendChild(b);
    });
    document.body.appendChild(nav);
    updateActive();
  }

  function updateActive(){
    var nav=document.querySelector('.mobile-bottom-nav'); if(!nav) return;
    var labels=[].slice.call(nav.querySelectorAll('button'));
    labels.forEach(function(b){b.classList.remove('active');});
    if(libraryOpen){var i=labels.findIndex(function(b){return norm(b.textContent)==='Library';}); if(i>=0) labels[i].classList.add('active'); return;}
    var active=navItems.findIndex(function(el){return el&&el.classList.contains('active');});
    if(active>=0 && labels[active]) labels[active].classList.add('active'); else if(labels[0]) labels[0].classList.add('active');
  }

  function setupMediaSession(){
    if(!('mediaSession' in navigator)) return;
    var getButton=function(terms){
      var els=[].slice.call(document.querySelectorAll('button,[role="button"]'));
      return els.find(function(el){var t=(el.getAttribute('aria-label')||el.title||el.textContent||'').toLowerCase(); return terms.some(function(x){return t.includes(x);});});
    };
    try{
      navigator.mediaSession.setActionHandler('play',function(){var b=getButton(['play','resume']); if(b)b.click();});
      navigator.mediaSession.setActionHandler('pause',function(){var b=getButton(['pause','stop']); if(b)b.click();});
      navigator.mediaSession.setActionHandler('nexttrack',function(){var b=getButton(['next','skip']); if(b)b.click();});
      navigator.mediaSession.setActionHandler('previoustrack',function(){var b=getButton(['previous','back']); if(b)b.click();});
    }catch(e){}
    updateMediaMetadata(); setInterval(updateMediaMetadata,2000);
  }

  function updateMediaMetadata(){
    if(!('mediaSession' in navigator)) return;
    var p=getPlayer(); if(!p) return;
    var t=p.querySelector('.player-title,.track-title,.song-title,[class*="title"]');
    var a=p.querySelector('.player-artist,.track-artist,.song-artist,[class*="artist"]');
    var img=p.querySelector('img');
    var title=norm(t&&t.textContent)||''; var artist=norm(a&&a.textContent)||'';
    if(title){
      navigator.mediaSession.metadata=new MediaMetadata({title:title,artist:artist||'Unknown artist',album:'Aura Music',artwork:img&&img.src?[{src:img.src,sizes:'512x512',type:'image/jpeg'}]:[]});
    }
  }

  function init(){
    if(!isMobile()) return;
    setupLibrary();
    setupMobileNav();
    addPlayerButton();
    setupMediaSession();
    updateActive();
    setTimeout(addPlayerButton,800);
    setTimeout(setupMobileNav,1200);
  }

  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',init); else init();
  setInterval(function(){if(isMobile()){setupMobileNav();addPlayerButton();updateActive();}},1500);
  document.addEventListener('visibilitychange',function(){if(!document.hidden) setTimeout(init,250);});
})();
