/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.core.app.ActivityCompat;
import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.BluetoothConnectionSettingReceiver;
import io.appium.settings.receivers.ClipboardReceiver;
import io.appium.settings.receivers.DataConnectionSettingReceiver;
import io.appium.settings.receivers.HasAction;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocationInfoReceiver;
import io.appium.settings.receivers.MediaScannerReceiver;
import io.appium.settings.receivers.NotificationsReceiver;
import io.appium.settings.receivers.SmsReader;
import io.appium.settings.receivers.UnpairBluetoothDevicesReceiver;
import io.appium.settings.receivers.WiFiConnectionSettingReceiver;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    private String recordingOutputPath = "";
    private int recordingRotation = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering the app");

        registerSettingsReceivers(Arrays.asList(
                WiFiConnectionSettingReceiver.class,
                AnimationSettingReceiver.class,
                DataConnectionSettingReceiver.class,
                LocaleSettingReceiver.class,
                LocationInfoReceiver.class,
                ClipboardReceiver.class,
                BluetoothConnectionSettingReceiver.class,
                UnpairBluetoothDevicesReceiver.class,
                NotificationsReceiver.class,
                SmsReader.class,
                MediaScannerReceiver.class
        ));

        // https://developer.android.com/about/versions/oreo/background-location-limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ForegroundService.getForegroundServiceIntent(Settings.this));
        } else {
            LocationTracker.getInstance().start(this);
        }

        handleRecording(getIntent());
    }

    private void handleRecording(Intent intent) {
        if (intent == null) {
            Log.e(TAG, "handleRecording: intent is null");
            finishActivity();
            return;
        }

        String recordingAction = intent.getAction();
        if (recordingAction == null) {
            Log.e(TAG, "handleRecording: intent.getAction() is null");
            finishActivity();
            return;
        }

        if (!isHigherThanP()) {
            Log.e(TAG, "handleRecording: Current Android OS Version is lower than Q");
            finishActivity();
            return;
        }

        if (!areRecordingPermissionsGranted()) {
            Log.e(TAG, "handleRecording: Required Permissions are not granted");
            finishActivity();
            return;
        }

        String recordingFilename = intent.getStringExtra(
                RecorderService.ACTION_RECORDING_FILENAME);

        if (recordingFilename == null || recordingFilename.isEmpty()
                || !recordingFilename.endsWith(".mp4")) {
            String timeStamp = new SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            recordingFilename = RecorderConstant.DEFAULT_RECORDING_FILENAME +
                    "_" + timeStamp + ".mp4";
        }

        recordingOutputPath = getExternalFilesDir(null).getAbsolutePath()
                + File.separator + recordingFilename;

        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

        if (display != null) {
            int deviceRotation = display.getRotation();

            switch(deviceRotation) {
                case Surface.ROTATION_0:
                    // portrait
                    recordingRotation = 0;
                    break;
                case Surface.ROTATION_90:
                    // landscape left
                    recordingRotation = 90;
                    break;
                case Surface.ROTATION_180:
                    // flipped portrait
                    recordingRotation = 180;
                    break;
                case Surface.ROTATION_270:
                    // landscape right
                    recordingRotation = 270;
                    break;
            }
        }

        if (recordingAction.equals(RecorderService.ACTION_RECORDING_START)) {
            // start record
            final MediaProjectionManager manager
                    = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);

            if (manager == null) {
                Log.e(TAG, "handleRecording: manager(MediaProjectionManager) is null");
                finishActivity();
                return;
            }

            final Intent permissionIntent = manager.createScreenCaptureIntent();

            startActivityForResult(permissionIntent,
                    RecorderService.REQUEST_CODE_SCREEN_CAPTURE);
        } else if (recordingAction.equals(RecorderService.ACTION_RECORDING_STOP)) {
            // stop record
            final Intent recorderIntent = new Intent(this, RecorderService.class);
            recorderIntent.setAction(RecorderService.ACTION_RECORDING_STOP);
            startService(recorderIntent);

            finishActivity();
        } else {
            Log.e(TAG, "handleRecording: Unknown recording intent.action");
            finishActivity();
        }
    }

    private void finishActivity() {
        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(Settings.this::finish, 1000);
    }

    private boolean areRecordingPermissionsGranted() {
        // Check if we have required permission
        int permission = ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionAudio = ActivityCompat.checkSelfPermission(getApplicationContext(),
                Manifest.permission.RECORD_AUDIO);

        return permission == PackageManager.PERMISSION_GRANTED
                && permissionAudio == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isHigherThanP() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (RecorderService.REQUEST_CODE_SCREEN_CAPTURE != requestCode) {
            Log.e(TAG, "handleRecording: onActivityResult: Unknown request code");
            finishActivity();
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "handleRecording: onActivityResult: " +
                    "MediaProjection permission is not granted, " +
                    "Did you apply appops adb command?");
            finishActivity();
            return;
        }

        final Intent intent = new Intent(this, RecorderService.class);
        intent.setAction(RecorderService.ACTION_RECORDING_START);
        intent.putExtra(RecorderService.ACTION_RECORDING_RESULT_CODE, resultCode);
        intent.putExtra(RecorderService.ACTION_RECORDING_FILENAME, recordingOutputPath);
        intent.putExtra(RecorderService.ACTION_RECORDING_ROTATION, recordingRotation);
        intent.putExtras(data);

        startService(intent);

        finishActivity();
    }

    private void registerSettingsReceivers(List<Class<? extends BroadcastReceiver>> receiverClasses)
    {
        for (Class<? extends BroadcastReceiver> receiverClass: receiverClasses) {
            try {
                final BroadcastReceiver receiver = receiverClass.newInstance();
                IntentFilter filter = new IntentFilter(((HasAction) receiver).getAction());
                getApplicationContext().registerReceiver(receiver, filter);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }
}
