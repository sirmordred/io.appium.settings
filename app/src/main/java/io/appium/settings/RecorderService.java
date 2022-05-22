package io.appium.settings;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
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

    @SuppressLint("NewApi")
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        Log.v(TAG, "onStartCommand:intent=" + intent);

        int result = START_STICKY;
        final String action = intent != null ? intent.getAction() : null;
        Log.v(TAG, "onStartCommand:intent=" + "action string: " + action);
        if (ACTION_RECORDING_START.equals(action)) {
            showNotification(TAG); // TODO is this really necessary

            MediaProjectionManager mMediaProjectionManager =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            if (mMediaProjectionManager != null) {
                startScreenRecord(mMediaProjectionManager, intent);
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
    @SuppressLint("NewApi")
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
                int width = metrics.widthPixels;
                int height = metrics.heightPixels;
                float scale;
                final float scaleX;
                final float scaleY;
                if (width > height) {
                    // Horizontal
                    scaleX = width / 1920f;
                    scaleY = height / 1080f;
                } else {
                    // Vertical
                    scaleX = width / 1080f;
                    scaleY = height / 1920f;
                }
                scale = Math.max(scaleX,  scaleY);
                width = (int)(width / scale);
                height = (int)(height / scale);
                Log.v(TAG, String.format("startRecording:(%d,%d)(%d,%d)",
                        metrics.widthPixels, metrics.heightPixels, width, height));
                String outputFilePath = intent.getStringExtra(ACTION_RECORDING_FILENAME);
                if (outputFilePath != null) {
                    recorderThread = new RecorderThread(projection, outputFilePath, width, height,
                            calcBitRate(30, width, height));
                    recorderThread.startRecording();
                } else {
                    Log.i(TAG, "outputFilePath == null");
                }
            }
        }
    }

    protected int calcBitRate(final int frameRate, int width, int height) {
        final int bitrate = (int)(0.25f * frameRate * width * height);
        Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
        return bitrate;
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

    @SuppressLint("NewApi")
    private void showNotification(final CharSequence text) {
        // Set the info for the views that show in the notification panel.
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
    }
}