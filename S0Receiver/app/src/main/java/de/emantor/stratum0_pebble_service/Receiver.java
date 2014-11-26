package de.emantor.stratum0_pebble_service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.StrictMode;
import android.util.Log;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import com.jcraft.jsch.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

/**
 * Created by phoenix on 24.11.14.
 */
public class Receiver extends BroadcastReceiver {
    private static final UUID PEBBLE_APP_UUID = UUID.fromString("62f12f0f-0de8-408e-bf67-08074cb4188d");

    private static final int SPACE_STATUS = 0;
    private static final int SPACE_OPENER = 1;
    private static final int REQUEST_DATA = 2;

    private static final String TAG = "S0Receiver";
    private static final String url = "http://status.stratum0.org/status.json";

    private boolean isOpen;
    private String since;
    private String openedBy;

    private static String getStatusFromJSON() {
        String result = "";
        DefaultHttpClient client = new DefaultHttpClient();
        try {
            HttpResponse response = client.execute(new HttpGet(url));
            if (response.getStatusLine().getStatusCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line;
                while ((line = br.readLine()) != null) {
                    result += line;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception " + e);
        }
        return result;
    }

    public void update() throws ParseException {
        try {
            JSONObject jsonObject = new JSONObject(getStatusFromJSON());
            since = jsonObject.getString("since");
            isOpen = jsonObject.getBoolean("isOpen");
            openedBy = jsonObject.getString("openedBy");
        } catch (JSONException e) {
            throw new ParseException(e);
        }
    }

    private boolean isConnectedTo(Context context,String t) {

        try {
            WifiManager wifiManager = (WifiManager) context
                    .getSystemService(context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            if (wifiInfo.getSSID().contains(t))
                return true;
        } catch (Exception a) {
        }

        return false;

    }

    private void setSpace(Context context,String zustand){
        if(isConnectedTo(context,"Stratum0") || isConnectedTo(context,"Stratum0_5G")) {
            try {
                JSch jsch = new JSch();
                jsch.addIdentity("/mnt/sdcard/stratum0", "");
                Session session = jsch.getSession(zustand, "powerberry.fritz.box", 22);
                session.setConfig("PreferredAuthentications", "publickey");
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();
                Channel channel = null;
                channel = (ChannelExec) session.openChannel("exec");
                channel.setInputStream(null);
                InputStream stdout = channel.getInputStream();
                InputStream stderr = channel.getExtInputStream();
                channel.connect();

                while (channel.getExitStatus() == -1){
                    try{Thread.sleep(1000);}catch(Exception e){System.out.println(e);}
                }

                channel.disconnect();
                session.disconnect();

            } catch (Exception e) {
                Log.e(TAG, "Error, could not open Space");
            }
        }
        else {
            Log.i(TAG, "Wrong Wifi");
        }
    }

    private void sendStatus(Context context){
        try {
            update();
            int status = 0;
            PebbleDictionary send = new PebbleDictionary();
            send.addString(SPACE_OPENER,openedBy);
            if(isOpen){ status = 1;}
            else { status = 0;}
            send.addInt8(SPACE_STATUS,(byte) status);
            PebbleKit.sendDataToPebble(context,PEBBLE_APP_UUID,send);

        } catch (ParseException e) {
            Log.w(TAG, "Exception " + e);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
        if (intent.getAction().equals(Constants.INTENT_APP_RECEIVE)) {
            final UUID receivedUuid = (UUID) intent.getSerializableExtra(Constants.APP_UUID);

            // Pebble-enabled apps are expected to be good citizens and only inspect broadcasts containing their UUID
            if (!PEBBLE_APP_UUID.equals(receivedUuid)) {
                Log.i(TAG, "not my UUID");
                return;
            }

            final int transactionId = intent.getIntExtra(Constants.TRANSACTION_ID, -1);
            final String jsonData = intent.getStringExtra(Constants.MSG_DATA);
            if (jsonData == null || jsonData.isEmpty()) {
                Log.i(TAG, "null");
                return;
            }
            final PebbleDictionary data;

            try {
                data = PebbleDictionary.fromJson(jsonData);
                // do what you need with the data
                PebbleKit.sendAckToPebble(context, transactionId);

            } catch (JSONException e) {
                Log.i(TAG, "failed reived -> dict" + e);
                return;
            }
            if(data.getInteger(REQUEST_DATA)==1){
                sendStatus(context);
            } else if (data.getInteger(REQUEST_DATA)==2){
                setSpace(context,"auf");
            } else if (data.getInteger(REQUEST_DATA)==3){
                setSpace(context,"zu");
            }
        }
    }
}

class ParseException extends Exception {
    public ParseException(Throwable e) {
        super(e);
    }
}