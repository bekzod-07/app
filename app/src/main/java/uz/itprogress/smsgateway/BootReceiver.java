package uz.itprogress.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences p = context.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
            if (p.getBoolean(MainActivity.KEY_AUTOSTART, true)) {
                Intent service = new Intent(context, SmsGatewayService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
                else context.startService(service);
            }
        }
    }
}
