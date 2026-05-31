package com.videolooper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Relance le VideoLooper au démarrage de l'appareil.
 *
 * Remarques importantes :
 *  - L'app doit avoir été lancée manuellement au moins une fois après
 *    l'installation, sinon Android ne lui envoie aucun broadcast.
 *  - Sur Android 10+ (API 29+), le lancement d'une Activity depuis
 *    l'arrière-plan peut être bloqué par le système. Si rien ne se lance
 *    au boot, privilégie la méthode "app = lanceur (Home)" (voir manifest).
 *  - Certains constructeurs (Xiaomi, Huawei, Oppo...) exigent d'activer
 *    manuellement le "démarrage automatique" de l'app dans leurs réglages.
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
