package com.aps.azebri;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

public class NotificationService extends Service {

    private static final String CHANNEL_ID = "internet_check_channel";
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // সার্ভিস শুরু হওয়ার সাথে সাথেই একটি বেসিক নোটিফিকেশন দিয়ে স্টার্ট করা হলো
        Notification notification = createNotification("Initializing...");
        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "SMS Reader Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("APSPaymentSystem")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.app_icon)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (runnable != null) handler.removeCallbacks(runnable);

        runnable = new Runnable() {
            @Override
            public void run() {
                String text = SketchwareUtil.isConnected(getApplicationContext()) ? "SMS Reader is Running" : "No Internet";
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(1, createNotification(text));
                }
                handler.postDelayed(this, 10000); // ১০ সেকেন্ড পরপর চেক করবে (ব্যাটারি সেভ করার জন্য সময় বাড়ানো হয়েছে)
            }
        };
        handler.post(runnable);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}