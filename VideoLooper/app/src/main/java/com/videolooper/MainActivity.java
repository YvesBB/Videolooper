package com.videolooper;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;

public class MainActivity extends Activity {

    private VideoView videoView;
    private TextView statusText;
    private StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        statusText = findViewById(R.id.statusText);

        addLog(">>> VideoLooper démarré");
        addLog("Recherche de la vidéo...");

        File videoFile = findVideo();

        if (videoFile != null && videoFile.exists()) {
            addLog("✓ Vidéo trouvée : " + videoFile.getAbsolutePath());
            addLog("Taille : " + (videoFile.length() / 1024) + " KB");
            addLog("Lancement dans 3 secondes...");

            final Uri uri = Uri.fromFile(videoFile);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    statusText.setVisibility(View.GONE);
                    videoView.setVisibility(View.VISIBLE);
                    videoView.setVideoURI(uri);
                    videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.setLooping(true);
                            videoView.start();
                        }
                    });
                    videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            statusText.setVisibility(View.VISIBLE);
                            videoView.setVisibility(View.GONE);
                            addLog("✗ Erreur lecture (what=" + what + " extra=" + extra + ")");
                            addLog("Nouvelle tentative...");
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() { videoView.start(); }
                            }, 2000);
                            return true;
                        }
                    });
                }
            }, 3000);

        } else {
            addLog("✗ Vidéo NON trouvée !");
            addLog("");
            addLog("Chemins testés :");
            String[] paths = {
                "/storage/emulated/0/Disruptive.mp4",
                "/sdcard/Disruptive.mp4",
                Environment.getExternalStorageDirectory() + "/Disruptive.mp4",
                "/storage/emulated/0/Movies/Disruptive.mp4",
                "/storage/emulated/0/DCIM/Disruptive.mp4",
            };
            for (String p : paths) {
                addLog("  " + (new File(p).exists() ? "✓" : "✗") + " " + p);
            }
            addLog("");
            addLog("Contenu mémoire interne :");
            File root = Environment.getExternalStorageDirectory();
            addLog("Racine : " + root.getAbsolutePath());
            File[] files = root.listFiles();
            if (files != null) {
                for (File f : files) {
                    addLog("  " + f.getName() + (f.isDirectory() ? "/" : ""));
                }
            } else {
                addLog("  (impossible de lire - permissions ?)");
            }
        }
    }

    private void addLog(String msg) {
        log.append(msg).append("\n");
        statusText.setText(log.toString());
    }

    private File findVideo() {
        String[] paths = {
            "/storage/emulated/0/Disruptive.mp4",
            "/sdcard/Disruptive.mp4",
            Environment.getExternalStorageDirectory() + "/Disruptive.mp4",
            "/storage/emulated/0/Movies/Disruptive.mp4",
            "/storage/emulated/0/DCIM/Disruptive.mp4",
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        File root = Environment.getExternalStorageDirectory();
        File[] files = root.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().endsWith(".mp4")) return f;
            }
        }
        return null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && videoView.getVisibility() == View.VISIBLE && !videoView.isPlaying()) {
            videoView.start();
        }
    }
}
