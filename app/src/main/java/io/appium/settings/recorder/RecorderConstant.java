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

import android.media.MediaFormat;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

import io.appium.settings.BuildConfig;

public class RecorderConstant {
    public static final int REQUEST_CODE_SCREEN_CAPTURE = 123;
    public static final String ACTION_RECORDING_BASE = BuildConfig.APPLICATION_ID + ".recording";
    public static final String ACTION_RECORDING_START = ACTION_RECORDING_BASE + ".ACTION_START";
    public static final String ACTION_RECORDING_STOP = ACTION_RECORDING_BASE + ".ACTION_STOP";
    public static final String ACTION_RECORDING_RESULT_CODE = "result_code";
    public static final String ACTION_RECORDING_ROTATION = "recording_rotation";
    public static final String ACTION_RECORDING_FILENAME = "filename";
    public static final String ACTION_RECORDING_PRIORITY = "priority";
    public static final String ACTION_RECORDING_MAX_DURATION = "max_duration_sec";
    public static final String ACTION_RECORDING_RESOLUTION = "resolution_mode";
    public static final String RECORDING_DEFAULT_VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    public static final int RECORDING_RESOLUTION_FULL_HD = 5;
    public static final int RECORDING_RESOLUTION_HD = 4;
    public static final int RECORDING_RESOLUTION_480P = 3;
    public static final int RECORDING_RESOLUTION_QVGA = 2;
    public static final int RECORDING_RESOLUTION_QCIF = 1;
    public static final Pair<Integer, Integer> RECORDING_RESOLUTION_DEFAULT =
            Pair.create(1920, 1080);
    public static final float BITRATE_MULTIPLIER = 0.25f;
    public static final int AUDIO_CODEC_SAMPLE_RATE_HZ = 44100;
    public static final int AUDIO_CODEC_CHANNEL_COUNT = 1;
    public static final int AUDIO_CODEC_REPEAT_PREV_FRAME_AFTER_MS = 1000000;
    public static final int AUDIO_CODEC_I_FRAME_INTERVAL_MS = 5;
    public static final int AUDIO_CODEC_DEFAULT_BITRATE = 64000;
    public static final int VIDEO_CODEC_FRAME_RATE = 30;
    public static final long MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT_MS = 10000;
    public static final long NANOSECONDS_IN_MICROSECOND = 1000;
    public static final String NO_PATH_SET = "";
    public static final long NO_TIMESTAMP_SET = -1;
    // Assume 0 degree == portrait as default
    public static final int RECORDING_ROTATION_DEFAULT_DEGREE = 0;
    public static final int NO_TRACK_INDEX_SET = -1;
    public static final int NO_RESOLUTION_MODE_SET = -1;
    public static final int RECORDING_PRIORITY_MAX = 2;
    public static final int RECORDING_PRIORITY_NORM = 1;
    public static final int RECORDING_PRIORITY_MIN = 0;
    public static final int RECORDING_PRIORITY_DEFAULT = Thread.MAX_PRIORITY;
    public static final int RECORDING_MAX_DURATION_DEFAULT_MS = 15 * 60 * 1000; // 15 Minutes, in milliseconds
    // Pair.create(int width, int height)
    public static final List<Pair<Integer, Integer>> RECORDING_RESOLUTION_LIST =
            Arrays.asList(
                    Pair.create(1920, 1080),
                    Pair.create(1280, 720),
                    Pair.create(720, 480),
                    Pair.create(320, 240),
                    Pair.create(176, 144)
                    );
}
