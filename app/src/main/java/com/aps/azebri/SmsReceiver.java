package com.aps.azebri;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.content.SharedPreferences;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import android.os.Build;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;
import android.graphics.BitmapFactory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import android.util.Log; // এটি নিশ্চিত করা হলো

public class SmsReceiver extends BroadcastReceiver {

    private SharedPreferences User;
    private SharedPreferences Save;
    private ArrayList<HashMap<String, Object>> ListMap = new ArrayList<>();
    private final ArrayList<String> ApprovedSenders = new ArrayList<>(Arrays.asList(
            "bkash", "nagad", "rocket", "upay", "surecash", "ipay", "okwallet", "tap", "cellfin"
    ));

    @Override
    public void onReceive(Context context, Intent intent) {
        User = context.getSharedPreferences("User", Context.MODE_PRIVATE);
        Save = context.getSharedPreferences("Save", Context.MODE_PRIVATE);

        if (intent.getAction() != null && intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    String from = "";
                    StringBuilder bodyBuilder = new StringBuilder();

                    for (Object pdu : pdus) {
                        SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                        bodyBuilder.append(message.getDisplayMessageBody());
                        from = message.getDisplayOriginatingAddress();
                    }

                    String body = bodyBuilder.toString();
                    String lowerBody = body.toLowerCase();

                    if (from != null && ApprovedSenders.contains(from.toLowerCase())) {
                        boolean isIncoming = lowerBody.contains("received") || lowerBody.contains("cash in") || lowerBody.contains("টাকা জমা");
                        boolean isOutgoing = lowerBody.contains("sent money") || lowerBody.contains("payment to") || lowerBody.contains("cash out");

                        if (isIncoming && !isOutgoing) {
                            saveMessage(from, body);
                            sendToServer(context, from, body);
                        }
                    }
                }
            }
        }
    }

    private void sendToServer(final Context context, final String from, final String body) {
        RequestQueue queue = Volley.newRequestQueue(context);
        String url = "https://payment.st-shop.xyz/api/add-data";

        Map<String, String> params = new HashMap<>();
        params.put("user_email", User.getString("email", ""));
        params.put("device_key", User.getString("device_key", ""));
        params.put("device_ip", User.getString("device_ip", ""));
        params.put("address", from);
        params.put("message", body.replaceAll("\r?\n", " "));

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                response -> sendLocalNotify(context, from, "SMS Processed"),
                error -> {
                    Log.e("VolleyError", error.toString());
                    sendLocalNotify(context, "Error", "Cloud sync failed.");
                }
        ) {
            @Override
            protected Map<String, String> getParams() { return params; }
        };
        queue.add(stringRequest);
    }

    private void sendLocalNotify(final Context context, String title, String message) {
        String channelId = "SmsNotify";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "SMS Reader", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.app_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void saveMessage(String from, String body) {
        try {
            String json = Save.getString("Json", "[]");
            ListMap = new Gson().fromJson(json, new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType());
            if (ListMap == null) ListMap = new ArrayList<>();

            HashMap<String, Object> newMsg = new HashMap<>();
            newMsg.put("From", from);
            newMsg.put("Body", body);
            ListMap.add(0, newMsg);

            Save.edit().putString("Json", new Gson().toJson(ListMap)).apply();
        } catch (Exception e) {
            Log.e("SaveError", e.getMessage() != null ? e.getMessage() : "Error saving");
        }
    }
}