package com.videolooper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Relance le VideoLooper sur PLUSIEURS événements système, pas seulement le
 * boot. Objectif : si le cadre (byJoywe) ne filtre que le signal de démarrage,
 * un autre événement (connexion wifi, déverrouillage) permettra quand même de
 * lancer l'app.
 *
 * Un anti-rebond évite de relancer l'app en boucle toutes les secondes.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final long DEBOUNCE_MS = 8000; // 8 s entre deux relances

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action);

        // Anti-rebond : pas plus d'une relance toutes les 8 s
        SharedPreferences sp = context.getSharedPreferences("videolooper", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = sp.getLong("lastLaunch", 0);
        if (now - last >= DEBOUNCE_MS) {
            sp.edit().putLong("lastLaunch", now).apply();
            startApp(context);
        }

        // Au boot uniquement : relance différée (12 s) pour passer au-dessus
        // de byJoywe une fois qu'il s'est posé.
        if (isBoot) {
            try {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(
                        context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT);
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    am.set(AlarmManager.RTC_WAKEUP, now + 12000, pi);
                }
            } catch (Exception ignored) { }
        }
    }

    private void startApp(Context context) {
        try {
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(i);
        } catch (Exception ignored) { }
    }
}
