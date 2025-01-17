package com.dmarc.cordovacall;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.os.Handler;
import android.net.Uri;
import java.util.ArrayList;

public class MyConnectionService extends ConnectionService {

    private static final String TAG = "MyConnectionService";
    private static Connection conn;

    public static Connection getConnection() {
        return conn;
    }

    public static void deinitConnection() {
        conn = null;
    }

    @Override
    public Connection onCreateIncomingConnection(final PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(),
                        CordovaCall.getCordova().getActivity().getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                CordovaCall.triggerAnswerCallResponse();
            }

            @Override
            public void onReject() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.REJECTED);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                CordovaCall.triggerRejectCallResponse();
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                assert callbackContexts != null;
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(() -> {
                        PluginResult result = new PluginResult(PluginResult.Status.OK,
                                "hangup event called successfully");
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    });
                }
            }
        };
        connection.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD);
        connection.setAudioModeIsVoip(true);
        connection.setCallerDisplayName(request.getExtras().getString("from"),
                TelecomManager.PRESENTATION_ALLOWED);
        Icon icon = CordovaCall.getIcon();
        if (icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence) "", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("receiveCall");
        assert callbackContexts != null;
        for (final CallbackContext callbackContext : callbackContexts) {
            CordovaCall.getCordova().getThreadPool().execute(() -> {
                PluginResult result = new PluginResult(PluginResult.Status.OK, "receiveCall event called successfully");
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            });
        }
        return connection;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final Connection connection = new Connection() {
            @Override
            public void onAnswer() {
                super.onAnswer();
            }

            @Override
            public void onReject() {
                super.onReject();
            }

            @Override
            public void onAbort() {
                super.onAbort();
            }

            @Override
            public void onDisconnect() {
                DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
                this.setDisconnected(cause);
                this.destroy();
                conn = null;
                ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("hangup");
                assert callbackContexts != null;
                for (final CallbackContext callbackContext : callbackContexts) {
                    CordovaCall.getCordova().getThreadPool().execute(() -> {
                        PluginResult result = new PluginResult(PluginResult.Status.OK,
                                "hangup event called successfully");
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
                    });
                }
            }

            @Override
            public void onStateChanged(int state) {
                if (state == Connection.STATE_DIALING) {
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent intent = new Intent(CordovaCall.getCordova().getActivity().getApplicationContext(),
                                    CordovaCall.getCordova().getActivity().getClass());
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
                        }
                    }, 500);
                }
            }
        };

        connection.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD);
        connection.setAudioModeIsVoip(true);
        connection.setCallerDisplayName(request.getExtras().getString("to"),
                TelecomManager.PRESENTATION_ALLOWED);
        connection.setVideoState(request.getVideoState());
        Icon icon = CordovaCall.getIcon();
        if (icon != null) {
            StatusHints statusHints = new StatusHints((CharSequence) "", icon, new Bundle());
            connection.setStatusHints(statusHints);
        }
        connection.setDialing();
        conn = connection;
        ArrayList<CallbackContext> callbackContexts = CordovaCall.getCallbackContexts().get("sendCall");
        if (callbackContexts != null) {
            for (final CallbackContext callbackContext : callbackContexts) {
                CordovaCall.getCordova().getThreadPool().execute(() -> {
                    PluginResult result = new PluginResult(PluginResult.Status.OK,
                            "sendCall event called successfully");
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                });
            }
        }
        return connection;
    }

    // @Override
    // public void onShowIncomingCallUi() {
    // Intent intent = new
    // Intent(CordovaCall.getCordova().getActivity().getApplicationContext(),
    // CordovaCall.getCordova().getActivity().getClass());
    // intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION |
    // Intent.FLAG_ACTIVITY_NEW_TASK);
    // CordovaCall.getCordova().getActivity().getApplicationContext().startActivity(intent);
    // }

}
