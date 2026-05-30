package com.example.videolooper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.VideoView;

import java.io.File;

/**
 * VideoLooper : lit une vidéo en boucle, plein écran, mode kiosque.
 *
 * Point clé : on utilise VideoView (et NON un MediaPlayer brut). VideoView
 * attend que sa Surface soit créée avant de préparer/lancer la lecture, ce qui
 * élimine l'erreur "what=1 extra=0" causée par un start() appelé trop tôt.
 */
public class MainActivity extends Activity {

    // === À ADAPTER ===
    private static final String VIDEO_PATH = "/storage/emulated/0/Disruptive.mp4";
    private static final int    MAX_RETRY  = 5;
    private static final int    REQ_READ   = 101;

    private VideoView videoView;
    private TextView  logView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder log = new StringBuilder();
    private int retryCount = 0;
    private boolean initiated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Plein écran + écran toujours allumé (kiosque)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        // --- UI construite en code, pas besoin de fichier XML ---
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#7a0d0d")); // rouge sombre

        videoView = new VideoView(this);
        FrameLayout.LayoutParams vp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        vp.gravity = Gravity.CENTER;
        videoView.setLayoutParams(vp);

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#33ff33")); // vert terminal
        logView.setTextSize(16);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(40, 60, 40, 40);

        root.addView(videoView);
        root.addView(logView); // log par-dessus la vidéo
        setContentView(root);

        println(">>> VideoLooper démarré");
        ensurePermissionThenStart();
    }

    /** Vérifie l'accès au stockage selon la version d'Android, puis démarre. */
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
        } else {                                                       // < Android 6
            startPlayback();
        }
    }

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
                mp.setLooping(true);      // boucle native, sans coupure ni recréation
                retryCount = 0;
                videoView.start();
                // On masque le log une fois la lecture lancée
                handler.postDelayed(new Runnable() {
                    @Override public void run() { logView.setVisibility(View.GONE); }
                }, 800);
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
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
                return true; // erreur gérée -> pas de boîte de dialogue système
            }
        });

        println("Lancement dans 3 secondes...");
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                // setVideoURI déclenche prepareAsync DÈS que la Surface est prête.
                videoView.setVideoURI(Uri.fromFile(f));
            }
        }, 3000);
    }

    private void println(String s) {
        log.append(s).append('\n');
        logView.setText(log.toString());
    }

    // === Cycle de vie / plein écran ===

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        // Retour depuis l'écran d'autorisation "Tous les fichiers" (Android 11+)
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
