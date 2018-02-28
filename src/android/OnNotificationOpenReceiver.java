package org.apache.cordova.firebase;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class OnNotificationOpenReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
        Log.d("Notification", "Notification onReceive context.getPackageName(): " + context.getPackageName());
        Log.d("Notification", "Notification onReceive launchIntent: " + launchIntent);
        String action=intent.getStringExtra("action");
        if(action.equals("ALLOW")){
            Log.d("Notification", "Notification onReceive ALLOW: ");
        }
        else if(action.equals("DENY")){
            Log.d("Notification", "Notification onReceive DENY: ");
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle data = intent.getExtras();
        data.putBoolean("tap", true);
        FirebasePlugin.sendNotification(data);
        launchIntent.putExtras(data);
        context.startActivity(launchIntent);
    }
}
