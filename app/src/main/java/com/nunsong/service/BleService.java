package com.nunsong.service;

import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.ParcelUuid;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.nunsong.blueeapp.BluetoothActivity;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BleService extends Service{
    private static final String TAG = "BleService";

    public static final int ACTIVITY_CONNECT = 0;
    public static final int CONNECT = 1;
    public static final int DISCONNECT = 2;
    public static final int PRINT_VALUE = 3;
    public static final int SEND_SCANNED_DEVICE = 4;
    public static final int CONNECT_TO_DEVICE = 5;
    public static final int REQUEST_DATA = 6;
    public static final int STOP_READ = 7;

    public ScanCallback scanCallback;
    public BluetoothLeScanner mBluetoothScanner;
    public Handler scanHandler;
    public Map<String, BluetoothDevice> scanResults;
    public static final long SCAN_PERIOD = 15000;

    public String strBack = "null";
    public String strFsr = "null";
    public BluetoothAdapter mFsrBluetoothAdapter = null;
    public BluetoothAdapter mBackBluetoothAdapter = null;
    public boolean isScanning = false;

    public BluetoothGatt mBackBluetoothGatt;
    public BluetoothGatt mFsrBluetoothGatt;

    private static boolean isConBack = false;
    private static boolean isConFsr = false;

    private String sensorType;

    public static String SERVICE_STRING = "00002220-0000-1000-8000-00805F9B34FB";
    public static UUID UUID_TDCS_SERVICE= UUID.fromString(SERVICE_STRING);
    public static String CHARACTERISTIC_COMMAND_STRING = "00002222-0000-1000-8000-00805F9B34FB";
    public static UUID UUID_CTRL_COMMAND = UUID.fromString( CHARACTERISTIC_COMMAND_STRING );
    public static String CHARACTERISTIC_RESPONSE_STRING = "00002221-0000-1000-8000-00805F9B34FB";
    public static UUID UUID_CTRL_RESPONSE = UUID.fromString( CHARACTERISTIC_RESPONSE_STRING );
    public static String CHARACTERISTIC_DESCRIPTER_STRING = "00002902-0000-1000-8000-00805F9B34FB";

    private Handler toastHandler;
    private Handler readHandler;

    BufferedWriter bw;

    private ArrayList<Messenger> mClientCallbacks = new ArrayList<>();
    final Messenger mMessenger = new Messenger(new CallbackHandler());

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public IBinder onBind(Intent intent) {
        toastHandler = new Handler();
        return mMessenger.getBinder();
    }

    private class ToastRunnable implements Runnable {
        String mText;

        public ToastRunnable(String text) {
            mText = text;
        }

        @Override
        public void run(){
            Toast.makeText(getApplicationContext(), mText, Toast.LENGTH_SHORT).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onDestroy(){
        disconnectGattServer("back");
        disconnectGattServer("fsr");

        super.onDestroy();
    }

    private class CallbackHandler  extends Handler {
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void handleMessage( Message msg ){
            switch(msg.what){
                case ACTIVITY_CONNECT:
                    Log.d(TAG, "Received MSG_CLIENT_CONNECT message from client");
                    mClientCallbacks.add(msg.replyTo);
                    break;
                case CONNECT:
                    sensorType = msg.getData().getString("sensorType");
                    startScan(sensorType);
                    break;
                case DISCONNECT:
                    sensorType = msg.getData().getString("sensorType");
                    disconnectGattServer(sensorType);
                    mClientCallbacks.remove(msg.replyTo);
                    break;
                case CONNECT_TO_DEVICE:
                    sensorType = msg.getData().getString("sensorType");
                    String deviceName = msg.getData().getString("deviceName");
                    connectDevice(sensorType, deviceName);
                    break;
                case REQUEST_DATA:
                    readView();
                    break;
                case STOP_READ:
                    if(readHandler != null){
                        Log.d("loglog", "isStop?");
                        readHandler.removeCallbacksAndMessages(null);
                    }
                    break;
            }
        }
    }

    private void sendMessage(Message msg) {
        for (int i = mClientCallbacks.size() - 1; i >= 0; i--) {
            Messenger messenger = mClientCallbacks.get(i);
            sendMessage(messenger, msg);
        }
    }

    private boolean sendMessage(Messenger messenger, Message msg) {
        boolean success = true;
        try {
            messenger.send(msg);
        } catch (RemoteException e) {
            Log.w(TAG, "Lost connection to client", e);
            success = false;
        }
        return success;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private BluetoothAdapter setSensorType(String sensorType){
        if(sensorType.equals("back")){ // 등센서
            mBackBluetoothAdapter = ((BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            return mBackBluetoothAdapter;
        }else { // fsr 센서
            mFsrBluetoothAdapter = ((BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
            return mFsrBluetoothAdapter;
        }
    }

    // Start BLE scan
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startScan(final String sensorType) {

        BluetoothAdapter mBluetoothAdapter = setSensorType(sensorType);
        toastHandler.post(new ToastRunnable("스캔 중...."));

        // check ble adapter and ble enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            toastHandler.post(new ToastRunnable("블루투스를 켜주세요"));
            if(((BluetoothActivity)BluetoothActivity.mContext) != null) {
                ((BluetoothActivity)BluetoothActivity.mContext).finish();
            }
            return;
        }

        //disconnectGattServer();

        // filter to connect specific devices
        List<ScanFilter> filters= new ArrayList<>();
        ScanFilter scanFilter= new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID_TDCS_SERVICE)).build();
        filters.add(scanFilter);

        //저전력모드로 스캔
        ScanSettings settings= new ScanSettings.Builder().setScanMode( ScanSettings.SCAN_MODE_LOW_POWER).build();

        scanResults = new HashMap<>();
        scanCallback = new BLEScanCallback();

        // start scan
        if(mBluetoothScanner == null) {
            mBluetoothScanner = mBluetoothAdapter.getBluetoothLeScanner();
        }

        mBluetoothScanner.startScan(filters, settings, scanCallback);

        // set scanning flag
        isScanning = true;

        // Stop scanning after SCAN_PERIOD
        scanHandler = new Handler();
        scanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan(sensorType);
            }
        }, SCAN_PERIOD);
    }

    /* Stop scanning */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopScan(String sensorType) {
        Log.d(TAG, "*************STOP SCAN**************");
        if(sensorType.equals("back")){
            if(isScanning && mBackBluetoothAdapter != null
                    && mBackBluetoothAdapter.isEnabled() && mBluetoothScanner != null ) {
                mBluetoothScanner.stopScan(scanCallback);
            }
        }else if(sensorType.equals("fsr")){
            if(isScanning && mFsrBluetoothAdapter != null
                    && mFsrBluetoothAdapter.isEnabled() && mBluetoothScanner != null ) {
                mBluetoothScanner.stopScan(scanCallback);
            }
        }

        scanCallback= null;
        isScanning = false;
        scanHandler = null;
    }

    // Connect to Blueeino
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized void connectDevice(String sensorType, String deviceName){

        BluetoothDevice device = scanResults.get(deviceName);

        // update the status
        GattClientCallback gattClientCb= new GattClientCallback();

        if(sensorType.equals("back")){
            GattClientCallback gattClientCb1= new GattClientCallback();
            mBackBluetoothGatt = device.connectGatt(this, false, gattClientCb1);
            if(mBackBluetoothGatt != null) {
                isConBack = true;
            }
        }else if(sensorType.equals("fsr")){
            GattClientCallback gattClientCb2= new GattClientCallback();
            mFsrBluetoothGatt = device.connectGatt(this, false, gattClientCb2);
            if(mFsrBluetoothGatt != null) {
                isConFsr = true;
            }
        }


        toastHandler.post(new ToastRunnable(device.getName() + "에 연결되었습니다"));
        scanResults.clear();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void readData() {
        // back sensor
        setCharacteristicNotification(mBackBluetoothGatt, findResponseCharacteristic(mBackBluetoothGatt), true);
        // fsr sensor
        setCharacteristicNotification(mFsrBluetoothGatt, findResponseCharacteristic(mFsrBluetoothGatt), true);

    }

    /* Subscription settings. Once you subscribe to notification, you will receive the value */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void setCharacteristicNotification(BluetoothGatt mBluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enabled) {

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // register descriptor
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_DESCRIPTER_STRING));

        descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[] { 0x00, 0x00 });
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    /* Gatt Client Callback class */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private class GattClientCallback extends BluetoothGattCallback{

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState ) {
            super.onConnectionStateChange(gatt, status, newState);

            if(status == BluetoothGatt.GATT_FAILURE) {
                disconnectGattServer(gatt);
                return;
            } else if(status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer(gatt);
                return;
            }if(newState == BluetoothProfile.STATE_CONNECTED) {
                // set the connection flag
                if(gatt == mBackBluetoothGatt){
                    //((MainActivity)MainActivity.mainContext).isBackConnected = true;
                }else if(gatt == mFsrBluetoothGatt){
                    //((MainActivity)MainActivity.mainContext).isFsrConnected = true;
                }

                Log.d( TAG, "Connected to the GATT server" );
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer(gatt);
            }
        }

        @Override
        public synchronized void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            // check if the discovery failed
            if(status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "*****Device service discovery failed, status: " + status);
                return;
            }
            // find discovered characteristics
            List<BluetoothGattCharacteristic> matching_characteristics= findBLECharacteristics(gatt);
            if(matching_characteristics.isEmpty()) {
                Log.e(TAG, "*****Unable to find characteristics" );
                return;
            }
            // log for successful discovery
            Log.d(TAG, "*****Services discovery is successful");
            if(gatt == mFsrBluetoothGatt){
                isConFsr = true;
            }else if(gatt == mBackBluetoothGatt){
                isConBack = true;
            }

            // if both sensors are connected, start read data
            if(isConBack && isConFsr){
                readData();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if(status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic written successfully");
            } else {
                Log.e(TAG, "Characteristic write unsuccessful, status: " + status) ;
                disconnectGattServer(gatt);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d (TAG, "Characteristic read successfully" );
                //readCharacteristic(characteristic);
                byte[] msg= characteristic.getValue();
                String text = new String(msg);
                if(gatt == mBackBluetoothGatt) strBack = text;
                else if(gatt == mFsrBluetoothGatt) strFsr = text;
            } else {
                Log.e( TAG, "Characteristic read unsuccessful, status: " + status);
                disconnectGattServer(gatt); // disconnect when read unsuccessful data
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            //readCharacteristic(characteristic);
            byte[] msg= characteristic.getValue();
            String text = new String(msg);
            if(gatt == mBackBluetoothGatt) strBack = text;
            else if(gatt == mFsrBluetoothGatt) strFsr = text;
        }

        private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
            byte[] msg= characteristic.getValue();
            String text = new String(msg);
            String str = text;
        }
    }

    public boolean getBackState(){
        return isConBack;
    }

    public boolean getFsrState(){
        return isConFsr;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void readView() {
        readHandler = new Handler();
        readHandler.post(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
            @Override
            public void run() {
                try {
                    if (isConBack && isConFsr) {
                        Log.d("data", "%"+ strBack + "," + strFsr);
                        Bundle bundle = new Bundle();
                        bundle.putString("str", strBack + "," + strFsr);
                        Message msg = Message.obtain(null, PRINT_VALUE);
                        msg.setData(bundle);
                        sendMessage(msg);      // send msg to activity
                    }

                    /*
                    // 텍스트 파일 작성 부분
                    try {
                        // 최대 20자. 모든 센서가 3자리수 일 경우 입력이 안됨
                        bw = new BufferedWriter(new FileWriter(getFilesDir() +"/" +"data.csv", true));
                        bw.write(strBack + "," + strFsr);
                        bw.newLine();

                        bw.flush();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                     */
                } catch (NullPointerException e) {

                }

                readHandler.postDelayed(this, 500);
            }
        });
    }

    /* Disconnect Gatt Server */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public synchronized void disconnectGattServer(String sensorType) {

        Log.d( TAG, "@@@@@@@@@@@@@@Closing Gatt connection" );

        toastHandler.post(new ToastRunnable(sensorType + " 연결이 종료되었습니다"));

        if(sensorType.equals("back")){
            // reset the connection flag
            //((MainActivity)MainActivity.mainContext).isBackConnected = false;
            isConBack = false;

            mBackBluetoothGatt.disconnect();
            mBackBluetoothGatt.close();
        }else if(sensorType.equals("fsr")){
            // reset the connection flag
            //((MainActivity)MainActivity.mainContext).isFsrConnected = false;
            isConFsr = false;

            mFsrBluetoothGatt.disconnect();
            mFsrBluetoothGatt.close();
        }
    }

    /* Disconnect Gatt Server */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void disconnectGattServer(BluetoothGatt gatt) {

        Log.d( TAG, "@@@@@@@@@@@@@@Closing Gatt connection" );

        toastHandler.post(new ToastRunnable("연결이 종료되었습니다"));

        if(gatt == mBackBluetoothGatt){
            // reset the connection flag
            isConBack = false;
        }else if(gatt == mFsrBluetoothGatt){
            // reset the connection flag
            isConFsr = false;
        }

        gatt.disconnect();
        gatt.close();

    }

    /* BLE Scan Callback class */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private class BLEScanCallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();

            // add the device to the result list
            scanResults.put(device.getName(), device);

            Bundle bundle = new Bundle();
            bundle.putString("deviceName", device.getName());
            Message msg = Message.obtain(null, SEND_SCANNED_DEVICE);
            msg.setData(bundle);
            sendMessage(msg);      // send msg to activity
        }

        @Override
        public void onScanFailed(int error) {
            Log.e( TAG, "BLE scan failed with code " + error);
        }
    };

    //UUID비교용 함수모음
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static List<BluetoothGattCharacteristic> findBLECharacteristics(BluetoothGatt gatt ) {
        List<BluetoothGattCharacteristic> matching_characteristics = new ArrayList<>();
        List<BluetoothGattService> service_list = gatt.getServices();
        BluetoothGattService service = findGattService(service_list);
        if (service == null) {
            return matching_characteristics;
        }

        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
        for (BluetoothGattCharacteristic characteristic : characteristicList) {
            if (isMatchingCharacteristic(characteristic)) {
                matching_characteristics.add(characteristic);
            }
        }

        return matching_characteristics;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Nullable
    public static BluetoothGattCharacteristic findCommandCharacteristic(BluetoothGatt gatt) {
        return findCharacteristic(gatt, CHARACTERISTIC_COMMAND_STRING);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Nullable
    public static BluetoothGattCharacteristic findResponseCharacteristic(BluetoothGatt gatt) {
        return findCharacteristic(gatt, CHARACTERISTIC_RESPONSE_STRING);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Nullable
    private static BluetoothGattCharacteristic findCharacteristic(BluetoothGatt gatt, String uuidString) {
        List<BluetoothGattService> serviceList= gatt.getServices();
        BluetoothGattService service= findGattService(serviceList);
        if(service == null) {
            return null;
        }

        List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
        for(BluetoothGattCharacteristic characteristic : characteristicList) {
            if(matchCharacteristic(characteristic, uuidString)) {
                return characteristic;
            }
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static boolean matchCharacteristic(BluetoothGattCharacteristic characteristic, String uuidString) {
        if(characteristic == null) {
            return false;
        }
        UUID uuid = characteristic.getUuid();
        return matchUUIDs(uuid.toString(), uuidString);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Nullable
    private static BluetoothGattService findGattService(List<BluetoothGattService> serviceList) {
        for (BluetoothGattService service : serviceList) {
            String serviceUuidString = service.getUuid().toString();
            if (matchServiceUUIDString(serviceUuidString)) {
                return service;
            }
        }
        return null;
    }

    private static boolean matchServiceUUIDString(String serviceUuidString) {
        return matchUUIDs(serviceUuidString, SERVICE_STRING);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static boolean isMatchingCharacteristic(BluetoothGattCharacteristic characteristic ) {
        if(characteristic == null) {
            return false;
        }
        UUID uuid = characteristic.getUuid();
        return matchCharacteristicUUID(uuid.toString());
    }

    private static boolean matchCharacteristicUUID(String characteristicUuidString) {
        return matchUUIDs(characteristicUuidString, CHARACTERISTIC_COMMAND_STRING, CHARACTERISTIC_RESPONSE_STRING );
    }

    private static boolean matchUUIDs(String uuidString, String... matches ) {
        for(String match : matches) {
            if(uuidString.equalsIgnoreCase(match)) {
                return true;
            }
        }
        return false;
    }
}