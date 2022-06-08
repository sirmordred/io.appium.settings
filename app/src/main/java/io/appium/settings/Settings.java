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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
import io.appium.settings.recorder.RecorderService;
import io.appium.settings.recorder.RecorderUtil;

import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_BASE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_FILENAME;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_ROTATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_STOP;
import static io.appium.settings.recorder.RecorderConstant.NO_ROTATION_SET;
import static io.appium.settings.recorder.RecorderConstant.REQUEST_CODE_SCREEN_CAPTURE;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    private String recordingOutputPath = "";
    private int recordingRotation = NO_ROTATION_SET;

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

        if (!recordingAction.startsWith(ACTION_RECORDING_BASE)) {
            Log.i(TAG, "handleRecording: Different intent");
            finishActivity();
            return;
        }

        if (RecorderUtil.isLowerThanQ()) {
            Log.e(TAG, "handleRecording: Current Android OS Version is lower than Q");
            finishActivity();
            return;
        }

        if (!RecorderUtil.areRecordingPermissionsGranted(getApplicationContext())) {
            Log.e(TAG, "handleRecording: Required Permissions are not granted");
            finishActivity();
            return;
        }

        String recordingFilename = intent.getStringExtra(ACTION_RECORDING_FILENAME);

        if (RecorderUtil.isValidFileName(recordingFilename)) {
            Log.e(TAG, "handleRecording: Invalid filename passed by user");
            finishActivity();
            return;
        }

        /*
        External Storage File Directory for app
        (i.e /storage/emulated/0/Android/data/io.appium.settings/files) may not be created
        so we need to call getExternalFilesDir() method twice
        source:https://www.androidbugfix.com/2021/10/getexternalfilesdirnull-returns-null-in.html
         */
        File externalStorageFile = getExternalFilesDir(null);
        if (externalStorageFile == null) {
            externalStorageFile = getExternalFilesDir(null);
        }
        // if path is still null despite calling method twice, early exit
        if (externalStorageFile == null) {
            Log.e(TAG, "handleRecording: external storage file path returns null");
            finishActivity();
            return;
        }
        recordingOutputPath = externalStorageFile.getAbsolutePath()
                + File.separator + recordingFilename;

        recordingRotation = RecorderUtil.getDeviceRotation(getApplicationContext());

        if (recordingAction.equals(ACTION_RECORDING_START)) {
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

            startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE);
        } else if (recordingAction.equals(ACTION_RECORDING_STOP)) {
            // stop record
            final Intent recorderIntent = new Intent(this, RecorderService.class);
            recorderIntent.setAction(ACTION_RECORDING_STOP);
            startService(recorderIntent);

            finishActivity();
        } else {
            Log.e(TAG, "handleRecording: Unknown recording intent.action:" + recordingAction);
            finishActivity();
        }
    }

    private void finishActivity() {
        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(Settings.this::finish, 1000);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE_SCREEN_CAPTURE != requestCode) {
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
        intent.setAction(ACTION_RECORDING_START);
        intent.putExtra(ACTION_RECORDING_RESULT_CODE, resultCode);
        intent.putExtra(ACTION_RECORDING_FILENAME, recordingOutputPath);
        intent.putExtra(ACTION_RECORDING_ROTATION, recordingRotation);
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
