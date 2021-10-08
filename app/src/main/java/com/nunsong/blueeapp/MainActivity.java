package com.nunsong.blueeapp;

import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import android.widget.TextView;
import android.widget.Toast;

import com.nunsong.service.BleService;

import org.jetbrains.annotations.NotNull;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends Activity implements View.OnClickListener  {

    // false 변경시 BLE 연결 없이 start 버튼 누르면
    // tensorflow lite(doCorrection()) 실행 가능
    // true 변경시 BLE 연결 후 센서 값 출력
    public static boolean isBLEOk = true;

    public static Button backConnectBtn;
    public static Button fsrConnectBtn;
    public static Button startBtn;
    public static Button stopBtn;

    public Activity mainActivity;

    private Chronometer mChronometer;
    private long timeWhenStopped = 0;
    private boolean stopClicked;

    private static TextView comment;

    //notification, interval
    private static final int Interval = 60;
    NotificationCompat.Builder mBuilder;
    private Timer timer;

    private boolean mIsBound;
    private Messenger mClientCallback = new Messenger(new CallbackHandler());
    String result = ""; // 센서 값
    private final static String TAG = "Main";

    private Messenger mServiceCallback = null;

    public int lastPosture;

    // 틀린 자세 횟수
    int leftCrossCount;
    int rightCrossCount;
    int leftLeanCount;
    int rightLeanCount;
    int frontLeanCount;
    int backLeanCount;
    int totalCount;

    // Dialog
    Dialog leftCrossDialog;
    Dialog rightCrossDialog;
    Dialog frontLeanDialog;
    Dialog backLeanDialog;
    Dialog leftLeanDialog;
    Dialog rightLeanDialog;

    // 알림시 진동
    Vibrator vibrator;


    @Override
    protected void onStart() {
        super.onStart();
        mIsBound = bindService(new Intent(MainActivity.this, BleService.class), mConnection, Context.BIND_AUTO_CREATE);
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

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainActivity = MainActivity.this;

        // 연결, 시작. 종료 버튼
        backConnectBtn = findViewById(R.id.back_connect_button); // 등센서
        fsrConnectBtn = findViewById(R.id.fsr_connect_button); // 방석센서
        startBtn = findViewById(R.id.start_button);
        stopBtn = findViewById(R.id.stop_button);

        // 코멘트 뷰
        comment = findViewById(R.id.commentVal);

        // timer
        mChronometer = (Chronometer) findViewById(R.id.chronometer_view);
        mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener(){
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                long time = SystemClock.elapsedRealtime() - chronometer.getBase();
                String t = longToTime(time);
                chronometer.setText(t);
            }
        });
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.setText("00:00:00");

        //버튼 이벤트 연결
        backConnectBtn.setOnClickListener(this);
        fsrConnectBtn.setOnClickListener(this);
        startBtn.setOnClickListener(this);
        stopBtn.setOnClickListener(this);

        // vibrator 생성
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // 시작시 한번 위치정보 사용 확인
        if (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION"}, 100);
            Toast.makeText(getApplicationContext(), "위치정보 사용을 허가해주세요", Toast.LENGTH_SHORT);
            return;
        }

        // Notification
        final Bitmap mLang = BitmapFactory.decodeResource(getResources(),R.drawable.ba);
        PendingIntent pIntent = PendingIntent.getActivity(MainActivity.this,0,new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);


        mBuilder = new NotificationCompat.Builder(MainActivity.this, "not1")
                .setOngoing(false)
                .setSmallIcon(R.drawable.ba)
                .setContentTitle("움직일 시간입니다")
                .setContentText("10분 정도 움직여 보아요!")
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setLargeIcon(mLang)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pIntent);

        timer = new Timer();


        //Dialog for crossing leg
        leftCrossCount = 0;
        rightCrossCount = 0;
        leftLeanCount = 0;
        rightLeanCount = 0;
        frontLeanCount = 0;
        backLeanCount = 0;


        leftCrossDialog = new Dialog(this);
        leftCrossDialog.setContentView(R.layout.leftcross_dialog);
        WindowManager.LayoutParams lcParams = new WindowManager.LayoutParams();
        lcParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        lcParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        lcParams.gravity = Gravity.CENTER;
        leftCrossDialog.getWindow().setAttributes(lcParams);
        leftCrossDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button leftCrossOK = (Button) leftCrossDialog.findViewById(R.id.leftcrossOK);
        leftCrossOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                leftCrossDialog.dismiss();
            }
        });


        rightCrossDialog = new Dialog(this);
        rightCrossDialog.setContentView(R.layout.rightcross_dialog);
        WindowManager.LayoutParams rcParams = new WindowManager.LayoutParams();
        rcParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        rcParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        rcParams.gravity = Gravity.CENTER;
        rightCrossDialog.getWindow().setAttributes(rcParams);
        rightCrossDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button rightCrossOK = (Button) rightCrossDialog.findViewById(R.id.rightcrossOK);
        rightCrossOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                rightCrossDialog.dismiss();
            }
        });



        frontLeanDialog = new Dialog(this);
        frontLeanDialog.setContentView(R.layout.frontlean_dialog);
        WindowManager.LayoutParams flParams = new WindowManager.LayoutParams();
        flParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        flParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        flParams.gravity = Gravity.CENTER;
        frontLeanDialog.getWindow().setAttributes(flParams);
        frontLeanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button frontLeanOK = (Button) frontLeanDialog.findViewById(R.id.frontleanOK);
        frontLeanOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                frontLeanDialog.dismiss();
            }
        });


        backLeanDialog = new Dialog(this);
        backLeanDialog.setContentView(R.layout.backlean_dialog);
        WindowManager.LayoutParams blParams = new WindowManager.LayoutParams();
        blParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        blParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        blParams.gravity = Gravity.CENTER;
        backLeanDialog.getWindow().setAttributes(blParams);
        backLeanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button backLeanOK = (Button) backLeanDialog.findViewById(R.id.backleanOK);
        backLeanOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                backLeanDialog.dismiss();
            }
        });


        rightLeanDialog = new Dialog(this);
        rightLeanDialog.setContentView(R.layout.rightlean_dialog);
        WindowManager.LayoutParams rlParams = new WindowManager.LayoutParams();
        rlParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        rlParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        rlParams.gravity = Gravity.CENTER;
        rightLeanDialog.getWindow().setAttributes(rlParams);
        rightLeanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button rightLeanOK = (Button) rightLeanDialog.findViewById(R.id.rightleanOK);
        rightLeanOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                rightLeanDialog.dismiss();
            }
        });

        leftLeanDialog = new Dialog(this);
        leftLeanDialog.setContentView(R.layout.leftlean_dialog);
        WindowManager.LayoutParams llParams = new WindowManager.LayoutParams();
        llParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        llParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        llParams.gravity = Gravity.CENTER;
        leftLeanDialog.getWindow().setAttributes(llParams);
        leftLeanDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Button leftLeanOK = (Button) leftLeanDialog.findViewById(R.id.leftleanOK);
        leftLeanOK.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                leftLeanDialog.dismiss();
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();

        // finish app if the BLE is not supported
        if(!getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE ) ) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mIsBound){
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    // time to string
    String longToTime(long time){
        int h = (int)(time /3600000);
        int m = (int)(time - h*3600000)/60000;
        int s = (int)(time - h*3600000- m*60000)/1000 ;
        String t = (h < 10 ? "0"+h: h)+ ":" +(m < 10 ? "0"+m: m)+ ":" + (s < 10 ? "0"+s: s);

        return t;
    }
    
    // 자세 교정 시작
    public void doCorrection(String sensorResult){
        // 인풋
        String[] sensorVals = sensorResult.split(",");


        float[] input1 = new float[]{Float.parseFloat(sensorVals[0])}; // mpu_pitch
        float[] input2 = new float[]{Float.parseFloat(sensorVals[1])}; // mpu_roll
        float[] input3 = new float[]{Float.parseFloat(sensorVals[2])}; // fsr_1
        float[] input4 = new float[]{Float.parseFloat(sensorVals[3])}; // fsr_2
        float[] input5 = new float[]{Float.parseFloat(sensorVals[4])}; // fsr_3
        float[] input6 = new float[]{Float.parseFloat(sensorVals[5])}; // fsr_4
        float[][][][] inputs = new float[][][][]{{{input1, input2, input3, input4, input5, input6}}};

        // 아웃풋
        float[][] output = new float[1][7]; // 7종류
        Map<Integer, Object> outputs = new HashMap<>();
        outputs.put(0, output);

        // 모델을 해석할 인터프리터 생성
        Interpreter tflite = getTfliteInterpreter("1d_cnn_model_modified_modified.tflite");

        // 모델 구동
        // 정확하게는 from_session 함수의 output_tensors 매개변수에 전달된 연산 호출
        tflite.runForMultipleInputsOutputs(inputs, outputs);

        for(int i = 0; i < 7; i++) {
            if(output[0][i] * 100 > 85) {
                if(i == 0){
                    lastPosture = i;
                }else if(i != lastPosture){
                    if (i == 1) { // 오른쪽 다리 꼼
                        rightCrossDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        rightCrossCount++;
                        Log.d("자세", "오른 다리 꼼");
                    } else if (i == 2) { // 왼쪽 다리 꼼
                        leftCrossDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        leftCrossCount++;
                        Log.d("자세", "왼 다리 꼼");
                    } else if (i == 3) { // 오른쪽으로 기움
                        rightLeanDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        rightLeanCount++;
                        Log.d("자세", "오른 기움");
                    } else if (i == 4) { // 왼쪽으로 기움
                        leftLeanDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        leftLeanCount++;
                        Log.d("자세", "왼 기움");
                    } else if (i == 5) { // 앞으로 기움
                        frontLeanDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        frontLeanCount++;
                        Log.d("자세", "앞 기움");
                    } else if (i == 6) { // 뒤로 기움
                        backLeanDialog.show();
                        vibrator.vibrate(500); // 0.5초간 진동
                        backLeanCount++;
                        Log.d("자세", "뒤 기움");
                    }
                    lastPosture = i;
                    totalCount++;
                    comment.setText(totalCount + "회 나쁜 자세를 했어요 분발하세요!");
                }
            } else
                continue;
        }
    }


    @Override
    public void onClick(View view) {
        //버튼 이벤트 처리
        switch (view.getId()){
            case R.id.back_connect_button:
                if (backConnectBtn.getText().equals("등 연결 시작")) {
                    Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
                    intent.putExtra("sensor_type", "back") ;
                    startActivity(intent);
                }else if(backConnectBtn.getText().equals("등 연결 종료") ){
                    ((BluetoothActivity) BluetoothActivity.mContext).disconnect("back");
                    backConnectBtn.setText("등 연결 시작");
                }
                break;
            case R.id.fsr_connect_button:
                if (fsrConnectBtn.getText().equals("방석 연결 시작")) {
                    Intent intent = new Intent(MainActivity.this, BluetoothActivity.class);
                    intent.putExtra("sensor_type", "fsr") ;
                    startActivity(intent);
                }else if(fsrConnectBtn.getText().equals("방석 연결 종료") ){
                    ((BluetoothActivity) BluetoothActivity.mContext).disconnect("fsr");
                    fsrConnectBtn.setText("방석 연결 시작");
                }
                break;
            case R.id.start_button:
                if(isBLEOk == true) {
                    if (backConnectBtn.getText().equals("등 연결 시작") || fsrConnectBtn.getText().equals("방석 연결 시작")) {
                        Toast.makeText(getApplicationContext(), "블루투스를 연결해주세요", Toast.LENGTH_SHORT);
                    }
                }

                leftCrossCount = 0;
                rightCrossCount = 0;
                leftLeanCount = 0;
                rightLeanCount = 0;
                frontLeanCount = 0;
                backLeanCount = 0;
                totalCount = 0;

                lastPosture = 0;

                comment.setText("잘하고 있어요!");

                if(isBLEOk == false){ // 텐서플로우 실험
                    // 테스트할 문자열 지정
                    String[] vals = new String[]{
                            "-1,0,532,511,505,623", // 바른
                            "1,0,438,409,9,626", // 오른 다리
                            "0,0,612,52,296,589", // 왼 다리
                            "0,11,214,187,445,648", // 오른 기움
                            "3,-11,618,422,181,408", // 왼 기움
                            "13,1,543,395,311,534", // 앞 기움
                            "-14,-2,524,389,441,668,7"}; // 뒤 기움

                    int[] idxs = new int[]{0,1,2,3,3,4,5,6};

                    for(int i = 0; i < idxs.length; i++){
                        try {
                            Thread.sleep(3000);
                            Log.d("인덱스", "************   " + i);
                            doCorrection(vals[idxs[i]]);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                mChronometer.setBase(SystemClock.elapsedRealtime() + timeWhenStopped);
                mChronometer.start();
                stopClicked = false;

                NotificationManager notiMan = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if(Build.VERSION.SDK_INT>=36) {
                    notiMan.createNotificationChannel(new NotificationChannel("not1", "채널", NotificationManager.IMPORTANCE_DEFAULT));
                }
                notiMan.notify(1004, mBuilder.build());

                TimerTask addTask = new TimerTask(){
                    @Override
                    public void run(){
                        NotificationManager notiMan = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                        if(Build.VERSION.SDK_INT>=26){
                            notiMan.createNotificationChannel(new NotificationChannel("not1", "채널", NotificationManager.IMPORTANCE_DEFAULT));
                        }
                        notiMan.notify(1004, mBuilder.build());
                    }
                };

                // 60분마다 notification 주는 timer
                timer.schedule(addTask, Interval*(60*1000), Interval*(60*1000));

                if(isBLEOk == true) {
                    // 센서값 화면 출력 시작
                    ((BluetoothActivity) BluetoothActivity.mContext).printData();
                }
                break;
            case R.id.stop_button:
                if (!stopClicked) {
                    if(isBLEOk == true) {
                        // 센서값 화면 출력 종료
                        ((BluetoothActivity) BluetoothActivity.mContext).stopPrintData();
                    }


                    mChronometer.stop();
                    long time = SystemClock.elapsedRealtime() - mChronometer.getBase();
                    String t = longToTime(time);
                    stopClicked = true;
                    Intent intent = new Intent(MainActivity.this,ResultActivity.class);

                    // notification cacel
                    timer.cancel();

                    //두번째 액티비티로 값 보내기
                    intent.putExtra("time", t);
                    intent.putExtra("leftCrossCount", leftCrossCount);
                    intent.putExtra("rightCrossCount", rightCrossCount);
                    intent.putExtra("frontLeanCount", frontLeanCount);
                    intent.putExtra("backLeanCount", backLeanCount);
                    intent.putExtra("leftLeanCount", leftLeanCount);
                    intent.putExtra("rightLeanCount", rightLeanCount);
                    intent.putExtra("totalCount", totalCount);

                    startActivity(intent);
                    break;
                }
        }
    }

    private class CallbackHandler extends Handler {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BleService.PRINT_VALUE:
                    result = msg.getData().getString("str");
                    Log.d("data", "************" + result);
                    doCorrection(result); // 실제 실행시 사용
                    break;
            }
        }
    }

    // 모델 파일 인터프리터를 생성하는 공통 함수
    private Interpreter getTfliteInterpreter(@NotNull String modelPath) {
        try {
            return new Interpreter(loadModelFile(MainActivity.this, modelPath));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 모델을 읽어오는 함수
    public MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

}
