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
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

import static android.content.Context.WINDOW_SERVICE;
import static io.appium.settings.recorder.RecorderConstant.NO_ROTATION_SET;

public class RecorderUtil {
    public static boolean isLowerThanQ() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    public static boolean areRecordingPermissionsGranted(Context context) {
        // Check if we have required permission
        int permission = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int permissionAudio = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.RECORD_AUDIO);

        return permission == PackageManager.PERMISSION_GRANTED
                && permissionAudio == PackageManager.PERMISSION_GRANTED;
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
}
