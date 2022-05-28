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

    private void handleRecording(Intent openingIntent) {
        if (openingIntent != null && openingIntent.getAction() != null) {
            if (openingIntent.getAction().equals(RecorderService.ACTION_RECORDING_START)
                    && isHigherThanP()) {
                if (areRecordingPermissionsGranted()) {
                    String recordingFilename = openingIntent.getStringExtra(
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

                    // start record
                    final MediaProjectionManager manager
                            = (MediaProjectionManager) getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE);
                    final Intent permissionIntent = manager.createScreenCaptureIntent();

                    startActivityForResult(permissionIntent,
                            RecorderService.REQUEST_CODE_SCREEN_CAPTURE);
                } else {
                    finishActivity();
                }
            } else if (openingIntent.getAction().equals(RecorderService.ACTION_RECORDING_STOP)
                    && isHigherThanP()) {
                // stop record
                final Intent intent = new Intent(this, RecorderService.class);
                intent.setAction(RecorderService.ACTION_RECORDING_STOP);
                startService(intent);

                finishActivity();
            } else {
                finishActivity();
            }
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
        if (RecorderService.REQUEST_CODE_SCREEN_CAPTURE == requestCode
                && resultCode == Activity.RESULT_OK) {
            final Intent intent = new Intent(this, RecorderService.class);
            intent.setAction(RecorderService.ACTION_RECORDING_START);
            intent.putExtra(RecorderService.ACTION_RECORDING_RESULT_CODE, resultCode);
            intent.putExtra(RecorderService.ACTION_RECORDING_FILENAME, recordingOutputPath);
            intent.putExtras(data);

            startService(intent);
        }

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
