package com.timid.vibrationlightv23;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Ringtone;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

public class ShakeDetectionService extends IntentService implements SensorEventListener {

    private SensorManager sensorMgr;
    private long lastUpdate = -1;
    private float last_x;
    private float last_y;
    private float last_z;
    private boolean firstShake = false;

    private int flashDuration;
    private double shakeThreshold;

    private PowerManager.WakeLock wl;
    private CameraManager cameraManager;
    private String cameraId;
    private AtomicBoolean flashOn = new AtomicBoolean(false);

    public ShakeDetectionService() {
        super("ShakeDetectionService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //create a notification to run foreground service
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("Vibration Flashlight Active")
                .setContentText("Shake to activate")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setTicker("")
                .build();
        startForeground(1102, notification);

        //sets up
        flashDuration = intent.getIntExtra(MainActivity.FLASH_DURATION,3000);
        shakeThreshold = intent.getDoubleExtra(MainActivity.VIBRATION_INTENSITY,0.1);

        Log.e("Flash Duration", String.valueOf(flashDuration));

        //acquire wakelock to keep cpu and sensor alive during screen-off
        PowerManager pm = (PowerManager)getApplicationContext().getSystemService(
                getApplicationContext().POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SHAKE_DETECTION_TIMER");
        wl.acquire();

        //register sensor listener
        sensorMgr = (SensorManager) getApplicationContext().getSystemService(SENSOR_SERVICE);
        sensorMgr.registerListener(this
                , sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                , SensorManager.SENSOR_DELAY_NORMAL);

        //initiate time variable for calculating shake speed
        lastUpdate = System.currentTimeMillis();

        //notify user of started service
        Toast.makeText(getApplicationContext(), "Auto Light enabled", Toast.LENGTH_SHORT).show();

        //get camera manager and id
        cameraManager = (CameraManager) getApplicationContext().getSystemService(Context.CAMERA_SERVICE);

        //get camera list and find back-facing camera
        try {
            String[] cameras = cameraManager.getCameraIdList();
            for(String cam: cameras){
                if(cameraManager.getCameraCharacteristics(cam)
                        .get(CameraCharacteristics.LENS_FACING)
                        .equals(CameraCharacteristics.LENS_FACING_BACK)){
                    cameraId = cam;
                }

            }
        }catch(CameraAccessException e){
            //if camera is not available
            Toast.makeText(getApplicationContext(), "Camera Access Error! Please close any other camera app.", Toast.LENGTH_SHORT).show();
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        //handles sensor event
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //get current time for calculating speed
            long curTime = System.currentTimeMillis();
            //check the sensor every 100 milliseconds

            long diffTime = (curTime - lastUpdate);
            if(diffTime > 100){
                //sets the last update time to now
                lastUpdate = curTime;

                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                //check if it is the first time
                if(!firstShake){
                    last_x = x;
                    last_y = y;
                    last_z = z;
                    firstShake = true;
                }

                float dx = x - last_x;
                float dy = y - last_y;
                float dz = z - last_z;

                float speed = ((dx*dx)+(dy*dy)+(dz*dz)) / (diffTime) ;

                if((speed) > shakeThreshold && !flashOn.get()){
                    try {
                        flashOn.set(true);
                        cameraManager.setTorchMode(cameraId, true);
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try{
                                    cameraManager.setTorchMode(cameraId, false);
                                }catch (CameraAccessException e) {
                                    Toast.makeText(getApplicationContext(), "Camera Access Error! Please close any other camera app.", Toast.LENGTH_SHORT).show();
                                }
                                flashOn.set(false);
                            }
                        },flashDuration);
                    }catch(CameraAccessException e){
                        Toast.makeText(getApplicationContext(), "Camera Access Error! Please close any other camera app.", Toast.LENGTH_SHORT).show();
                    }
                }

                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorMgr.unregisterListener(this);
        try {
            cameraManager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            Toast.makeText(getApplicationContext(), "Fail to close flash", Toast.LENGTH_SHORT).show();
        }
        wl.release();
        Toast.makeText(getApplicationContext(), "Auto Light disabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
