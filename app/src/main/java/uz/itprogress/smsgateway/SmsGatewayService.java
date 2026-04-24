package uz.itprogress.smsgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.telephony.SmsManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class SmsGatewayService extends Service {
    private volatile boolean running = false;
    private Thread workerThread;
    private int sentCount = 0;
    private int errorCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1001, buildNotification("SMS Gateway ishlayapti"));
        running = true;
        startWorker();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startWorker() {
        workerThread = new Thread(() -> {
            while (running) {
                try {
                    SharedPreferences p = getSharedPreferences(MainActivity.PREF, MODE_PRIVATE);
                    String server = p.getString(MainActivity.KEY_SERVER, "").replaceAll("/+$", "");
                    String device = p.getString(MainActivity.KEY_DEVICE, "phone-001");
                    String token = p.getString(MainActivity.KEY_TOKEN, "demo-token");
                    int interval = p.getInt(MainActivity.KEY_INTERVAL, 5);

                    if (!server.isEmpty()) {
                        ping(server, device, token);
                        JSONObject sms = nextSms(server, device, token);
                        if (sms != null && sms.optBoolean("has_sms", false)) {
                            long messageId = sms.optLong("id");
                            String phone = sms.optString("phone");
                            String text = sms.optString("message");
                            try {
                                sendSms(phone, text);
                                sentCount++;
                                report(server, token, messageId, "sent", "SMS telefon orqali yuborildi");
                                updateNotification("Yuborildi: " + sentCount + " | Xato: " + errorCount);
                            } catch (Exception e) {
                                errorCount++;
                                report(server, token, messageId, "failed", e.getMessage());
                                updateNotification("Yuborildi: " + sentCount + " | Xato: " + errorCount);
                            }
                        }
                    }
                    Thread.sleep(Math.max(3, interval) * 1000L);
                } catch (Exception e) {
                    errorCount++;
                    updateNotification("Server bilan xatolik. Xato: " + errorCount);
                    try { Thread.sleep(7000); } catch (InterruptedException ignored) {}
                }
            }
        });
        workerThread.start();
    }

    private void sendSms(String phone, String text) {
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(text);
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
    }

    private void ping(String server, String deviceId, String token) throws Exception {
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("token", token);
        body.put("name", "Android SMS Gateway");
        body.put("phone_model", Build.MANUFACTURER + " " + Build.MODEL);
        body.put("android_version", Build.VERSION.RELEASE);
        body.put("battery", getBatteryLevel());
        postJson(server + "/api/device/ping/", body.toString());
    }

    private JSONObject nextSms(String server, String deviceId, String token) throws Exception {
        String url = server + "/api/sms/next/?device_id=" + encode(deviceId) + "&token=" + encode(token);
        String res = get(url);
        return new JSONObject(res);
    }

    private void report(String server, String token, long id, String status, String message) throws Exception {
        JSONObject body = new JSONObject();
        body.put("token", token);
        body.put("id", id);
        body.put("status", status);
        body.put("message", message == null ? "" : message);
        postJson(server + "/api/sms/report/", body.toString());
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (Build.VERSION.SDK_INT >= 21) return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return 0;
    }

    private String get(String urlString) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestMethod("GET");
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String res = readAll(is);
        c.disconnect();
        return res;
    }

    private String postJson(String urlString, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = c.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String res = readAll(is);
        c.disconnect();
        return res;
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("sms_gateway", "SMS Gateway", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, "sms_gateway")
                : new Notification.Builder(this);
        return b.setContentTitle("SMS Gateway")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.sym_action_email)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001, buildNotification(text));
    }
}
