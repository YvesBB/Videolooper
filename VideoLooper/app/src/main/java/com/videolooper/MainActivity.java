package com.example.videolooper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;

/**
 * VideoLooper : lecture en boucle, plein écran, mode kiosque, avec son.
 *
 * Commandes tactiles :
 *   1 appui        -> pause / reprise
 *   2 appuis brefs -> coupe / remet le son
 *   5 appuis       -> arrête le programme
 *
 * On distingue le nombre d'appuis avec une fenêtre de TAP_WINDOW ms : tant
 * que les appuis s'enchaînent dans cette fenêtre, ils sont comptés ensemble,
 * puis l'action correspondante est déclenchée.
 */
public class MainActivity extends Activity {

    // === À ADAPTER ===
    private static final String VIDEO_PATH = "/storage/emulated/0/Disruptive.mp4";
    private static final int    MAX_RETRY  = 5;
    private static final int    REQ_READ   = 101;
    private static final long   TAP_WINDOW = 350; // ms pour grouper les appuis

    private VideoView videoView;
    private TextView  logView;
    private MediaPlayer player;       // récupéré dans onPrepared (pour le volume)
    private boolean muted = false;
    private int retryCount = 0;
    private int tapCount = 0;
    private boolean initiated = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();

    private final Runnable tapEvaluator = new Runnable() {
        @Override public void run() {
            int n = tapCount;
            tapCount = 0;
            handleTaps(n);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        // S'assure que le volume média est audible (sinon "pas de son")
        ensureMediaVolume();

        // --- UI en code ---
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#7a0d0d"));

        videoView = new VideoView(this);
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        full.gravity = Gravity.CENTER;
        videoView.setLayoutParams(full);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#33ff33"));
        logView.setTextSize(16);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(40, 60, 40, 40);

        // Couche transparente qui capte tous les appuis, au-dessus de la vidéo
        View touchCatcher = new View(this);
        touchCatcher.setLayoutParams(full);
        touchCatcher.setClickable(true);
        touchCatcher.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onTap(); }
        });

        root.addView(videoView);
        root.addView(logView);
        root.addView(touchCatcher);
        setContentView(root);

        println(">>> VideoLooper démarré");
        ensurePermissionThenStart();
    }

    // === Gestion des appuis ===

    private void onTap() {
        tapCount++;
        handler.removeCallbacks(tapEvaluator);
        handler.postDelayed(tapEvaluator, TAP_WINDOW);
    }

    private void handleTaps(int n) {
        if (n >= 5) {
            quitApp();
        } else if (n == 2) {
            toggleMute();
        } else if (n == 1) {
            togglePlayPause();
        }
        // 3 ou 4 appuis : aucune action
    }

    private void togglePlayPause() {
        if (videoView.isPlaying()) {
            videoView.pause();
            toast("\u23F8 Pause");
        } else {
            videoView.start();
            toast("\u25B6 Lecture");
        }
    }

    private void toggleMute() {
        muted = !muted;
        applyVolume();
        toast(muted ? "\uD83D\uDD07 Son coupé" : "\uD83D\uDD0A Son activé");
    }

    private void applyVolume() {
        if (player != null) {
            float v = muted ? 0f : 1f;
            player.setVolume(v, v);
        }
    }

    private void quitApp() {
        println("Arrêt du programme...");
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
        finishAndRemoveTask();
    }

    /** Remonte le volume STREAM_MUSIC à ~70% s'il est à zéro. */
    private void ensureMediaVolume() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (int) (max * 0.7f), 0);
            }
        } catch (Exception ignored) { }
    }

    // === Permissions ===

    private void ensurePermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {           // Android 11+
            if (Environment.isExternalStorageManager()) {
                startPlayback();
            } else {
                println("Autorisation 'Tous les fichiers' requise...");
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {    // Android 6..10
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                startPlayback();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_READ);
            }
        } else {
            startPlayback();
        }
    }

    // === Lecture ===

    private void startPlayback() {
        initiated = true;
        logView.setVisibility(View.VISIBLE);

        final File f = new File(VIDEO_PATH);
        println("Recherche de la vidéo...");
        if (!f.exists() || !f.canRead()) {
            println("\u2717 Vidéo introuvable ou illisible : " + VIDEO_PATH);
            return;
        }
        println("\u2713 Vidéo trouvée : " + VIDEO_PATH);
        println("Taille : " + (f.length() / 1024) + " KB");

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                player = mp;              // pour piloter le volume (mute)
                mp.setLooping(true);      // boucle native, sans coupure
                applyVolume();            // applique l'état mute courant
                retryCount = 0;
                videoView.start();
                handler.postDelayed(new Runnable() {
                    @Override public void run() { logView.setVisibility(View.GONE); }
                }, 800);
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                player = null;
                println("\u2717 Erreur lecture (what=" + what + " extra=" + extra + ")");
                logView.setVisibility(View.VISIBLE);
                if (++retryCount <= MAX_RETRY) {
                    println("Nouvelle tentative... (" + retryCount + "/" + MAX_RETRY + ")");
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { startPlayback(); }
                    }, 2000);
                } else {
                    println("Abandon après " + MAX_RETRY + " tentatives.");
                }
                return true;
            }
        });

        println("Lancement dans 3 secondes...");
        handler.postDelayed(new Runnable() {
            @Override public void run() { videoView.setVideoURI(Uri.fromFile(f)); }
        }, 3000);
    }

    private void println(String s) {
        log.append(s).append('\n');
        logView.setText(log.toString());
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // === Cycle de vie / plein écran ===

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (!initiated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && Environment.isExternalStorageManager()) {
            startPlayback();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == REQ_READ) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startPlayback();
            } else {
                println("\u2717 Permission refusée.");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (videoView != null) videoView.stopPlayback();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
