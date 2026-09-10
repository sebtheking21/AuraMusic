package com.auramusic.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.IBinder;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {
 public static final String ACTION_PLAY="com.auramusic.app.PLAY", ACTION_PAUSE="com.auramusic.app.PAUSE", ACTION_PLAY_PAUSE="com.auramusic.app.PLAY_PAUSE", ACTION_NEXT="com.auramusic.app.NEXT", ACTION_PREVIOUS="com.auramusic.app.PREVIOUS";
 public static final String EXTRA_URL="url", EXTRA_TITLE="title", EXTRA_ARTIST="artist", EXTRA_ART="art";
 private ExoPlayer player; private MediaSession mediaSession;
 @Override public void onCreate(){super.onCreate(); AudioAttributes aa=new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(); player=new ExoPlayer.Builder(this).build(); player.setAudioAttributes(aa,true); player.setHandleAudioBecomingNoisy(true); mediaSession=new MediaSession.Builder(this,player).setSessionActivity(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();}
 @Override public int onStartCommand(Intent intent,int flags,int startId){try{if(intent!=null){String a=intent.getAction(); if(ACTION_PLAY.equals(a))playUrl(intent.getStringExtra(EXTRA_URL),intent.getStringExtra(EXTRA_TITLE),intent.getStringExtra(EXTRA_ARTIST),intent.getStringExtra(EXTRA_ART)); else if(ACTION_PAUSE.equals(a))player.pause(); else if(ACTION_PLAY_PAUSE.equals(a)){if(player.isPlaying())player.pause();else player.play();} else if(ACTION_NEXT.equals(a)&&player.hasNextMediaItem())player.seekToNextMediaItem(); else if(ACTION_PREVIOUS.equals(a)&&player.hasPreviousMediaItem())player.seekToPreviousMediaItem();}}catch(Throwable ignored){} return START_STICKY;}
 private void playUrl(String url,String title,String artist,String art){try{if(url==null||url.trim().isEmpty())return; String l=url.toLowerCase(); if(l.startsWith("blob:")||l.startsWith("javascript:")||l.contains("youtube.com/watch")||l.contains("youtu.be/"))return; MediaMetadata.Builder m=new MediaMetadata.Builder(); if(title!=null)m.setTitle(title); if(artist!=null)m.setArtist(artist); if(art!=null&&!art.isEmpty())try{m.setArtworkUri(android.net.Uri.parse(art));}catch(Exception ignored){} player.setMediaItem(new MediaItem.Builder().setUri(url).setMediaMetadata(m.build()).build()); player.prepare(); player.play();}catch(Throwable ignored){}}
 @Override public MediaSession onGetSession(MediaSession.ControllerInfo info){return mediaSession;}
 @Override public void onDestroy(){try{if(mediaSession!=null)mediaSession.release();}catch(Throwable ignored){} try{if(player!=null)player.release();}catch(Throwable ignored){} mediaSession=null;player=null;super.onDestroy();}
 @Override public IBinder onBind(Intent intent){return super.onBind(intent);}
}