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

public class RecorderService extends Service {
    private static final String TAG = "RecorderService";

    public static final int REQUEST_CODE_SCREEN_CAPTURE = 123;
    private static final String BASE = "io.appium.settings.recording";
    public static final String ACTION_RECORDING_START = BASE + ".ACTION_START";
    public static final String ACTION_RECORDING_STOP = BASE + ".ACTION_STOP";
    public static final String ACTION_RECORDING_RESULT_CODE = "result_code";
    public static final String ACTION_RECORDING_FILENAME = "recording_filename";

    private static RecorderThread recorderThread;

    public RecorderService() {
        super();
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy:");
        if (recorderThread != null && recorderThread.isRecordingContinue()) {
            recorderThread.stopRecording();
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
            }
        } else if (ACTION_RECORDING_STOP.equals(action)) {
            Log.v(TAG, "onStartCommand:intent=" + "stop");
            stopScreenRecord();
            result = START_NOT_STICKY;
        }

        return result;
    }

    /**
     * start screen recording
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startScreenRecord(MediaProjectionManager mediaProjectionManager,
                                   final Intent intent) {
        if (recorderThread == null) {
            Log.v(TAG, "recorder thread null");

            final int resultCode = intent.getIntExtra(ACTION_RECORDING_RESULT_CODE, 0);
            // get MediaProjection
            final MediaProjection projection = mediaProjectionManager.getMediaProjection(resultCode,
                    intent);
            if (projection != null) {
                Log.v(TAG, "projection different from null");
                final DisplayMetrics metrics = getResources().getDisplayMetrics();
                int rawWidth = metrics.widthPixels;
                int rawHeight = metrics.heightPixels;
                boolean isRotated = false;
                if (rawWidth > rawHeight) {
                    // landscape mode
                    isRotated = true;
                    /* TODO we need to rotate frames that comes from virtual screen before writing to file via muxer,
                    *  for handling landscape mode properly, rotateYuvImages somehow fast and reliable
                    *  for now, just flip width and height to fake and force portrait mode instead of landscape
                    * */
                    rawWidth = metrics.heightPixels;
                    rawHeight = metrics.widthPixels;
                }
                Log.v(TAG, String.format("startRecording:(%d,%d)(%d,%d)",
                        metrics.widthPixels, metrics.heightPixels, rawWidth, rawHeight));
                String outputFilePath = intent.getStringExtra(ACTION_RECORDING_FILENAME);
                if (outputFilePath != null) {
                    recorderThread = new RecorderThread(projection, outputFilePath,
                            rawWidth, rawHeight, isRotated);
                    recorderThread.startRecording();
                } else {
                    Log.i(TAG, "outputFilePath == null");
                }
            }
        }
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