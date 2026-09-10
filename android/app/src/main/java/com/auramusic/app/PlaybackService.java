package com.auramusic.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {
    public static final String ACTION_PLAY = "com.auramusic.app.PLAY";
    public static final String ACTION_PAUSE = "com.auramusic.app.PAUSE";
    public static final String ACTION_PLAY_PAUSE = "com.auramusic.app.PLAY_PAUSE";
    public static final String ACTION_NEXT = "com.auramusic.app.NEXT";
    public static final String ACTION_PREVIOUS = "com.auramusic.app.PREVIOUS";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ART = "art";

    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(audioAttributes, true);
        player.setHandleAudioBecomingNoisy(true);

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(buildSessionActivity())
                .build();
    }

    private PendingIntent buildSessionActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY.equals(action)) {
                playUrl(
                        intent.getStringExtra(EXTRA_URL),
                        intent.getStringExtra(EXTRA_TITLE),
                        intent.getStringExtra(EXTRA_ARTIST),
                        intent.getStringExtra(EXTRA_ART)
                );
            } else if (ACTION_PAUSE.equals(action)) {
                player.pause();
            } else if (ACTION_PLAY_PAUSE.equals(action)) {
                if (player.isPlaying()) player.pause(); else player.play();
            } else if (ACTION_NEXT.equals(action)) {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem();
            } else if (ACTION_PREVIOUS.equals(action)) {
                if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem();
            }
        }
        return START_STICKY;
    }

    private void playUrl(String url, String title, String artist, String art) {
        if (url == null || url.trim().isEmpty()) return;
        // Native background playback only accepts real network/local media URLs.
        // Blob URLs, JavaScript URLs, and YouTube page URLs are deliberately ignored.
        String lower = url.toLowerCase();
        if (lower.startsWith("blob:") || lower.startsWith("javascript:") || lower.contains("youtube.com/watch") || lower.contains("youtu.be/")) return;

        MediaMetadata.Builder metadata = new MediaMetadata.Builder();
        if (title != null) metadata.setTitle(title);
        if (artist != null) metadata.setArtist(artist);
        if (art != null && !art.isEmpty()) {
            try { metadata.setArtworkUri(android.net.Uri.parse(art)); } catch (Exception ignored) {}
        }

        MediaItem item = new MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(metadata.build())
                .build();
        player.setMediaItem(item);
        player.prepare();
        player.play();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Keep the Media3 session/player alive when the app task is swiped away.
        if (player != null && player.isPlaying()) return;
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) mediaSession.release();
        if (player != null) player.release();
        mediaSession = null;
        player = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}
