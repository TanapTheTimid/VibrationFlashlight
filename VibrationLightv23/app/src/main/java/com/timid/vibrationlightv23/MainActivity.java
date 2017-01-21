package com.timid.vibrationlightv23;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity{

    private ToggleButton toggleButton;
    private SeekBar lightDurationBar;
    private TextView durationIndicator;
    private SeekBar sensitivitySeekBar;
    private RelativeLayout advancedMenu;
    private ToggleButton advancedToggle;

    private int advancedMenuHeight;

    private int flashDuration = 3000;

    public final static String FLASH_DURATION = "FLASH_DURATION";
    public final static String VIBRATION_INTENSITY = "VIBRATION_INTENSITY";
    public final static String CAMERA_MANAGER = "CAMERA_MANAGER";
    public final static String CAMERA_ID = "CAMERA_ID";
    public final static int MAX_FLASH_DURATION = 300000;

    private AtomicBoolean toggleState = new AtomicBoolean(false);

    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //setup all the components of the UI

        toggleButton = (ToggleButton) findViewById(R.id.toggle);
        toggleButton.setChecked(false);
        toggleButton.setTextOn("Auto");

        lightDurationBar = (SeekBar) findViewById(R.id.light_length_slider);
        lightDurationBar.setOnSeekBarChangeListener(new SeekBarChangeListener());

        durationIndicator = (TextView) findViewById(R.id.duration_indicator);
        durationIndicator.setText("3");

        sensitivitySeekBar = (SeekBar) findViewById(R.id.sensitivity_slider);
        sensitivitySeekBar.setMax(10);
        sensitivitySeekBar.setEnabled(true);

        advancedToggle = (ToggleButton) findViewById(R.id.advanced_toggle);
        advancedToggle.setTextOn("Hide advanced options");
        advancedToggle.setTextOff("Show advanced options");
        advancedToggle.setChecked(false);

        advancedMenu = (RelativeLayout) findViewById(R.id.advanced_menu);
        advancedMenuHeight = advancedMenu.getLayoutParams().height;
        advancedMenu.getLayoutParams().height = 0;
        advancedMenu.setVisibility(View.INVISIBLE);
    }



    @Override
    protected void onStart() {
        super.onStart();
        lightDurationBar.setMax(MAX_FLASH_DURATION/1000);
        lightDurationBar.setProgress(0);
        sensitivitySeekBar.setProgress(0);

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
    }


    //house keeping
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(toggleButton.isChecked()) {
            Intent intent = new Intent(this, ShakeDetectionService.class);
            stopService(intent);
        }
    }


    //show advanced menu when clicked
    public void advancedOptions(View view){
        ToggleButton tb = (ToggleButton) view;
        if(tb.isChecked()){
            advancedMenu.getLayoutParams().height = advancedMenuHeight;
            advancedMenu.setVisibility(View.VISIBLE);
            advancedMenu.requestLayout();
        }else{
            advancedMenu.getLayoutParams().height = 0;
            advancedMenu.setVisibility(View.INVISIBLE);
            advancedMenu.requestLayout();
        }
    }

    //handles toggling on - when toggled, start the foreground service
    public void toggleDetection(View view){
        ToggleButton tb = (ToggleButton) view;
        if(!toggleState.get()){
            try{
                cameraManager.setTorchMode(cameraId, true);
                tb.setTextOff("On");
                toggleState.set(true);
                tb.setChecked(false);
            }catch (CameraAccessException e) {
                Toast.makeText(getApplicationContext(), "Camera Access Error! Please close any other camera app.", Toast.LENGTH_SHORT).show();
            }

        }
        else if(tb.isChecked()){
            try{
                cameraManager.setTorchMode(cameraId, false);
                tb.setTextOff("Off");
            }catch (CameraAccessException e) {
                Toast.makeText(getApplicationContext(), "Camera Access Error! Please close any other camera app.", Toast.LENGTH_SHORT).show();
            }

            Intent intent = new Intent(this, ShakeDetectionService.class);

            //calculate shake threshold
            double shakeThreshold = (double)sensitivitySeekBar.getProgress() / 10.0;
            shakeThreshold = (shakeThreshold < 0.1) ? 0.1 : shakeThreshold;

            //extra info for the service
            intent.putExtra(VIBRATION_INTENSITY, shakeThreshold);
            intent.putExtra(FLASH_DURATION, flashDuration);

            startService(intent);
            lightDurationBar.setEnabled(false);
        }else{
            Intent intent = new Intent(this, ShakeDetectionService.class);
            stopService(intent);
            lightDurationBar.setEnabled(true);
            toggleState.set(false);
        }
    }

    public class SeekBarChangeListener implements SeekBar.OnSeekBarChangeListener{

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            //flash duration cannot be 0
            progress = (progress > 0) ? progress : 1;
            //multiply by 1000 because duration is in milliseconds
            flashDuration = progress * 1000;
            //the text should represent seconds
            durationIndicator.setText(String.valueOf(progress));
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }
}
