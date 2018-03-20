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
        if (!window.Twilio.TwilioVoice) window.Twilio.TwilioVoiceClient = new TwilioPlugin.TwilioVoiceClient();
    }
 TwilioPlugin.install();

})()



