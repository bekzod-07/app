package uz.itprogress.smsgateway;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREF = "sms_gateway_pref";
    public static final String KEY_SERVER = "server_url";
    public static final String KEY_DEVICE = "device_id";
    public static final String KEY_TOKEN = "token";
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_AUTOSTART = "autostart";

    private EditText serverUrlInput, deviceIdInput, tokenInput, intervalInput;
    private CheckBox autostartCheck;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNeededPermissions();
        buildUi();
        loadPrefs();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 48, 36, 36);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SMS Gateway");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 22);
        root.addView(title);

        statusText = label("Holat: sozlanmagan");
        statusText.setTextSize(17);
        statusText.setPadding(0, 0, 0, 22);
        root.addView(statusText);

        root.addView(label("Server URL"));
        serverUrlInput = input("http://192.168.1.25:8000");
        root.addView(serverUrlInput);

        root.addView(label("Device ID"));
        deviceIdInput = input("phone-001");
        root.addView(deviceIdInput);

        root.addView(label("API token (ixtiyoriy)"));
        tokenInput = input("demo-token");
        root.addView(tokenInput);

        root.addView(label("Tekshirish intervali, sekund"));
        intervalInput = input("5");
        root.addView(intervalInput);

        autostartCheck = new CheckBox(this);
        autostartCheck.setText("Telefon yoqilganda avtomatik ishga tushsin");
        root.addView(autostartCheck);

        Button saveBtn = button("Saqlash");
        saveBtn.setOnClickListener(v -> savePrefs());
        root.addView(saveBtn);

        Button startBtn = button("Serviceni boshlash");
        startBtn.setOnClickListener(v -> { savePrefs(); startGatewayService(); });
        root.addView(startBtn);

        Button stopBtn = button("Serviceni to‘xtatish");
        stopBtn.setOnClickListener(v -> stopGatewayService());
        root.addView(stopBtn);

        Button batteryBtn = button("Battery optimization sozlamasi");
        batteryBtn.setOnClickListener(v -> openBatterySettings());
        root.addView(batteryBtn);

        TextView note = label("Eslatma: SMS yuborish ruxsatini bering. Orqa fonda ishonchli ishlashi uchun Battery optimization’dan chiqarib qo‘ying.");
        note.setPadding(0, 22, 0, 0);
        root.addView(note);

        setContentView(scroll);
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(15);
        v.setPadding(0, 10, 0, 6);
        return v;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(20, 14, 20, 14);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 14, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREF, MODE_PRIVATE);
        serverUrlInput.setText(p.getString(KEY_SERVER, "http://192.168.1.25:8000"));
        deviceIdInput.setText(p.getString(KEY_DEVICE, "phone-001"));
        tokenInput.setText(p.getString(KEY_TOKEN, "demo-token"));
        intervalInput.setText(String.valueOf(p.getInt(KEY_INTERVAL, 5)));
        autostartCheck.setChecked(p.getBoolean(KEY_AUTOSTART, true));
    }

    private void savePrefs() {
        int interval = 5;
        try { interval = Math.max(3, Integer.parseInt(intervalInput.getText().toString().trim())); } catch (Exception ignored) {}
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString(KEY_SERVER, serverUrlInput.getText().toString().trim().replaceAll("/+$", ""))
                .putString(KEY_DEVICE, deviceIdInput.getText().toString().trim())
                .putString(KEY_TOKEN, tokenInput.getText().toString().trim())
                .putInt(KEY_INTERVAL, interval)
                .putBoolean(KEY_AUTOSTART, autostartCheck.isChecked())
                .apply();
        Toast.makeText(this, "Sozlamalar saqlandi", Toast.LENGTH_SHORT).show();
        statusText.setText("Holat: sozlama saqlandi");
    }

    private void startGatewayService() {
        Intent i = new Intent(this, SmsGatewayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        statusText.setText("Holat: service ishlayapti");
    }

    private void stopGatewayService() {
        stopService(new Intent(this, SmsGatewayService.class));
        statusText.setText("Holat: service to‘xtatildi");
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_NETWORK_STATE,
                    Build.VERSION.SDK_INT >= 33 ? Manifest.permission.POST_NOTIFICATIONS : Manifest.permission.INTERNET
            }, 10);
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }
}
