package com.example.googlecastpersonalizada;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

public class ActividadPrincipal extends AppCompatActivity {
//    public static final String APP_ID =
//            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
    public static final String APP_ID = "EBD933E1";
    private MediaRouter mediaRouter;
    private MediaRouteSelector mediaRouteSelector;
    public CastDevice selectedDevice;
    private static final int REQUEST_GMS_ERROR = 0;
    private GoogleApiClient apiClient;
    private boolean applicationStarted;
    private static final String TAG = "Googlecast Pers";
    public static final String NAMESPACE =
            "urn:x-cast:com.example.googlecastpersonalizada";
    String sessionId;
    private Button textoButton;
    private Button fondoButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mediaRouter = MediaRouter.getInstance(getApplicationContext());
        mediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent
                        .categoryForCast(APP_ID)).build();
        textoButton = (Button) findViewById(R.id.btn_texto);
        textoButton.setOnClickListener(btnClickListener);
        fondoButton = (Button) findViewById(R.id.btn_fondo);
        fondoButton.setOnClickListener(btnClickListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu, menu);
        MenuItem mediaRouteMenuItem =
                menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(
                        mediaRouteMenuItem);
        mediaRouteActionProvider.setRouteSelector(mediaRouteSelector);
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    protected void onStop() {
        setSelectedDevice(null);
        mediaRouter.removeCallback(mediaRouterCallback);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(
                this);
        if (errorCode != ConnectionResult.SUCCESS) {
            GooglePlayServicesUtil.getErrorDialog(errorCode, this,
                    REQUEST_GMS_ERROR).show();
        }
    }

    @Override
    protected void onPause() {
        disconnectApiClient();
        super.onPause();
    }


    private void setSelectedDevice(CastDevice device) {
        selectedDevice = device;
        if (selectedDevice != null) {
            try {
                stopApplication();
                disconnectApiClient();
                connectApiClient();
            } catch (IllegalStateException e) {
                disconnectApiClient();
            }
        } else {
            disconnectApiClient();
            mediaRouter.selectRoute(mediaRouter.getDefaultRoute());
        }
    }

    private void connectApiClient() {
        Cast.CastOptions apiOptions = Cast.CastOptions.builder(selectedDevice,
                castClientListener).build();
        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptions)
                .addConnectionCallbacks(connectionCallback)
                .addOnConnectionFailedListener(connectionFailedListener)
                .build();
        apiClient.connect();
    }

    private void disconnectApiClient() {
        if (apiClient != null) {
            apiClient.disconnect();
            apiClient = null;
        }
    }

    private void stopApplication() {
        if (apiClient == null) return;
        if (applicationStarted) {
            Cast.CastApi.stopApplication(apiClient);
            applicationStarted = false;
        }
    }

    private final MediaRouter.Callback mediaRouterCallback =
            new MediaRouter.Callback() {
                @Override
                public void onRouteSelected(MediaRouter router,
                                            MediaRouter.RouteInfo route) {
                    CastDevice device = CastDevice.getFromBundle(route.getExtras());
                    setSelectedDevice(device);
                }

                @Override
                public void onRouteUnselected(MediaRouter router,
                                              MediaRouter.RouteInfo route) {
                    stopApplication();
                    setSelectedDevice(null);
                    setSessionStarted(false);
                }
            };
    private final Cast.Listener castClientListener = new Cast.Listener() {
        @Override
        public void onApplicationDisconnected(int statusCode) {
            try {
                Cast.CastApi.removeMessageReceivedCallbacks(apiClient,
                        NAMESPACE);
            } catch (IOException e) {
                Log.w(TAG, "Error al lanzar la aplicación", e);
            }
            setSelectedDevice(null);
            setSessionStarted(false);
        }

        @Override
        public void onVolumeChanged() {
            if (apiClient != null) {
                Log.d(TAG, "Cambio de volumen: " +
                        Cast.CastApi.getVolume(apiClient));
            }
        }
    };
    private final GoogleApiClient.ConnectionCallbacks connectionCallback =
            new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {
                    try {
                        Cast.CastApi.launchApplication(apiClient, APP_ID,
                                false).setResultCallback(connectionResultCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Error al lanzar la aplicación", e);
                    }
                }

                @Override
                public void onConnectionSuspended(int i) {
                    Log.e(TAG, "Connection suspended");
                }
            };
    private final ResultCallback<Cast.ApplicationConnectionResult>
            connectionResultCallback = new
            ResultCallback<Cast.ApplicationConnectionResult>() {
                @Override
                public void onResult(Cast.ApplicationConnectionResult result) {
                    Status status = result.getStatus();
                    if (status.isSuccess()) {
                        applicationStarted = true;
                        sessionId = result.getSessionId();
                        try {
                            Cast.CastApi.setMessageReceivedCallbacks(apiClient, NAMESPACE,
                                    incomingMsgHandler);
                        } catch (IOException e) {
                            Log.e(TAG, "Error mientras se crea el canal.", e);
                        }
                        setSessionStarted(true);
                    } else {
                        setSessionStarted(false);
                    }
                }
            };
    public final Cast.MessageReceivedCallback incomingMsgHandler =
            new Cast.MessageReceivedCallback() {
                @Override
                public void onMessageReceived(CastDevice castDevice,
                                              String namespace, String message) {
                    Log.d(TAG, String.format("mensaje recibido: %s", message));
                }
            };
    private final GoogleApiClient
            .OnConnectionFailedListener connectionFailedListener =
            new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult connectionResult) {
                    setSelectedDevice(null);
                    setSessionStarted(false);
                }
            };

    private void setSessionStarted(boolean enabled) {
        textoButton.setEnabled(enabled);
        fondoButton.setEnabled(enabled);
    }

    private final View.OnClickListener btnClickListener = new
            View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (v.getId()) {
                        case R.id.btn_texto:
                            sendMessage("#T#hola");
                            break;
                        case R.id.btn_fondo:
                            sendMessage("#F#blue");
                            break;
                    }
                }
            };
    private void sendMessage(String message) {
        if (apiClient != null) {
            try {
                Cast.CastApi.sendMessage(apiClient, NAMESPACE, message)
                        .setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status result) {
                                if (!result.isSuccess()) {
                                    Log.e(TAG, "El envío del mensaje ha fallado.");
                                }
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error mientras se enviaba un mensaje", e);
            }
        }
    }
}