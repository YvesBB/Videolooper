package com.videolooper;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.WindowManager;
import android.widget.VideoView;
import java.io.File;

public class MainActivity extends Activity {

    private VideoView videoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Plein écran total, pas de barre de statut
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Cache toutes les barres système
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);

        // Cherche la vidéo dans la mémoire interne
        File videoFile = findVideo();

        if (videoFile != null && videoFile.exists()) {
            Uri uri = Uri.fromFile(videoFile);
            videoView.setVideoURI(uri);
            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true); // Boucle infinie
                    videoView.start();
                }
            });
            videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // Relance en cas d'erreur
                    videoView.start();
                    return true;
                }
            });
        }
    }

    private File findVideo() {
        // Cherche d'abord au chemin exact
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

        // Sinon cherche la première vidéo MP4 dans la mémoire interne
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
        if (videoView != null && !videoView.isPlaying()) {
            videoView.start();
        }
    }
}
