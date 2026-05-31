package com.videolooper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Relance le VideoLooper au démarrage.
 *
 * Sur les cadres verrouillés (ex. byJoywe), l'app maison reprend souvent le
 * dessus juste après le boot. On lance donc l'app deux fois :
 *   1) tout de suite,
 *   2) une seconde fois ~12 s plus tard, pour passer PAR-DESSUS byJoywe une
 *      fois qu'il s'est posé.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // 1) Lancement immédiat
            startApp(context);

            // 2) Relance différée (12 s) au cas où byJoywe revient au premier plan
            try {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(
                        context, 1001, i, PendingIntent.FLAG_UPDATE_CURRENT);

                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    am.set(AlarmManager.RTC_WAKEUP,
                            System.currentTimeMillis() + 12000, pi);
                }
            } catch (Exception ignored) { }
        }
    }

    private void startApp(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }
}
