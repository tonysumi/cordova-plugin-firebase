package org.apache.cordova.firebase;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.support.v4.content.LocalBroadcastManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.voice.CallInvite;
import com.twilio.voice.MessageException;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;
import java.util.Map;
import java.util.Random;

public class FirebasePluginMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FirebasePlugin";
  private static final String NOTIFICATION_ID_KEY = "NOTIFICATION_ID";
    private static final String CALL_SID_KEY = "CALL_SID";
    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        String title;
        String text;
        String type = null;
        String id;
        String sound = null;
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            text = remoteMessage.getNotification().getBody();
            id = remoteMessage.getMessageId();
        } else {
            title = remoteMessage.getData().get("title");
            text = remoteMessage.getData().get("text");
            id = remoteMessage.getData().get("id");
            sound = remoteMessage.getData().get("sound");
            type = remoteMessage.getData().get("type");

            if(TextUtils.isEmpty(text)){
                text = remoteMessage.getData().get("body");
            }
        }

        if(TextUtils.isEmpty(id)){
            Random rand = new Random();
            int  n = rand.nextInt(50) + 1;
            id = Integer.toString(n);
        }

        Log.d(TAG, "From: " + remoteMessage.getFrom());
        Log.d(TAG, "Notification Message id: " + id);
        Log.d(TAG, "Notification Message Title: " + title);
        Log.d(TAG, "Notification Message Body/Text: " + text);
        Log.d(TAG, "Notification Message Sound: " + sound);
        Log.d(TAG, "Notification type: " + type);

        
        // TODO: Add option to developer to configure if show notification when app on foreground
        if (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title) || (!remoteMessage.getData().isEmpty())) {
            boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback()) && (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title));
            Log.d(TAG, "showNotification: " + showNotification);
            if(!TextUtils.isEmpty(type)){
                sendNotification(id, title, text, remoteMessage.getData(), showNotification, sound);
            }
        }
	}

    private void sendNotification(String id, String title, String messageBody, Map<String, String> data, boolean showNotification, String sound) {
        Bundle bundle = new Bundle();
        for (String key : data.keySet()) {
            bundle.putString(key, data.get(key));
        }
     //   if (showNotification) {
            if (true) {
            Log.d(TAG, "true: " );
            Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
            intent.putExtras(bundle);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if(TextUtils.equals(sound,"TYPE_RINGTONE")){
                 defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setContentTitle(title)
                .setContentText(messageBody)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setContentIntent(pendingIntent);

            int resID = getResources().getIdentifier("notification_icon", "drawable", getPackageName());
            Log.d(TAG, "resID: " + resID);
            if (resID != 0) {
                notificationBuilder.setSmallIcon(resID);
      
            } else {
                notificationBuilder.setSmallIcon(getApplicationInfo().icon);
      
            }

            if (sound != null) {
                Log.d(TAG, "sound before path is: " + sound);
                Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                    + "://" + getPackageName() + "/raw/" + sound);
                Log.d(TAG, "Parsed sound is: " + soundPath.toString());
                notificationBuilder.setSound(soundPath);
            } else {
                Log.d(TAG, "Sound was null ");
            }

            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            {
                int accentID = getResources().getIdentifier("accent", "color", getPackageName());
                notificationBuilder.setColor(getResources().getColor(accentID, null));
            }

            Notification notification = notificationBuilder.build();
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
                int iconID = android.R.id.icon;
                int notiID = getResources().getIdentifier("notification_big", "drawable", getPackageName());
                if (notification.contentView != null) {
                    notification.contentView.setImageViewResource(iconID, notiID);
                }
            }
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.notify(id.hashCode(), notification);
        } else {
            Log.d(TAG, "else: " );
            bundle.putBoolean("tap", false);
            bundle.putString("title", title);
            bundle.putString("body", messageBody);
            FirebasePlugin.sendNotification(bundle);
        }
    }
}
