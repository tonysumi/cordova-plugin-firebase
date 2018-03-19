package org.apache.cordova.firebase;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.support.v4.app.NotificationCompat;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.content.BroadcastReceiver;
import android.util.Base64;
import android.util.Log;
import android.media.AudioManager;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import me.leolin.shortcutbadger.ShortcutBadger;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult.Status;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;


// Firebase PhoneAuth
import java.util.concurrent.TimeUnit;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;


//Twilio
import com.twilio.voice.Call;
import com.twilio.voice.CallState;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

public class FirebasePlugin extends CordovaPlugin {

    private FirebaseAnalytics mFirebaseAnalytics;
    private final String TAG = "FirebasePlugin";
    protected static final String KEY = "badge";

    private static boolean inBackground = true;
    private static ArrayList<Bundle> notificationStack = null;
    private static CallbackContext notificationCallbackContext;
    private static CallbackContext tokenRefreshCallbackContext;
    private CallbackContext mInitCallbackContext;
    private int mCurrentNotificationId = 1;


 // An incoming call intent to process (can be null)
    private Intent mIncomingCallIntent;

// Marshmallow Permissions
    public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final int RECORD_AUDIO_REQ_CODE = 0;

 // Constants for Intents and Broadcast Receivers
    public static final String INCOMING_CALL_INVITE = "INCOMING_CALL_INVITE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String ACTION_INCOMING_CALL = "ACTION_INCOMING_CALL";
    public static final String HANGUP_ACTION = "HANGUP_ACTION";

    
    // Access Token
    private String mAccessToken;

    private Call mCall;
    private CallInvite mCallInvite;
    RegistrationListener registrationListener = registrationListener();

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_INCOMING_CALL)) {
                /*
                 * Handle the incoming call invite
                 */
                    Log.d(TAG, "mBroadcastReceiver action" + action);
                handleIncomingCallIntent(intent);
            }
        }
    };
    

    // Twilio Voice Registration Listener
    private RegistrationListener registrationListener() {
            return new RegistrationListener() {
                @Override
                public void onRegistered(String accessToken, String fcmToken) {
                    Log.d(TAG, "Successfully registered FCM " + fcmToken);
                    Log.d(TAG, "onRegistered accessToken: " + accessToken);
                }

                @Override
                public void onError(RegistrationException error, String accessToken, String fcmToken) {
                    Log.e("onError accessToken: ", accessToken);
                    String message = String.format("Registration Error: %d, %s, %s", error.getErrorCode(), error.getMessage(), error.getExplanation());
                    Log.e(TAG, message);
                }
            };
        }

    @Override
    protected void pluginInitialize() {
        final Context context = this.cordova.getActivity().getApplicationContext();
        final Bundle extras = this.cordova.getActivity().getIntent().getExtras();
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Log.d(TAG, "Starting Firebase plugin");
                mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
                mFirebaseAnalytics.setAnalyticsCollectionEnabled(true);
                if (extras != null && extras.size() > 1) {
                    if (FirebasePlugin.notificationStack == null) {
                        FirebasePlugin.notificationStack = new ArrayList<Bundle>();
                    }
                    if (extras.containsKey("google.message_id")) {
                        extras.putBoolean("tap", true);
                        notificationStack.add(extras);
                    }
                }
            }
        });
    }

@Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        Log.d(TAG, "initialize()");


        // initialize sound SoundPoolManager
       // SoundPoolManager.getInstance(cordova.getActivity());

        // Handle an incoming call intent if launched from a notification
        Intent intent = cordova.getActivity().getIntent();
        if (intent.getAction().equals(ACTION_INCOMING_CALL)) {
            mIncomingCallIntent = intent;
        }
    }

    @Override
    public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
        super.onRestoreStateForActivityResult(state, callbackContext);
        Log.d(TAG, "onRestoreStateForActivityResult()");
        mInitCallbackContext = callbackContext;
    }


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {


        if (action.equals("getInstanceId")) {
            this.getInstanceId(callbackContext);
            return true;
        } else if (action.equals("getId")) {
            this.getId(callbackContext);
            return true;
        } else if (action.equals("getToken")) {
            this.getToken(callbackContext);
            return true;
        } else if (action.equals("hasPermission")) {
            this.hasPermission(callbackContext);
            return true;
        } else if (action.equals("setBadgeNumber")) {
            this.setBadgeNumber(callbackContext, args.getInt(0));
            return true;
        } else if (action.equals("getBadgeNumber")) {
            this.getBadgeNumber(callbackContext);
            return true;
        } else if (action.equals("subscribe")) {
            this.subscribe(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("unsubscribe")) {
            this.unsubscribe(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("unregister")) {
            this.unregister(callbackContext);
            return true;
        } else if (action.equals("onNotificationOpen")) {
            this.onNotificationOpen(callbackContext);
            return true;
        } else if (action.equals("onTokenRefresh")) {
            this.onTokenRefresh(callbackContext);
            return true;
        } else if (action.equals("logEvent")) {
            this.logEvent(callbackContext, args.getString(0), args.getJSONObject(1));
            return true;
        } else if (action.equals("logError")) {
            this.logError(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("setScreenName")) {
            this.setScreenName(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("setUserId")) {
            this.setUserId(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("setUserProperty")) {
            this.setUserProperty(callbackContext, args.getString(0), args.getString(1));
            return true;
        } else if (action.equals("activateFetched")) {
            this.activateFetched(callbackContext);
            return true;
        } else if (action.equals("fetch")) {
            if (args.length() > 0) this.fetch(callbackContext, args.getLong(0));
            else this.fetch(callbackContext);
            return true;
        } else if (action.equals("getByteArray")) {
            if (args.length() > 1) this.getByteArray(callbackContext, args.getString(0), args.getString(1));
            else this.getByteArray(callbackContext, args.getString(0), null);
            return true;
        } else if (action.equals("getValue")) {
            if (args.length() > 1) this.getValue(callbackContext, args.getString(0), args.getString(1));
            else this.getValue(callbackContext, args.getString(0), null);
            return true;
        } else if (action.equals("getInfo")) {
            this.getInfo(callbackContext);
            return true;
        } else if (action.equals("setConfigSettings")) {
            this.setConfigSettings(callbackContext, args.getJSONObject(0));
            return true;
        } else if (action.equals("setDefaults")) {
            if (args.length() > 1) this.setDefaults(callbackContext, args.getJSONObject(0), args.getString(1));
            else this.setDefaults(callbackContext, args.getJSONObject(0), null);
            return true;
        } else if (action.equals("verifyPhoneNumber")) {
            this.verifyPhoneNumber(callbackContext, args.getString(0), args.getInt(1));
            return true;
        } else if (action.equals("startTrace")) {
            this.startTrace(callbackContext, args.getString(0));
            return true;
        } else if (action.equals("incrementCounter")) {
            this.incrementCounter(callbackContext, args.getString(0), args.getString(1));
            return true;
        } else if (action.equals("stopTrace")) {
            this.stopTrace(callbackContext, args.getString(0));
            return true;
        }else if (action.equals("registerForCallInvites")) {
            mAccessToken = args.getString(0);
            mInitCallbackContext = callbackContext;
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_INCOMING_CALL);
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova.getActivity());
            lbm.registerReceiver(mBroadcastReceiver, intentFilter);
            
            if(cordova.hasPermission(RECORD_AUDIO))
            {
                this.registerForCallInvites(callbackContext, args.getString(0));            }
            else
            {
                cordova.requestPermission(this, RECORD_AUDIO_REQ_CODE, RECORD_AUDIO);
            }
            
            return true;
        } else if ("call".equals(action)) {
            this.call(args, callbackContext);
            return true;
        } else if ("acceptCallInvite".equals(action)) {
            this.acceptCallInvite(args, callbackContext);
            return true;
        } else if ("disconnect".equals(action)) {
            this.disconnect(args, callbackContext);
            return true;
        } else if ("sendDigits".equals(action)) {
            this.sendDigits(args, callbackContext);
            return true;
        } else if ("muteCall".equals(action)) {
            this.muteCall(callbackContext);
            return true;
        }  else if ("unmuteCall".equals(action)) {
            this.unmuteCall(callbackContext);
            return true;
        }  else if ("isCallMuted".equals(action)) {
            this.isCallMuted(callbackContext);
            return true;
        } else if ("callStatus".equals(action)) {
            this.callStatus(callbackContext);
            return true;
        }else if ("rejectCallInvite".equals(action)) {
            this.rejectCallInvite(args, callbackContext);
            return true;
        } else if ("showNotification".equals(action)) {
            this.showNotification("",callbackContext);
            return true;
        } else if ("cancelNotification".equals(action)) {
            this.cancelNotification(args,callbackContext);
            return true;
        } else if ("setSpeaker".equals(action)) {
            this.setSpeaker(args,callbackContext);
            return true;
        }else if ("checkMicrophonePermission".equals(action)) {
            this.checkMicrophonePermission(callbackContext);
            return true;
        }
        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        FirebasePlugin.inBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {     
        FirebasePlugin.inBackground = false;
    }

    @Override
    public void onReset() {
        FirebasePlugin.notificationCallbackContext = null;
        FirebasePlugin.tokenRefreshCallbackContext = null;
    }

    @Override
    public void onDestroy() {
       /* LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(cordova.getActivity());
        lbm.unregisterReceiver(mBroadcastReceiver);*/
        super.onDestroy();
    }

private void registerForCallInvites(final CallbackContext callbackContext,String accessToken) {
        Log.d(TAG, "registerForCallInvites() called");
       
        final String fcmToken = FirebaseInstanceId.getInstance().getToken();
        Log.d(TAG, "fcmToken: "+fcmToken);
        if (fcmToken != null) {
            Log.i(TAG, "Registering with FCM");
            Log.d(TAG, "accessToken :"+accessToken);
            Voice.register(cordova.getActivity().getApplicationContext(), accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
        }
    }


    private void onNotificationOpen(final CallbackContext callbackContext) {
        FirebasePlugin.notificationCallbackContext = callbackContext;
        if (FirebasePlugin.notificationStack != null) {
            for (Bundle bundle : FirebasePlugin.notificationStack) {
                FirebasePlugin.sendNotification(bundle);
            }
            FirebasePlugin.notificationStack.clear();
        }
    }

    private void onTokenRefresh(final CallbackContext callbackContext) {
        FirebasePlugin.tokenRefreshCallbackContext = callbackContext;

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String currentToken = FirebaseInstanceId.getInstance().getToken();

                    if (currentToken != null) {
                        FirebasePlugin.sendToken(currentToken);
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    public static void sendNotification(Bundle bundle) {
        if (!FirebasePlugin.hasNotificationsCallback()) {
            if (FirebasePlugin.notificationStack == null) {
                FirebasePlugin.notificationStack = new ArrayList<Bundle>();
            }
            notificationStack.add(bundle);
            return;
        }
        final CallbackContext callbackContext = FirebasePlugin.notificationCallbackContext;
        if (callbackContext != null && bundle != null) {
            JSONObject json = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                try {
                    json.put(key, bundle.get(key));
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                    return;
                }
            }

            PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, json);
            pluginresult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginresult);
        }
    }

    public static void sendToken(String token) {
        if (FirebasePlugin.tokenRefreshCallbackContext == null) {
            return;
        }
        final CallbackContext callbackContext = FirebasePlugin.tokenRefreshCallbackContext;
        if (callbackContext != null && token != null) {
            PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, token);
            pluginresult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginresult);
        }
    }

    public static boolean inBackground() {
        return FirebasePlugin.inBackground;
    }

    public static boolean hasNotificationsCallback() {
        return FirebasePlugin.notificationCallbackContext != null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Bundle data = intent.getExtras();
        if (data != null && data.containsKey("google.message_id")) {
            data.putBoolean("tap", true);
            FirebasePlugin.sendNotification(data);
        }
    }

    // DEPRECTED - alias of getToken
    private void getInstanceId(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String token = FirebaseInstanceId.getInstance().getToken();
                    callbackContext.success(token);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getId(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String id = FirebaseInstanceId.getInstance().getId();
                    callbackContext.success(id);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getToken(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String token = FirebaseInstanceId.getInstance().getToken();
                    callbackContext.success(token);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void hasPermission(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
                    boolean areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled();
                    JSONObject object = new JSONObject();
                    object.put("isEnabled", areNotificationsEnabled);
                    callbackContext.success(object);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setBadgeNumber(final CallbackContext callbackContext, final int number) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    SharedPreferences.Editor editor = context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit();
                    editor.putInt(KEY, number);
                    editor.apply();
                    ShortcutBadger.applyCount(context, number);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getBadgeNumber(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    SharedPreferences settings = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
                    int number = settings.getInt(KEY, 0);
                    callbackContext.success(number);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void subscribe(final CallbackContext callbackContext, final String topic) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().subscribeToTopic(topic);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void unsubscribe(final CallbackContext callbackContext, final String topic) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void unregister(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseInstanceId.getInstance().deleteInstanceId();
                    String currentToken = FirebaseInstanceId.getInstance().getToken();
                    if (currentToken != null) {
                        FirebasePlugin.sendToken(currentToken);
                    }
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void logEvent(final CallbackContext callbackContext, final String name, final JSONObject params) throws JSONException {
        final Bundle bundle = new Bundle();
        Iterator iter = params.keys();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            Object value = params.get(key);

            if (value instanceof Integer || value instanceof Double) {
                bundle.putFloat(key, ((Number) value).floatValue());
            } else {
                bundle.putString(key, value.toString());
            }
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.logEvent(name, bundle);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void logError(final CallbackContext callbackContext, final String message) throws JSONException {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseCrash.report(new Exception(message));
                    callbackContext.success(1);
                } catch (Exception e) {
                    FirebaseCrash.log(e.getMessage());
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setScreenName(final CallbackContext callbackContext, final String name) {
        // This must be called on the main thread
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setCurrentScreen(cordova.getActivity(), name, null);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setUserId(final CallbackContext callbackContext, final String id) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setUserId(id);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setUserProperty(final CallbackContext callbackContext, final String name, final String value) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setUserProperty(name, value);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void activateFetched(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    final boolean activated = FirebaseRemoteConfig.getInstance().activateFetched();
                    callbackContext.success(String.valueOf(activated));
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void fetch(CallbackContext callbackContext) {
        fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch());
    }

    private void fetch(CallbackContext callbackContext, long cacheExpirationSeconds) {
        fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch(cacheExpirationSeconds));
    }

    private void fetch(final CallbackContext callbackContext, final Task<Void> task) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    task.addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            callbackContext.success();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            callbackContext.error(e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getByteArray(final CallbackContext callbackContext, final String key, final String namespace) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    byte[] bytes = namespace == null ? FirebaseRemoteConfig.getInstance().getByteArray(key)
                            : FirebaseRemoteConfig.getInstance().getByteArray(key, namespace);
                    JSONObject object = new JSONObject();
                    object.put("base64", Base64.encodeToString(bytes, Base64.DEFAULT));
                    object.put("array", new JSONArray(bytes));
                    callbackContext.success(object);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getValue(final CallbackContext callbackContext, final String key, final String namespace) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseRemoteConfigValue value = namespace == null ? FirebaseRemoteConfig.getInstance().getValue(key)
                            : FirebaseRemoteConfig.getInstance().getValue(key, namespace);
                    callbackContext.success(value.asString());
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getInfo(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseRemoteConfigInfo remoteConfigInfo = FirebaseRemoteConfig.getInstance().getInfo();
                    JSONObject info = new JSONObject();

                    JSONObject settings = new JSONObject();
                    settings.put("developerModeEnabled", remoteConfigInfo.getConfigSettings().isDeveloperModeEnabled());
                    info.put("configSettings", settings);

                    info.put("fetchTimeMillis", remoteConfigInfo.getFetchTimeMillis());
                    info.put("lastFetchStatus", remoteConfigInfo.getLastFetchStatus());

                    callbackContext.success(info);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setConfigSettings(final CallbackContext callbackContext, final JSONObject config) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    boolean devMode = config.getBoolean("developerModeEnabled");
                    FirebaseRemoteConfigSettings.Builder settings = new FirebaseRemoteConfigSettings.Builder()
                            .setDeveloperModeEnabled(devMode);
                    FirebaseRemoteConfig.getInstance().setConfigSettings(settings.build());
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setDefaults(final CallbackContext callbackContext, final JSONObject defaults, final String namespace) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (namespace == null)
                        FirebaseRemoteConfig.getInstance().setDefaults(defaultsToMap(defaults));
                    else
                        FirebaseRemoteConfig.getInstance().setDefaults(defaultsToMap(defaults), namespace);
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private static Map<String, Object> defaultsToMap(JSONObject object) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = object.get(key);

            if (value instanceof Integer) {
                //setDefaults() should take Longs
                value = new Long((Integer) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() == 1 && array.get(0) instanceof String) {
                    //parse byte[] as Base64 String
                    value = Base64.decode(array.getString(0), Base64.DEFAULT);
                } else {
                    //parse byte[] as numeric array
                    byte[] bytes = new byte[array.length()];
                    for (int i = 0; i < array.length(); i++)
                        bytes[i] = (byte) array.getInt(i);
                    value = bytes;
                }
            }

            map.put(key, value);
        }
        return map;
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    public void verifyPhoneNumber(final CallbackContext callbackContext, final String number, final int timeOutDuration) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        @Override
                        public void onVerificationCompleted(PhoneAuthCredential credential) {
                            // This callback will be invoked in two situations:
                            // 1 - Instant verification. In some cases the phone number can be instantly
                            //     verified without needing to send or enter a verification code.
                            // 2 - Auto-retrieval. On some devices Google Play services can automatically
                            //     detect the incoming verification SMS and perform verificaiton without
                            //     user action.
                            Log.d(TAG, "success: verifyPhoneNumber.onVerificationCompleted - doing nothing. sign in with token from onCodeSent");

                            // does this fire in cordova?
                            // TODO: return credential
                        }

                        @Override
                        public void onVerificationFailed(FirebaseException e) {
                            // This callback is invoked in an invalid request for verification is made,
                            // for instance if the the phone number format is not valid.
                            Log.w(TAG, "failed: verifyPhoneNumber.onVerificationFailed ", e);

                            String errorMsg = "unknown error verifying number";
                            errorMsg += " Error instance: " + e.getClass().getName();
                            errorMsg += " Error code: " + ((FirebaseAuthException)e).getErrorCode().toString();

                            if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                // Invalid request
                                errorMsg = "Invalid phone number";
                            } else if (e instanceof FirebaseTooManyRequestsException) {
                                // The SMS quota for the project has been exceeded
                                errorMsg = "The SMS quota for the project has been exceeded";
                            }

                            callbackContext.error(errorMsg);
                        }

                        @Override
                        public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken token) {
                            // The SMS verification code has been sent to the provided phone number, we
                            // now need to ask the user to enter the code and then construct a credential
                            // by combining the code with a verification ID [(in app)].
                            Log.d(TAG, "success: verifyPhoneNumber.onCodeSent");

                            JSONObject returnResults = new JSONObject();
                            try {
                                returnResults.put("verificationId", verificationId);
                                //returnResults.put("forceResendingToken", token); // TODO: return forceResendingToken
                            } catch (JSONException e) {
                                callbackContext.error(e.getMessage());
                                return;
                            }
                            PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, returnResults);
                            pluginresult.setKeepCallback(true);
                            callbackContext.sendPluginResult(pluginresult);
                        }
                    };

                    PhoneAuthProvider.getInstance().verifyPhoneNumber(
                            number,                 // Phone number to verify
                            timeOutDuration,        // Timeout duration
                            TimeUnit.SECONDS,       // Unit of timeout
                            cordova.getActivity(),  // Activity (for callback binding)
                            mCallbacks);            // OnVerificationStateChangedCallbacks
                    //resentToken);         // The ForceResendingToken obtained from onCodeSent callback
                    // to force re-sending another verification SMS before the auto-retrieval timeout.
                    // TODO: make resendToken accessible


                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    //
    // Firebase Performace
    //

    private HashMap<String,Trace> traces = new HashMap<String,Trace>();

    private void startTrace(final CallbackContext callbackContext, final String name){
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {

                    Trace myTrace = null;
                    if ( self.traces.containsKey(name) ){
                        myTrace = self.traces.get(name);
                    }

                    if ( myTrace == null ){
                        myTrace = FirebasePerformance.getInstance().newTrace(name);
                        myTrace.start();
                        self.traces.put(name, myTrace);
                    }

                    callbackContext.success();
                } catch (Exception e) {
                    FirebaseCrash.log(e.getMessage());
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void incrementCounter(final CallbackContext callbackContext, final String name, final String counterNamed){
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {

                    Trace myTrace = null;
                    if ( self.traces.containsKey(name) ){
                        myTrace = self.traces.get(name);
                    }

                    if ( myTrace != null && myTrace instanceof Trace ){
                        myTrace.incrementCounter(counterNamed);
                        callbackContext.success();
                    }else{
                        callbackContext.error("Trace not found");
                    }

                } catch (Exception e) {
                    FirebaseCrash.log(e.getMessage());
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void stopTrace(final CallbackContext callbackContext, final String name){
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {

                    Trace myTrace = null;
                    if ( self.traces.containsKey(name) ){
                        myTrace = self.traces.get(name);
                    }

                    if ( myTrace != null && myTrace instanceof Trace ){ //
                        myTrace.stop();
                        self.traces.remove(name);
                        callbackContext.success();
                    }else{
                        callbackContext.error("Trace not found");
                    }

                } catch (Exception e) {
                    FirebaseCrash.log(e.getMessage());
                    e.printStackTrace();
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }
//-------------------------------------------------Twilio Vioce Start---------------------------------------------------------------------------

    private void call(final JSONArray arguments, final CallbackContext callbackContext) {
        Log.d(TAG,"call");
            if(cordova.hasPermission(RECORD_AUDIO))
                    {
                            cordova.getThreadPool().execute(new Runnable(){
                                public void run() {
                                    String accessToken = arguments.optString(0,mAccessToken);
                                    JSONObject options = arguments.optJSONObject(1);
                                    Map<String, String> map = getMap(options);
                                    if (mCall != null && mCall.getState().equals(CallState.CONNECTED)) {
                                        mCall.disconnect();
                                    }
                                    showNotification(map.get("to_number").toString(),callbackContext);
                                    mCall = Voice.call(cordova.getActivity(),accessToken, map, mCallListener);
                                    Log.d(TAG, "Placing call with params: " + map.toString());
                                }
                            });
                    }
                    else
                    {
                        cordova.requestPermission(this, RECORD_AUDIO_REQ_CODE, RECORD_AUDIO);
                    }

    
        
    }



// helper method to get a map of strings from a JSONObject
    public Map<String, String> getMap(JSONObject object) {
        if (object == null) {
            return null;
        }

        Map<String, String> map = new HashMap<String, String>();

        @SuppressWarnings("rawtypes")
        Iterator keys = object.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            map.put(key, object.optString(key));
        }
        return map;
    }
    
    // helper method to get a JSONObject from a Map of Strings
    public JSONObject getJSONObject(Map<String, String> map) throws JSONException {
        if (map == null) {
            return null;
        }

        JSONObject json = new JSONObject();
        for (String key : map.keySet()) {
            json.putOpt(key, map.get(key));
        }
        return json;
    }

/*
     * Accept an incoming Call
     */
    private void acceptCallInvite(JSONArray arguments, final CallbackContext callbackContext) {
        Log.d(TAG,"acceptCallInvite");
        if (mCallInvite == null) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCallInvite.accept(cordova.getActivity(),mCallListener);
                callbackContext.success(); 
            }
        });
        
    }
    
    private void rejectCallInvite(JSONArray arguments, final CallbackContext callbackContext) {
        Log.d(TAG,"rejectCallInvite");
        if (mCallInvite == null) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCallInvite.reject(cordova.getActivity());
                callbackContext.success(); 
            }
        });
    }



     /*
     * Disconnect from Call
     */
    private void disconnect(JSONArray arguments, final CallbackContext callbackContext) {
        Log.d(TAG,"disconnect");
        if (mCall == null) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCall.disconnect();
                callbackContext.success(); 
            }
        });
    }

    private void sendDigits(final JSONArray arguments,
            final CallbackContext callbackContext) {
        Log.d(TAG,"sendDigits"+arguments.optString(0));
        if (arguments == null || arguments.length() < 1 || mCall == null) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCall.sendDigits(arguments.optString(0));
                callbackContext.success(); 
            }
        });
        
    }
    
    private void checkMicrophonePermission(final CallbackContext callbackContext) {
        Log.d(TAG,"checkMicrophonePermission");
        if(cordova.hasPermission(RECORD_AUDIO))
        {
            PluginResult result = new PluginResult(PluginResult.Status.OK,true);
            callbackContext.sendPluginResult(result);
            return;
        }else{
            PluginResult result = new PluginResult(PluginResult.Status.OK,false);
            callbackContext.sendPluginResult(result);
            return;
        }
    }


    private void muteCall(final CallbackContext callbackContext) {
        if (mCall == null) {
            Log.d("muteCall", "mCall == null");
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCall.mute(true);
                callbackContext.success(); 
            }
        });
    }

    private void unmuteCall(final CallbackContext callbackContext) {
        if (mCall == null) {
            Log.d("unmuteCall", "mCall == null");
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                mCall.mute(false);
                callbackContext.success(); 
            }
        });
    }

    private void isCallMuted(CallbackContext callbackContext) {
        if (mCall == null) {
            Log.d("isCallMuted", "mCall == null");
            callbackContext.sendPluginResult(new PluginResult(
                PluginResult.Status.OK,false));
            return;
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK,mCall.isMuted());
        callbackContext.sendPluginResult(result);
    }


    private void callStatus(CallbackContext callbackContext) {
        Log.d(TAG,"callStatus");
        if (mCall == null) {
            Log.d("callStatus", "mCall == null");
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.ERROR));
            return;
        }
        String state = getCallState(mCall.getState());
        if (state == null) {
            state = "";
        }
        PluginResult result = new PluginResult(PluginResult.Status.OK,state);
        callbackContext.sendPluginResult(result);
    }


    
    private void showNotification(String To_number, CallbackContext context) {
        Log.d(TAG,"showNotification");
        Context acontext = FirebasePlugin.this.webView.getContext();
        NotificationManager mNotifyMgr = 
                (NotificationManager) acontext.getSystemService(Activity.NOTIFICATION_SERVICE);
        mNotifyMgr.cancelAll();
        
        
        PackageManager pm = acontext.getPackageManager();
        Intent notificationIntent = pm.getLaunchIntentForPackage(acontext.getPackageName());
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("notificationTag", "BVNotification");
        
        PendingIntent pendingIntent = PendingIntent.getActivity(acontext, 0, notificationIntent, 0);  
        int notification_icon = acontext.getResources().getIdentifier("sabro_icon", "drawable", acontext.getPackageName());
        Log.d("SaBRO" ,"notification_icon : " + notification_icon); 
        Log.d("SaBRO" ,"package name : " + acontext.getPackageName());  

       

NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(acontext)
                .setSmallIcon(notification_icon)
                .setContentTitle("Outgoing Call")
                .setContentText(To_number)
                .setContentIntent(pendingIntent);
        mNotifyMgr.notify(mCurrentNotificationId, mBuilder.build());
        
        context.success();
    }
    
    private void cancelNotification(JSONArray arguments, CallbackContext context) {
        Log.d(TAG,"cancelNotification");
        NotificationManager mNotifyMgr = 
                (NotificationManager) FirebasePlugin.this.webView.getContext().getSystemService(Activity.NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(mCurrentNotificationId);
        context.success();
    }
    
    /**
     *  Changes sound from earpiece to speaker and back
     * 
     *  @param mode Speaker Mode
     * */
    public void setSpeaker(final JSONArray arguments, final CallbackContext callbackContext) {
        Log.d(TAG,"setSpeaker");
        cordova.getThreadPool().execute(new Runnable(){
            public void run() {
                Context context = cordova.getActivity().getApplicationContext();
                AudioManager m_amAudioManager;
                m_amAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                String mode = arguments.optString(0);
                if(mode.equals("on")) {
                    Log.d(TAG, "SPEAKER");
                    m_amAudioManager.setMode(AudioManager.MODE_NORMAL);
                    m_amAudioManager.setSpeakerphoneOn(true);           
                }
                else {
                    Log.d(TAG, "EARPIECE");
                    m_amAudioManager.setMode(AudioManager.MODE_IN_CALL); 
                    m_amAudioManager.setSpeakerphoneOn(false);
                }
            }
        });
    }


    // Plugin-to-Javascript communication methods
    private void javascriptCallback(String event, JSONObject arguments,
            CallbackContext callbackContext) {
        if (callbackContext == null) {
            return;
        }
        JSONObject options = new JSONObject();
        try {
            options.putOpt("callback", event);
            options.putOpt("arguments", arguments);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.JSON_EXCEPTION));
            return;
        }
        PluginResult result = new PluginResult(Status.OK, options);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);

    }

    private void javascriptCallback(String event,
            CallbackContext callbackContext) {
        javascriptCallback(event, null, callbackContext);
    }

    
    private void javascriptErrorback(int errorCode, String errorMessage, CallbackContext callbackContext) {
        JSONObject object = new JSONObject();
        try {
            object.putOpt("message", errorMessage);
        } catch (JSONException e) {
            callbackContext.sendPluginResult(new PluginResult(
                    PluginResult.Status.JSON_EXCEPTION));
            return;
        }
        PluginResult result = new PluginResult(Status.ERROR, object);
        result.setKeepCallback(true);
        callbackContext.sendPluginResult(result);
    }

    private void fireDocumentEvent(String eventName) {
        Log.d(TAG,"fireDocumentEvent");
        if (eventName != null) {
            javascriptCallback(eventName,mInitCallbackContext);
        }
    }


public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        Log.d(TAG,"onRequestPermissionResult");
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                mInitCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Permission denied"));
                return;
            }
            else if(r == PackageManager.PERMISSION_GRANTED)
            {
                mInitCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "Permission granted"));
                return;
            }
        }
        switch(requestCode)
        {
            case RECORD_AUDIO_REQ_CODE:
              //  registerForCallInvites();
                break;
        }
    }

// Process incoming call invites
    private void handleIncomingCallIntent(Intent intent) {
     if (intent != null && intent.getAction() != null) {
                 if (intent.getAction() == ACTION_INCOMING_CALL) {
                     mCallInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
                  if (mCallInvite != null && (mCallInvite.getState() == CallInvite.State.PENDING)) {
                      //  SoundPoolManager.getInstance(cordova.getActivity()).playRinging();
                        
                        NotificationManager mNotifyMgr = (NotificationManager) cordova.getActivity().getSystemService(Activity.NOTIFICATION_SERVICE);
                        mNotifyMgr.cancel(intent.getIntExtra(INCOMING_CALL_NOTIFICATION_ID, 0));
                        JSONObject callInviteProperties = new JSONObject();
                        try {
                            callInviteProperties.putOpt("from", mCallInvite.getFrom());
                            callInviteProperties.putOpt("to", mCallInvite.getTo());
                            callInviteProperties.putOpt("callSid", mCallInvite.getCallSid());
                            String callInviteState = getCallInviteState(mCallInvite.getState());
                            callInviteProperties.putOpt("state",callInviteState);
                        } catch (JSONException e) {
                            Log.e(TAG,e.getMessage(),e);
                        }
                        Log.d(TAG,"oncallinvitereceived");
                        javascriptCallback("oncallinvitereceived", callInviteProperties, mInitCallbackContext);
                     } else {                    
                      //      SoundPoolManager.getInstance(cordova.getActivity()).stopRinging();
                            javascriptCallback("oncallinvitecanceled",mInitCallbackContext); 
                      }
                 } 
              }
    }

    // Twilio Voice Call Listener
    private Call.Listener mCallListener = new Call.Listener() {
        @Override
        public void onConnected(Call call) {
        Log.d(TAG,"onConnected");
            mCall = call;

            JSONObject callProperties = new JSONObject();
            try {
                callProperties.putOpt("from", call.getFrom());
                callProperties.putOpt("to", call.getTo());
                callProperties.putOpt("callSid", call.getSid());
                callProperties.putOpt("isMuted", call.isMuted());
                String callState = getCallState(call.getState());
                callProperties.putOpt("state",callState);
            } catch (JSONException e) {
                Log.e(TAG,e.getMessage(),e);
            }
            javascriptCallback("oncalldidconnect",callProperties,mInitCallbackContext);
        }

        /*
        The call failed to connect.
        */

        @Override
        public void onConnectFailure(Call call, CallException exception) {
        Log.d(TAG,"onConnectFailure");

            mCall = null;
            NotificationManager mNotifyMgr = (NotificationManager) FirebasePlugin.this.webView.getContext().getSystemService(Activity.NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(mCurrentNotificationId);
            javascriptErrorback(exception.getErrorCode(), exception.getMessage(), mInitCallbackContext);
        }


        /*
        The call was disconnected.
        */

        @Override
        public void onDisconnected(Call call, CallException exception) {
        Log.d(TAG,"onDisconnected");
            mCall = null;
            NotificationManager mNotifyMgr = (NotificationManager) FirebasePlugin.this.webView.getContext().getSystemService(Activity.NOTIFICATION_SERVICE);
        mNotifyMgr.cancel(mCurrentNotificationId);
            javascriptCallback("oncalldiddisconnect",mInitCallbackContext);
        }
    };

    private String getCallState(CallState callState) {
        if (callState == CallState.CONNECTED) {
            return "connected";
        } else if (callState == CallState.CONNECTING) {
            return "connecting";
        } else if (callState == CallState.DISCONNECTED) {
            return "disconnected";
        }
        return null;
    }

    private String getCallInviteState(CallInvite.State state) {
        if (state == CallInvite.State.PENDING) {
            return "pending";
        } else if (state == CallInvite.State.ACCEPTED) {
            return "accepted";
        } else if (state == CallInvite.State.REJECTED) {
            return "rejected";
        } else if (state == CallInvite.State.CANCELED) {
            return "canceled";
        }

        return null;
    }
//-------------------------------------------------Twilio Vioce End---------------------------------------------------------------------------

}
