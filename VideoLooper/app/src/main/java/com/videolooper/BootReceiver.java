package com.videolooper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Relance le VideoLooper au démarrage de l'appareil.
 *
 * Sur Android 6.0.1, aucune restriction : le lancement au boot fonctionne
 * directement, à condition d'avoir ouvert l'app au moins une fois après
 * l'installation.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Intent launch = new Intent(context, MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        }
    }
}
