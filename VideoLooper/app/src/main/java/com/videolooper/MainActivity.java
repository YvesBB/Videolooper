package com.videolooper;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * VideoLooper : lecture en boucle, plein écran, avec son.
 *
 * Démarrage auto : l'app utilise le root pour devenir le seul "écran d'accueil"
 * (en désactivant byJoywe et Launcher3). Comme l'accueil est lancé à coup sûr
 * au démarrage, l'app se lance alors automatiquement. Réversible via "quitter".
 *
 * Commandes tactiles :
 *   1 appui        -> pause / reprise
 *   2 appuis brefs -> coupe / remet le son
 *   5 appuis       -> restaure l'accueil d'origine + quitte
 */
public class MainActivity extends Activity {

    private static final String VIDEO_PATH = "/storage/emulated/0/Disruptive.mp4";
    private static final int    MAX_RETRY  = 5;
    private static final int    REQ_READ   = 101;
    private static final long   TAP_WINDOW = 350;
    private static final String PREFS      = "videolooper";

    private VideoView videoView;
    private TextView  logView;
    private MediaPlayer player;
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
        ensureMediaVolume();

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
        setupHomeViaRoot();        // devient l'écran d'accueil via root
        ensurePermissionThenStart();
    }

    // === Définir VideoLooper comme écran d'accueil (via root) ===

    private void setupHomeViaRoot() {
        println("Configuration de l'écran d'accueil...");
        new Thread(new Runnable() {
            @Override public void run() {
                final List<String> others = otherHomePackages();
                boolean ok;
                if (others.isEmpty()) {
                    ok = true; // déjà seul écran d'accueil
                } else {
                    StringBuilder script = new StringBuilder();
                    StringBuilder csv = new StringBuilder();
                    for (String pkg : others) {
                        script.append("pm disable-user --user 0 ").append(pkg).append("\n");
                        csv.append(pkg).append(",");
                    }
                    ok = runAsRoot(script.toString());
                    if (ok) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString("disabledHomes", csv.toString()).apply();
                    }
                }
                final boolean fok = ok;
                final int n = others.size();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (fok) {
                            println("\u2713 VideoLooper défini comme écran d'accueil");
                            if (n > 0) println("  (" + n + " accueil(s) désactivé(s))");
                            println("  redémarre le cadre pour vérifier");
                        } else {
                            println("\u2717 Root refusé - accueil non modifié");
                        }
                    }
                });
            }
        }).start();
    }

    /** Liste des paquets "écran d'accueil" autres que nous-mêmes. */
    private List<String> otherHomePackages() {
        List<String> res = new ArrayList<>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = getPackageManager().queryIntentActivities(home, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg != null && !pkg.equals(getPackageName()) && !res.contains(pkg)) {
                    res.add(pkg);
                }
            }
        } catch (Exception ignored) { }
        return res;
    }

    /** Réactive les écrans d'accueil qu'on avait désactivés. */
    private void restoreHomes() {
        try {
            String csv = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString("disabledHomes", "");
            if (csv.isEmpty()) return;
            StringBuilder script = new StringBuilder();
            for (String pkg : csv.split(",")) {
                if (!pkg.trim().isEmpty()) {
                    script.append("pm enable ").append(pkg.trim()).append("\n");
                }
            }
            if (script.length() > 0) runAsRoot(script.toString());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .remove("disabledHomes").apply();
        } catch (Exception ignored) { }
    }

    private boolean runAsRoot(String script) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(script);
            os.writeBytes("\nexit\n");
            os.flush();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Exception ignored) { }
            }
        }
    }

    // === Gestion des appuis ===

    private void onTap() {
        tapCount++;
        handler.removeCallbacks(tapEvaluator);
        handler.postDelayed(tapEvaluator, TAP_WINDOW);
    }

    private void handleTaps(int n) {
        if (n >= 5)      quitApp();
        else if (n == 2) toggleMute();
        else if (n == 1) togglePlayPause();
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
        println("Restauration de l'accueil d'origine...");
        new Thread(new Runnable() {
            @Override public void run() {
                restoreHomes();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        handler.removeCallbacksAndMessages(null);
                        if (videoView != null) videoView.stopPlayback();
                        finishAndRemoveTask();
                    }
                });
            }
        }).start();
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                player = mp;
                mp.setLooping(true);
                applyVolume();
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
