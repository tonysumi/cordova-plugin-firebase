package org.apache.cordova.firebase;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
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
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
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
//-------------------------------------------Custom----START---------------------------------------------------------

//Start Incoming Call
        if (remoteMessage.getData().size() > 0) {
            String twi_message_type = remoteMessage.getData().get("twi_message_type");
            if (!TextUtils.isEmpty(twi_message_type) || TextUtils.equals(twi_message_type,"twilio.voice.call")) {
                
                Map<String, String> data = remoteMessage.getData();
                for (Map.Entry<String,String> entry : data.entrySet()) {
                Log.d("data", "Key: " + entry.getKey());
                Log.d("data", "Value: " + entry.getValue());
                }
                final int notificationId = (int) System.currentTimeMillis();
                Voice.handleMessage(this, data, new MessageListener() {
                    @Override
                    public void onCallInvite(CallInvite callInvite) {
                        String callSid = callInvite.getCallSid();
                        Log.d("Incoming onCallInvite", callSid + " : " + callInvite);
                       
                          FirebasePluginMessagingService.this.notify(callInvite, notificationId);
                          FirebasePluginMessagingService.this.sendCallInviteToActivity(callInvite, notificationId);
                    }

                    @Override
                    public void onError(MessageException messageException) {
                        Log.d("Incoming Error", messageException.getLocalizedMessage());
                    }
                });

            }
            else if (!TextUtils.isEmpty(twi_message_type) || TextUtils.equals(twi_message_type,"twilio.voice.cancel")) {
                Log.d("Incoming Cancel", "Incoming call canceled");
            }

        }



//End Incoming Call
    }


private void notify(CallInvite callInvite, int notificationId) {
        String callSid = callInvite.getCallSid();
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (callInvite.getState() == CallInvite.State.PENDING) {
            Intent intent = new Intent(this, FirebasePlugin.class);
            intent.setAction(FirebasePlugin.ACTION_INCOMING_CALL);
            intent.putExtra(FirebasePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            intent.putExtra(FirebasePlugin.INCOMING_CALL_INVITE, callInvite);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_ONE_SHOT);
            /*
             * Pass the notification id and call sid to use as an identifier to cancel the
             * notification later
             */
            Bundle extras = new Bundle();
            extras.putInt(NOTIFICATION_ID_KEY, notificationId);
            extras.putString(CALL_SID_KEY, callSid);

            int iconIdentifier = getResources().getIdentifier("notification_icon", "drawable", getPackageName());
            NotificationCompat.Builder notificationBuilder =
                     new NotificationCompat.Builder(this)
                             .setSmallIcon(iconIdentifier)
                            .setContentTitle("SaBRO")
                             .setContentText(callInvite.getFrom() + " is calling.")
                             .setAutoCancel(true)
                             .setExtras(extras)
                             .setContentIntent(pendingIntent);
                            /*.setGroup("test_app_notification")
                             .setColor(Color.rgb(214, 10, 37));*/
 
             notificationManager.notify(notificationId, notificationBuilder.build());
          
        } else {
           // SoundPoolManager.getInstance(this).stopRinging();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                /*
                 * If the incoming call was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    Bundle extras = notification.extras;
                    String notificationCallSid = extras.getString(CALL_SID_KEY);

                    if (callSid.equals(notificationCallSid)) {
                        notificationManager.cancel(extras.getInt(NOTIFICATION_ID_KEY));
                    } else {
                        sendCallInviteToActivity(callInvite, notificationId);
                    }
                }
            } else {
                /*
                 * Prior to Android M the notification manager did not provide a list of
                 * active notifications so we lazily clear all the notifications when
                 * receiving a cancelled call.
                 *
                 * In order to properly cancel a notification using
                 * NotificationManager.cancel(notificationId) we should store the call sid &
                 * notification id of any incoming calls using shared preferences or some other form
                 * of persistent storage.
                 */
                notificationManager.cancelAll();
            }
        }
    }

    /*
     * Send the CallInvite to the FirebasePlugin
     */
    private void sendCallInviteToActivity(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(FirebasePlugin.ACTION_INCOMING_CALL);
        intent.putExtra(FirebasePlugin.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(FirebasePlugin.INCOMING_CALL_INVITE, callInvite);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }


//-------------------------------------------Custom----End---------------------------------------------------------

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
/*                
            Intent intentAllow = new Intent(this, OnNotificationOpenReceiver.class);
            intentAllow.putExtras(bundle);
            intentAllow.putExtra("action","ALLOW");
            PendingIntent pendingIntentAllow = PendingIntent.getBroadcast(this, id.hashCode(), intentAllow,PendingIntent.FLAG_UPDATE_CURRENT);
                
            Intent intentDeny = new Intent(this, OnNotificationOpenReceiver.class);
            intentDeny.putExtras(bundle);
            intentDeny.putExtra("action","DENY");
            PendingIntent pendingIntentDeny = PendingIntent.getBroadcast(this, id.hashCode(), intentDeny,PendingIntent.FLAG_UPDATE_CURRENT);
*/
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
       //         notificationBuilder.addAction(resID, "Allow",pendingIntentAllow);
       //         notificationBuilder.addAction(resID, "Deny",pendingIntentDeny);
            } else {
                notificationBuilder.setSmallIcon(getApplicationInfo().icon);
       //         notificationBuilder.addAction(getApplicationInfo().icon, "Allow",pendingIntentAllow);
      //          notificationBuilder.addAction(getApplicationInfo().icon, "Deny",pendingIntentDeny);
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
