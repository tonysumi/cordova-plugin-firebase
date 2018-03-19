var exec = require('cordova/exec');

exports.getInstanceId = function(success, error) {
    exec(success, error, "FirebasePlugin", "getInstanceId", []);
};

exports.getId = function(success, error) {
  exec(success, error, "FirebasePlugin", "getId", []);
};

exports.getToken = function(success, error) {
    exec(success, error, "FirebasePlugin", "getToken", []);
};

exports.onNotificationOpen = function(success, error) {
    exec(success, error, "FirebasePlugin", "onNotificationOpen", []);
};

exports.onTokenRefresh = function(success, error) {
    exec(success, error, "FirebasePlugin", "onTokenRefresh", []);
};

exports.grantPermission = function(success, error) {
    exec(success, error, "FirebasePlugin", "grantPermission", []);
};

exports.hasPermission = function(success, error) {
    exec(success, error, "FirebasePlugin", "hasPermission", []);
};

exports.setBadgeNumber = function(number, success, error) {
    exec(success, error, "FirebasePlugin", "setBadgeNumber", [number]);
};

exports.getBadgeNumber = function(success, error) {
    exec(success, error, "FirebasePlugin", "getBadgeNumber", []);
};

exports.subscribe = function(topic, success, error) {
    exec(success, error, "FirebasePlugin", "subscribe", [topic]);
};

exports.unsubscribe = function(topic, success, error) {
    exec(success, error, "FirebasePlugin", "unsubscribe", [topic]);
};

exports.unregister = function(success, error) {
    exec(success, error, "FirebasePlugin", "unregister", []);
};

exports.logEvent = function(name, params, success, error) {
    exec(success, error, "FirebasePlugin", "logEvent", [name, params]);
};

exports.logError = function(message, success, error) {
    exec(success, error, "FirebasePlugin", "logError", [message]);
};

exports.setScreenName = function(name, success, error) {
    exec(success, error, "FirebasePlugin", "setScreenName", [name]);
};

exports.setUserId = function(id, success, error) {
    exec(success, error, "FirebasePlugin", "setUserId", [id]);
};

exports.setUserProperty = function(name, value, success, error) {
    exec(success, error, "FirebasePlugin", "setUserProperty", [name, value]);
};

exports.activateFetched = function (success, error) {
    exec(success, error, "FirebasePlugin", "activateFetched", []);
};

exports.fetch = function (cacheExpirationSeconds, success, error) {
    var args = [];
    if (typeof cacheExpirationSeconds === 'number') {
        args.push(cacheExpirationSeconds);
    } else {
        error = success;
        success = cacheExpirationSeconds;
    }
    exec(success, error, "FirebasePlugin", "fetch", args);
};

exports.getByteArray = function (key, namespace, success, error) {
    var args = [key];
    if (typeof namespace === 'string') {
        args.push(namespace);
    } else {
        error = success;
        success = namespace;
    }
    exec(success, error, "FirebasePlugin", "getByteArray", args);
};

exports.getValue = function (key, namespace, success, error) {
    var args = [key];
    if (typeof namespace === 'string') {
        args.push(namespace);
    } else {
        error = success;
        success = namespace;
    }
    exec(success, error, "FirebasePlugin", "getValue", args);
};

exports.getInfo = function (success, error) {
    exec(success, error, "FirebasePlugin", "getInfo", []);
};

exports.setConfigSettings = function (settings, success, error) {
    exec(success, error, "FirebasePlugin", "setConfigSettings", [settings]);
};

exports.setDefaults = function (defaults, namespace, success, error) {
    var args = [defaults];
    if (typeof namespace === 'string') {
        args.push(namespace);
    } else {
        error = success;
        success = namespace;
    }
    exec(success, error, "FirebasePlugin", "setDefaults", args);
};

exports.startTrace = function (name, success, error) {
    exec(success, error, "FirebasePlugin", "startTrace", [name]);
};

exports.incrementCounter = function (name, counterNamed, success, error) {
    exec(success, error, "FirebasePlugin", "incrementCounter", [name, counterNamed]);
};

exports.registerForCallInvites = function (accessToken, success, error) {
    exec(success, error, "FirebasePlugin", "registerForCallInvites", [accessToken]);
};

exports.stopTrace = function (name, success, error) {
    exec(success, error, "FirebasePlugin", "stopTrace", [name]);
};

(function() {
    var delegate = {}
    var TwilioPlugin = {

        TwilioVoiceClient: function() {
            return this;
        }
    }

    TwilioPlugin.TwilioVoiceClient.prototype.checkMicrophonePermission  = function(fn) {
        Cordova.exec(fn,null,"FirebasePlugin","checkMicrophonePermission",null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.call = function(token, params) {
        Cordova.exec(null,null,"FirebasePlugin","call",[token, params]);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.sendDigits = function(digits) {
        Cordova.exec(null,null,"FirebasePlugin","sendDigits",[digits]);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.disconnect = function() {
        Cordova.exec(null,null,"FirebasePlugin","disconnect",null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.rejectCallInvite = function() {
        Cordova.exec(null,null,"FirebasePlugin","rejectCallInvite",null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.acceptCallInvite = function() {
        Cordova.exec(null,null,"FirebasePlugin","acceptCallInvite",null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.setSpeaker = function(mode) {
        // "on" or "off"        
        Cordova.exec(null, null, "FirebasePlugin", "setSpeaker", [mode]);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.muteCall = function() {
        Cordova.exec(null, null, "FirebasePlugin", "muteCall", null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.unmuteCall = function() {
        Cordova.exec(null, null, "FirebasePlugin", "unmuteCall", null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.isCallMuted = function(fn) {
        Cordova.exec(fn, null, "FirebasePlugin", "isCallMuted", null);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.initialize = function(token) {

        var error = function(error) {
            //TODO: Handle errors here
            if(delegate['onerror']) delegate['onerror'](error)
        }

        var success = function(callback) {
            var argument = callback['arguments'];
            if (delegate[callback['callback']]) delegate[callback['callback']](argument);
        }

        Cordova.exec(success,error,"FirebasePlugin","initializeWithAccessToken",[token]);
    }

    TwilioPlugin.TwilioVoiceClient.prototype.error = function(fn) {
        delegate['onerror'] = fn;
    }

    TwilioPlugin.TwilioVoiceClient.prototype.clientinitialized = function(fn) {
        delegate['onclientinitialized'] = fn;
    }


    TwilioPlugin.TwilioVoiceClient.prototype.callinvitereceived = function(fn) {
        delegate['oncallinvitereceived'] = fn;
    }

    TwilioPlugin.TwilioVoiceClient.prototype.callinvitecanceled = function(fn) {
        delegate['oncallinvitecanceled'] = fn;
    }

    TwilioPlugin.TwilioVoiceClient.prototype.calldidconnect = function(fn) {
        delegate['oncalldidconnect'] = fn;
    }

    TwilioPlugin.TwilioVoiceClient.prototype.calldiddisconnect = function(fn) {
        delegate['oncalldiddisconnect'] = fn;
    }

    TwilioPlugin.install = function() {
        if (!window.Twilio) window.Twilio = {};
        if (!window.Twilio.TwilioVoice) window.Twilio.TwilioVoice = new TwilioPlugin.TwilioVoiceClient();
    }
 TwilioPlugin.install();

})()