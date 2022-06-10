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

package io.appium.settings.recorder;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

import static android.content.Context.WINDOW_SERVICE;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_MAX_DURATION;
import static io.appium.settings.recorder.RecorderConstant.ACTION_RECORDING_PRIORITY;
import static io.appium.settings.recorder.RecorderConstant.NO_ROTATION_SET;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_MAX_DURATION_DEFAULT_MS;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_DEFAULT;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_MAX;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_MIN;
import static io.appium.settings.recorder.RecorderConstant.RECORDING_PRIORITY_NORM;

public class RecorderUtil {
    private static final String TAG = "RecorderUtil";

    public static boolean isLowerThanQ() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    public static boolean areRecordingPermissionsGranted(Context context) {
        // Check if we have required permission
        int permissionAudio = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO);

        return permissionAudio == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isValidFileName(String filename) {
        if (filename == null || filename.isEmpty() || !filename.endsWith(".mp4")) {
            return false;
        }
        if (filename.length() >= 255) {
            return false;
        }
        for (char c : filename.toCharArray()) {
            if (!isValidFilenameChar(c)){
                return false;
            }
        }
        return true;
    }

    // taken from https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/os/FileUtils.java#995
    private static boolean isValidFilenameChar(char c) {
        switch (c) {
            case '"':
            case '*':
            case '/':
            case ':':
            case '<':
            case '>':
            case '?':
            case '\\':
            case '|':
            case '\0':
                return false;
            default:
                return true;
        }
    }

    public static int getDeviceRotation(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        if (wm == null) {
            return NO_ROTATION_SET;
        }
        Display display = wm.getDefaultDisplay();
        if (display == null) {
            return NO_ROTATION_SET;
        }
        int deviceRotation = display.getRotation();

        switch(deviceRotation) {
            case Surface.ROTATION_0:
                // portrait
                return 0;
            case Surface.ROTATION_90:
                // landscape left
                return 90;
            case Surface.ROTATION_180:
                // flipped portrait
                return 180;
            case Surface.ROTATION_270:
                // landscape right
                return 270;
            default:
                break;
        }
        return NO_ROTATION_SET;
    }

    public static int getRecordingPriority(Intent intent) {
        if (intent.hasExtra(ACTION_RECORDING_PRIORITY)) {
            try {
                int userRequestedRecordingPriority =
                        Integer.parseInt(intent.getStringExtra(ACTION_RECORDING_PRIORITY));
                switch(userRequestedRecordingPriority) {
                    case RECORDING_PRIORITY_MAX:
                        return Thread.MAX_PRIORITY;
                    case RECORDING_PRIORITY_MIN:
                        return Thread.MIN_PRIORITY;
                    case RECORDING_PRIORITY_NORM:
                        return Thread.NORM_PRIORITY;
                    default:
                        break;
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Exception while retrieving recording priority", e);
            }
        } else {
            Log.e(TAG, "Unable to retrieve recording priority");
        }
        return RECORDING_PRIORITY_DEFAULT;
    }

    public static int getRecordingMaxDuration(Intent intent) {
        if (intent.hasExtra(ACTION_RECORDING_MAX_DURATION)) {
            try {
                int userRequestedMaxDurationInSecond =
                        Integer.parseInt(intent.getStringExtra(ACTION_RECORDING_MAX_DURATION));
                if (userRequestedMaxDurationInSecond <= 0) {
                    Log.e(TAG, "Maximum recording duration must be greater than 0 second");
                    return RECORDING_MAX_DURATION_DEFAULT_MS;
                }
                // Convert it to millisecond and return
                return userRequestedMaxDurationInSecond * 1000;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Exception while retrieving recording max duration", e);
            }
        } else {
            Log.e(TAG, "Unable to retrieve recording max duration");
        }
        return RECORDING_MAX_DURATION_DEFAULT_MS;
    }
}
