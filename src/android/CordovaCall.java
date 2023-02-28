package com.dmarc.cordovacall;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import android.os.Bundle;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.Manifest;
import android.telecom.Connection;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class CordovaCall extends CordovaPlugin {

    private static final String TAG = "CordovaCall";
    public static final int CALL_PHONE_REQ_CODE = 0;
    public static final int REAL_PHONE_CALL = 1;
    private int permissionCounter = 0;
    private String pendingAction;
    private TelecomManager tm;
    private PhoneAccountHandle handle;
    private PhoneAccount phoneAccount;
    private CallbackContext callbackContext;
    private static String from;
    private String to;
    private String realCallTo;
    private static final HashMap<String, ArrayList<CallbackContext>> callbackContextMap = new HashMap<String, ArrayList<CallbackContext>>();
    private static CordovaInterface cordovaInterface;
    private static Icon icon;
    private static JSONObject payload;
    private static CordovaCall cordovaCallInstance = null;

    public static HashMap<String, ArrayList<CallbackContext>> getCallbackContexts() {
        return callbackContextMap;
    }

    public static CordovaInterface getCordova() {
        return cordovaInterface;
    }

    public static Icon getIcon() {
        return icon;
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        cordovaCallInstance = CordovaCall.this;
        cordovaInterface = cordova;
        super.initialize(cordova, webView);
        String appName = getApplicationName(this.cordova.getActivity().getApplicationContext());
        handle = new PhoneAccountHandle(
                new ComponentName(this.cordova.getActivity().getApplicationContext(), MyConnectionService.class),
                appName);
        tm = (TelecomManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(this.cordova.getActivity().getApplicationContext().TELECOM_SERVICE);
        phoneAccount = new PhoneAccount.Builder(handle, appName)
                .setCapabilities(
                        PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();
        tm.registerPhoneAccount(phoneAccount);

        callbackContextMap.put("answer", new ArrayList<CallbackContext>());
        callbackContextMap.put("reject", new ArrayList<CallbackContext>());
        callbackContextMap.put("hangup", new ArrayList<CallbackContext>());
        callbackContextMap.put("sendCall", new ArrayList<CallbackContext>());
        callbackContextMap.put("receiveCall", new ArrayList<CallbackContext>());
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        this.checkCallPermission();
        this.webView.loadUrl("javascript:document.dispatchEvent(new Event('appEnterForeground'));");
    }

    @Override
    public void onStop() {
        super.onStop();
        this.webView.loadUrl("javascript:document.dispatchEvent(new Event('appEnterBackground'));");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        switch (action) {
            case "receiveCall":
                this.handleReceiveCall(args.getString(0));
                return true;
            case "sendCall": {
                Connection conn = MyConnectionService.getConnection();
                if (conn != null) {
                    if (conn.getState() == Connection.STATE_ACTIVE) {
                        this.callbackContext.error("You can't make a call right now because you're already in a call");
                    } else if (conn.getState() == Connection.STATE_DIALING) {
                        this.callbackContext
                                .error("You can't make a call right now because you're already trying to make a call");
                    } else {
                        this.callbackContext.error("You can't make a call right now");
                    }
                } else {
                    to = args.getString(0);
                    permissionCounter = 2;
                    pendingAction = "sendCall";
                    this.checkCallPermission();
                    /*
                     * cordova.getThreadPool().execute(new Runnable() {
                     * public void run() {
                     * getCallPhonePermission();
                     * }
                     * });
                     */
                }
                return true;
            }
            case "connectCall": {
                Connection conn = MyConnectionService.getConnection();
                if (conn == null) {
                    this.callbackContext.error("No call exists for you to connect");
                } else if (conn.getState() == Connection.STATE_ACTIVE) {
                    this.callbackContext.error("Your call is already connected");
                } else {
                    conn.setActive();
                    Intent intent = new Intent(this.cordova.getActivity().getApplicationContext(),
                            this.cordova.getActivity().getClass());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    this.cordova.getActivity().getApplicationContext().startActivity(intent);
                    this.callbackContext.success("Call connected successfully");
                }
                return true;
            }
            case "endCall": {
                Connection conn = MyConnectionService.getConnection();
                if (conn == null) {
                    this.callbackContext.error("No call exists for you to end");
                } else {
                    DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                    conn.setDisconnected(cause);
                    conn.destroy();
                    MyConnectionService.deinitConnection();
                    ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                    assert callbackContexts != null;
                    for (final CallbackContext cbContext : callbackContexts) {
                        cordova.getThreadPool().execute(new Runnable() {
                            public void run() {
                                PluginResult result = new PluginResult(PluginResult.Status.OK,
                                        "hangup event called successfully");
                                result.setKeepCallback(true);
                                cbContext.sendPluginResult(result);
                            }
                        });
                    }
                    this.callbackContext.success("Call ended successfully");
                }
                return true;
            }
            case "registerEvent":
                String eventType = args.getString(0);
                ArrayList<CallbackContext> callbackContextList = callbackContextMap.get(eventType);
                if (callbackContextList != null) {
                    callbackContextList.add(this.callbackContext);
                }
                return true;
            case "setAppName":
                String appName = args.getString(0);
                handle = new PhoneAccountHandle(new ComponentName(this.cordova.getActivity().getApplicationContext(),
                        MyConnectionService.class), appName);
                phoneAccount = new PhoneAccount.Builder(handle, appName)
                        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                        .build();
                tm.registerPhoneAccount(phoneAccount);
                this.callbackContext.success("App Name Changed Successfully");
                return true;
            case "setIcon":
                String iconName = args.getString(0);
                int iconId = this.cordova.getActivity().getApplicationContext().getResources().getIdentifier(iconName,
                        "drawable", this.cordova.getActivity().getPackageName());
                if (iconId != 0) {
                    icon = Icon.createWithResource(this.cordova.getActivity(), iconId);
                    this.callbackContext.success("Icon Changed Successfully");
                } else {
                    this.callbackContext.error(
                            "This icon does not exist. Make sure to add it to the res/drawable folder the right way.");
                }
                return true;
            case "mute":
                this.mute();
                this.callbackContext.success("Muted Successfully");
                return true;
            case "unmute":
                this.unmute();
                this.callbackContext.success("Unmuted Successfully");
                return true;
            case "speakerOn":
                this.speakerOn();
                this.callbackContext.success("Speakerphone is on");
                return true;
            case "speakerOff":
                this.speakerOff();
                this.callbackContext.success("Speakerphone is off");
                return true;
            case "callNumber":
                realCallTo = args.getString(0);
                if (realCallTo != null) {
                    cordova.getThreadPool().execute(this::callNumberPhonePermission);
                    this.callbackContext.success("Call Successful");
                } else {
                    this.callbackContext.error("Call Failed. You need to enter a phone number.");
                }
                return true;
            case "notification":
                CordovaCall.onReceiveCallNotify(args.getJSONObject(0));
                return true;
            case "requestPermission":
                this.requestPermissionNumbers();
                this.callbackContext.success("requestPermission");
                return true;
        }
        return false;
    }

    private void handleReceiveCall(String callName) {
        Connection conn = MyConnectionService.getConnection();
        if (conn != null) {
            if (conn.getState() == Connection.STATE_ACTIVE) {
                this.callbackContext.error("You can't receive a call right now because you're already in a call");
            } else {
                this.callbackContext.error("You can't receive a call right now");
            }
        } else {
            from = callName;
            permissionCounter = 2;
            pendingAction = "receiveCall";
            this.checkCallPermission();
        }
    }

    private void checkCallPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            String permission = Manifest.permission.READ_PHONE_NUMBERS;
            int res = this.cordova.getActivity().getApplicationContext().checkCallingOrSelfPermission(
                    permission);

            if (res != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissionNumbers();
                return;
            }
        }

        if (permissionCounter >= 1) {
            PhoneAccount currentPhoneAccount = tm.getPhoneAccount(handle);
            if (currentPhoneAccount.isEnabled()) {
                if (Objects.equals(pendingAction, "receiveCall")) {
                    this.receiveCall();
                } else if (Objects.equals(pendingAction, "sendCall")) {
                    this.sendCall();
                }
            } else {
                if (permissionCounter == 2) {
                    Intent phoneIntent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                    phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    this.cordova.getActivity().getApplicationContext().startActivity(phoneIntent);
                } else {
                    this.callbackContext
                            .error("You need to accept phone account permissions in order to send and receive calls");
                }
            }
        }
        permissionCounter--;
    }

    private void receiveCall() {
        Bundle callInfo = new Bundle();
        callInfo.putString("from", from);
        tm.addNewIncomingCall(handle, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Incoming call successful");
    }

    private void sendCall() {
        Uri uri = Uri.fromParts("tel", to, null);
        Bundle callInfoBundle = new Bundle();
        callInfoBundle.putString("to", to);
        Bundle callInfo = new Bundle();
        callInfo.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callInfoBundle);
        callInfo.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        callInfo.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, true);
        if (ActivityCompat.checkSelfPermission(this.cordova.getActivity(),
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this.cordova.getActivity(), new String[] {
                            Manifest.permission.READ_PHONE_NUMBERS,
                            Manifest.permission.MODIFY_AUDIO_SETTINGS,
                            Manifest.permission.RECORD_AUDIO,
                    }, CALL_PHONE_REQ_CODE);
            return;
        }
        tm.placeCall(uri, callInfo);
        permissionCounter = 0;
        this.callbackContext.success("Outgoing call successful");
    }

    private void mute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(true);
    }

    private void unmute() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
    }

    private void speakerOn() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(true);
    }

    private void speakerOff() {
        AudioManager audioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(false);
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    protected void getCallPhonePermission() {
        cordova.requestPermission(this, CALL_PHONE_REQ_CODE, Manifest.permission.CALL_PHONE);
    }

    protected void requestPermissionNumbers() {
        ActivityCompat.requestPermissions(
                this.cordova.getActivity(), new String[] {
                        Manifest.permission.READ_PHONE_NUMBERS,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS,
                        Manifest.permission.RECORD_AUDIO,
                }, 0);
    }

    protected void callNumberPhonePermission() {
        cordova.requestPermission(this, REAL_PHONE_CALL, Manifest.permission.CALL_PHONE);
    }

    private void callNumber() {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", realCallTo, null));
            this.cordova.getActivity().getApplicationContext().startActivity(intent);
        } catch (Exception e) {
            this.callbackContext.error("Call Failed");
        }
        this.callbackContext.success("Call Successful");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
            throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                this.callbackContext
                        .sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "CALL_PHONE Permission Denied"));
                return;
            }
        }
        switch (requestCode) {
            case CALL_PHONE_REQ_CODE:
                this.sendCall();
                break;
            case REAL_PHONE_CALL:
                this.callNumber();
                break;
        }
    }

    public static void triggerAnswerCallResponse() {
        ArrayList<CallbackContext> callbackContexts = callbackContextMap.get("answer");
        JSONObject contact = CordovaCall.getContactActive();
        CordovaCall.payload = null;
        assert callbackContexts != null;
        for (final CallbackContext callbackContext : callbackContexts) {
            cordovaInterface.getThreadPool().execute(() -> {
                PluginResult result = new PluginResult(PluginResult.Status.OK, contact);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            });
        }
    }

    public static void triggerRejectCallResponse() {
        ArrayList<CallbackContext> callbackContexts = callbackContextMap.get("reject");
        JSONObject contact = CordovaCall.getContactActive();
        CordovaCall.payload = null;
        assert callbackContexts != null;
        for (final CallbackContext callbackContext : callbackContexts) {
            cordovaInterface.getThreadPool().execute(() -> {
                PluginResult result = new PluginResult(PluginResult.Status.OK, contact);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            });
        }
    }

    // Receive FCM notification and active incoming call
    // {
    // Caller: {
    // Username: 'Display Name',
    // ConnectionId: 'Unique Call ID'
    // }
    // }
    public static void onReceiveCallNotify(JSONObject payload) {
        try {
            JSONObject callData = payload.getJSONObject("Caller");
            CordovaCall.payload = payload;
            cordovaCallInstance.handleReceiveCall(callData.getString("Username"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject getContactActive() {
        JSONObject json = new JSONObject();
        try {
            json.put("callName", from);
            json.put("payload", payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
}
