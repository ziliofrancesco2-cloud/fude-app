package it.crowclaw.fude;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;
import java.util.TimeZone;

/** Promemoria giornaliero: allarme non esatto (nessun permesso speciale) che si riprogramma da solo. */
public class Reminders extends BroadcastReceiver {

    static final String CHANNEL = "fude_daily";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("fude_reminder", Context.MODE_PRIVATE);
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Promemoria giornaliero",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Ti ricorda la sessione di giapponese del giorno");
            c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, Reminders.class).setAction("it.crowclaw.fude.REMIND");
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("on", false)) return;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, p.getInt("h", 20));
        t.set(Calendar.MINUTE, p.getInt("m", 0));
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis() + 30_000) t.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.setWindow(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), 10 * 60 * 1000L, alarmIntent(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(alarmIntent(c));
    }

    static int today() {
        long now = System.currentTimeMillis();
        return (int) ((now + TimeZone.getDefault().getOffset(now)) / 86_400_000L);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) {
            schedule(c);
            return;
        }
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("on", false)) return;
        schedule(c); // prossimo giorno

        int day = p.getInt("day", -1);
        boolean done = day == today() && p.getBoolean("done", false);
        if (done) return;

        int streak = p.getInt("streak", 0);
        int left = day == today() ? p.getInt("left", 0) : -1;
        String title = streak > 0 ? "連続 " + streak + " giorni: non spezzare la serie!" : "毎日、手で書く。";
        String text = left > 0 ? "Ti mancano " + left + " esercizi per l'obiettivo di oggi."
                : "La sessione di oggi ti aspetta: bastano pochi minuti.";

        createChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_stat_fude)
                .setColor(0xFFE5482E)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            c.getSystemService(NotificationManager.class).notify(1, b.build());
        } catch (SecurityException ignored) {
            // permesso notifiche negato
        }
    }
}
