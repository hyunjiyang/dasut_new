package com.nunsong.blueeapp;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;

import com.nunsong.service.BleService;

import java.util.ArrayList;


public class BluetoothActivity extends Activity {
    public static Context mContext;

    private ArrayAdapter listViewAdapter;
    private ListView listView;
    ArrayList<String> listMenu;

    private String sensorType;

    public static int REQUEST_FINE_LOCATION= 2;

    private final static String TAG="BLE";

    private Messenger mServiceCallback = null;
    private Messenger mClientCallback = new Messenger(new CallbackHandler());
    private boolean mIsBound;

    /* stop service */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void setStopService() {
        if (mIsBound) {
            getApplicationContext().unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BleService.class);
        getApplicationContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.find_device);
        mContext = BluetoothActivity.this;

        Intent intent = getIntent();
        sensorType = intent.getStringExtra("sensor_type");

        Log.d(TAG, "*********Trying to connect to service");

        listMenu = new ArrayList<String>();
        listViewAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, listMenu);
        listView = (ListView)findViewById(R.id.listView);
        listView.setAdapter(listViewAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String deviceName = (String)parent.getItemAtPosition(position);

                Toast.makeText(getApplicationContext(), deviceName+"에 연결 중....", Toast.LENGTH_SHORT).show();
                if (mIsBound) {
                    if (mServiceCallback != null) {
                        try {
                            Bundle bundle = new Bundle();
                            bundle.putString("sensorType", sensorType);
                            bundle.putString("deviceName", deviceName);
                            Message msg = Message.obtain(null, BleService.CONNECT_TO_DEVICE);
                            msg.setData(bundle);
                            msg.replyTo = mClientCallback;
                            mServiceCallback.send(msg);
                            finish();
                            if(sensorType.equals("back"))
                                MainActivity.backConnectBtn.setText("등 연결 종료");
                            else if(sensorType.equals("fsr"))
                                MainActivity.fsrConnectBtn.setText("방석 연결 종료");
                        } catch (RemoteException e) {
                        }
                    }
                }
            }
        });
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mServiceCallback = new Messenger(service);

            // connect to service
            Message msg = Message.obtain(null, BleService.ACTIVITY_CONNECT);
            msg.replyTo = mClientCallback;
            try {
                mServiceCallback.send(msg);
                Log.d(TAG, "Send ACTIVITY_CONNECT message to Service");
                startScan();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            mServiceCallback = null;
        }
    };

    private class CallbackHandler extends Handler {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BleService.SEND_SCANNED_DEVICE:
                    String deviceName = msg.getData().getString("deviceName");

                    // add to device list view
                    listMenu.add(deviceName);
                    listViewAdapter.notifyDataSetChanged();

                    break;
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void printData(){

        Message msg = Message.obtain(null, BleService.REQUEST_DATA);
        msg.replyTo = mClientCallback;
        try {
            mServiceCallback.send(msg);
            Log.d(TAG, "*******Send REQUEST_DATA message to Service");
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public void stopPrintData(){

        Message msg = Message.obtain(null, BleService.STOP_READ);
        msg.replyTo = mClientCallback;
        try {
            mServiceCallback.send(msg);
            Log.d(TAG, "*******Send STOP_READ message to Service");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void startScan() {
        Log.w(TAG, "&&&&&&&&&&findDevice Start Scan&&&&&&&");

        try {
            listViewAdapter.notifyDataSetChanged();

            // connect to service
            Bundle bundle = new Bundle();
            bundle.putString("sensorType", sensorType);
            Log.w(TAG, "&&&&&&&&&&SensorType "+sensorType+"&&&&&&&");
            Message msg = Message.obtain(null, BleService.CONNECT);
            msg.setData(bundle);
            msg.replyTo = mClientCallback;
            mServiceCallback.send(msg);

            Log.d(TAG, "Send CONNECT message to Service");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void disconnect(String sensorType){
        // connect to service
        try {
            Bundle bundle = new Bundle();
            bundle.putString("sensorType", sensorType);
            Message msg = Message.obtain(null, BleService.DISCONNECT);
            msg.setData(bundle);
            msg.replyTo = mClientCallback;
            mServiceCallback.send(msg);
            Log.d(TAG, "Send DISCONNECT message to Service");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /* Request Fine Location permission */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
    }

}
