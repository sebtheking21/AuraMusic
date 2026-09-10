(function(){
  function setupMobileUI(){
    if(!window.matchMedia('(max-width: 900px)').matches) return;
    if(document.querySelector('.mobile-bottom-nav')) return;
    var originals=[].slice.call(document.querySelectorAll('.sidebar .nav-item')).filter(function(el){return el.offsetParent!==null || el.textContent.trim();});
    if(!originals.length) return;
    var nav=document.createElement('nav');
    nav.className='mobile-bottom-nav';
    var preferred=[];
    var wanted=['home','search','library','settings'];
    wanted.forEach(function(w){
      var hit=originals.find(function(el){return el.textContent.toLowerCase().indexOf(w)>=0;});
      if(hit && preferred.indexOf(hit)<0) preferred.push(hit);
    });
    originals.forEach(function(el){if(preferred.length<4 && preferred.indexOf(el)<0) preferred.push(el);});
    preferred.slice(0,4).forEach(function(original){
      var button=document.createElement('button');
      var icon=original.querySelector('i');
      var label=(original.textContent||'').replace(/\s+/g,' ').trim();
      if(label.length>10) label=label.split(' ')[0];
      if(icon) button.innerHTML=icon.outerHTML+'<span>'+label+'</span>'; else button.innerHTML='<span>'+label+'</span>';
      button.addEventListener('click',function(){original.click();updateActive();});
      nav.appendChild(button);
    });
    document.body.appendChild(nav);
    function updateActive(){
      var active=preferred.findIndex(function(el){return el.classList.contains('active');});
      [].slice.call(nav.children).forEach(function(b,i){b.classList.toggle('active',i===active);});
    }
    updateActive();
    setInterval(updateActive,500);
  }
  function inject(){setupMobileUI();}
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',inject); else inject();
  setTimeout(inject,800); setTimeout(inject,2000);
})();
