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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import io.appium.settings.helpers.NotificationHelpers;

import static io.appium.settings.RecorderConstant.ACTION_RECORDING_FILENAME;
import static io.appium.settings.RecorderConstant.ACTION_RECORDING_RESULT_CODE;
import static io.appium.settings.RecorderConstant.ACTION_RECORDING_ROTATION;
import static io.appium.settings.RecorderConstant.ACTION_RECORDING_START;
import static io.appium.settings.RecorderConstant.ACTION_RECORDING_STOP;

public class RecorderService extends Service {
    private static final String TAG = "RecorderService";

    private static RecorderThread recorderThread;

    public RecorderService() {
        super();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy:");
        if (recorderThread != null && recorderThread.isRecordingRunning()) {
            recorderThread.stopRecording();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.v(TAG, "onStartCommand:intent=" + intent);

        int result = START_STICKY;
        final String action = intent != null ? intent.getAction() : null;
        Log.v(TAG, "onStartCommand:action string: " + action);
        if (ACTION_RECORDING_START.equals(action)) {
            showNotification(); // TODO is this really necessary

            MediaProjectionManager mMediaProjectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            if (mMediaProjectionManager != null) {
                startScreenRecord(mMediaProjectionManager, intent);
            } else {
                Log.e(TAG, "mMediaProjectionManager null");
                result = START_NOT_STICKY;
            }
        } else if (ACTION_RECORDING_STOP.equals(action)) {
            Log.v(TAG, "onStartCommand:intent=" + "stop");
            stopScreenRecord();
            result = START_NOT_STICKY;
        } else {
            Log.w(TAG, "onStartCommand: Unknown ACTION name");
            result = START_NOT_STICKY;
        }

        return result;
    }

    /**
     * start screen recording
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void startScreenRecord(MediaProjectionManager mediaProjectionManager,
                                   final Intent intent) {
        if (recorderThread != null) {
            Log.v(TAG, "recorder thread is not null");
            if (recorderThread.isRecordingRunning()) {
                Log.v(TAG, "recording is already continuing");
            } else {
                Log.v(TAG, "recording is stopped");
            }
            return;
        }

        int resultCode = intent.getIntExtra(ACTION_RECORDING_RESULT_CODE, 0);
        // get MediaProjection
        final MediaProjection projection = mediaProjectionManager.getMediaProjection(resultCode,
                intent);
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null");
            return;
        }

        String outputFilePath = intent.getStringExtra(ACTION_RECORDING_FILENAME);
        if (outputFilePath == null) {
            Log.i(TAG, "outputFilePath is null");
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int rawWidth = metrics.widthPixels;
        int rawHeight = metrics.heightPixels;

        int recordingRotation = intent.getIntExtra(ACTION_RECORDING_ROTATION, -1);

        if (recordingRotation == -1) {
            // fallback to our old determination method
            if (rawWidth > rawHeight) {
                // landscape mode
                /* TODO we need to rotate frames that comes from virtual screen before writing to file via muxer,
                 *  for handling landscape mode properly, rotateYuvImages somehow fast and reliable
                 *  for now, just flip width and height to fake and force portrait mode instead of landscape
                 * */
                rawWidth = metrics.heightPixels;
                rawHeight = metrics.widthPixels;
            }
            Log.v(TAG, String.format("startRecording:(%d,%d)(%d,%d)",
                    metrics.widthPixels, metrics.heightPixels, rawWidth, rawHeight));
        } else if (recordingRotation == 90 || recordingRotation == 270) {
            rawWidth = metrics.heightPixels;
            rawHeight = metrics.widthPixels;
            Log.v(TAG, String.format("startRecording:(%d,%d)(%d,%d)",
                    metrics.widthPixels, metrics.heightPixels, rawWidth, rawHeight));
        } else {
            // degree 0 and degree 180 conditions
            Log.v(TAG, String.format("startRecording:(%d,%d)(%d,%d)",
                    metrics.widthPixels, metrics.heightPixels, rawWidth, rawHeight));
        }

        recorderThread = new RecorderThread(projection, outputFilePath,
                rawWidth, rawHeight, recordingRotation);
        recorderThread.startRecording();
    }

    /**
     * stop screen recording
     */
    private void stopScreenRecord() {
        if (recorderThread != null) {
            recorderThread.stopRecording();
            recorderThread = null;
        }
        stopSelf();
    }

    private void showNotification() {
        // Set the info for the views that show in the notification panel.
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
    }
}