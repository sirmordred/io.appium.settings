package io.appium.settings;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import androidx.annotation.RequiresApi;

public class RecorderThread implements Runnable {

    private static final String TAG = "RecorderThread";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private MediaCodec audioEncoder;
    private MediaCodec videoEncoder;
    private AudioRecord audioRecord;
    private MediaMuxer muxer;
    private boolean muxerStarted;
    private boolean startTimestampInitialized;
    private long startTimestampUs;
    private Handler handler;
    private Thread audioRecordThread;

    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;


    private String outputFilePath;
    private String videoMime;
    private int videoWidth;
    private int videoHeight;
    private int videoBitrate;
    private int sampleRate;
    private Thread recordingThread;

    private volatile boolean stopped = false;
    private volatile boolean audioStopped;
    private volatile boolean asyncError = false;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private VirtualDisplay.Callback displayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
            Log.v(TAG, "Display paused");
        }

        @Override
        public void onStopped() {
            super.onStopped();
            if (!stopped) {
                asyncError = true;
            }
        }
    };

    public RecorderThread(MediaProjection mediaProjection, String outputFilePath,
                          int videoWidth, int videoHeight, int videoBitrate) {
        this.mediaProjection = mediaProjection;
        handler = new Handler();
        this.outputFilePath = outputFilePath;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.videoBitrate = videoBitrate;
        this.sampleRate = RecorderConstant.AUDIO_CODEC_SAMPLE_RATE;
        videoMime = MediaFormat.MIMETYPE_VIDEO_AVC;
    }

    public void startRecording() {
        stopped = false;
        recordingThread = new Thread(this);
        recordingThread.start();
    }

    public void stopRecording() {
        stopped = true;
    }

    public boolean isRecordingContinue() {
        return stopped;
    }

    private void setupVideoCodec() throws IOException {
        // Encoded video resolution matches virtual display.
        MediaFormat encoderFormat = MediaFormat.createVideoFormat(videoMime, videoWidth,
                videoHeight);
        encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
                RecorderConstant.VIDEO_CODEC_FRAME_RATE);
        encoderFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER,
                RecorderConstant.AUDIO_CODEC_REPEAT_PREV_FRAME_AFTER);
        encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                RecorderConstant.AUDIO_CODEC_I_FRAME_INTERVAL);

        videoEncoder = MediaCodec.createEncoderByType(videoMime);
        videoEncoder.configure(encoderFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = videoEncoder.createInputSurface();
        videoEncoder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setupVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("Appium Screen Recorder",
                videoWidth, videoHeight, DisplayMetrics.DENSITY_HIGH,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, displayCallback, handler);
    }

    private void setupAudioCodec() throws IOException {
        // Encoded video resolution matches virtual display. TODO set channelCount 2 try stereo quality
        MediaFormat encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate, RecorderConstant.AUDIO_CODEC_CHANNEL_COUNT);
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                RecorderConstant.AUDIO_CODEC_DEFAULT_BITRATE);

        audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        audioEncoder.configure(encoderFormat, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioEncoder.start();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void setupAudioRecord() {
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioFormat.Builder audioFormatBuilder = new AudioFormat.Builder();
        AudioFormat newAudioFormat = audioFormatBuilder.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        AudioRecord.Builder audioRecordBuilder = new AudioRecord.Builder();
        AudioPlaybackCaptureConfiguration apcc =
                new AudioPlaybackCaptureConfiguration.Builder(this.mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();
        audioRecord = audioRecordBuilder.setAudioFormat(newAudioFormat)
                .setBufferSizeInBytes(4 * minBufferSize)
                .setAudioPlaybackCaptureConfig(apcc)
                .build();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startAudioRecord() {
        audioRecordThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                try {
                    audioRecord.startRecording();
                } catch (Exception e) {
                    asyncError = true;
                    e.printStackTrace();
                    return;
                }
                try {
                    while (!audioStopped) {
                        int index = audioEncoder.dequeueInputBuffer(
                                RecorderConstant.MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT);
                        if (index < 0) {
                            continue;
                        }
                        ByteBuffer inputBuffer = audioEncoder.getInputBuffer(index);
                        if (inputBuffer == null) {
                            if (!stopped) {
                                asyncError = true;
                            }
                            return;
                        }
                        inputBuffer.clear();
                        int read = audioRecord.read(inputBuffer, inputBuffer.capacity());
                        if (read < 0) {
                            if (!stopped) {
                                asyncError = true;
                            }
                            break;
                        }
                        audioEncoder.queueInputBuffer(index, 0, read,
                                getPresentationTimeUs(), 0);
                    }
                } catch (Exception e) {
                    if (!stopped) {
                        Log.e(TAG, "Audio error", e);
                        asyncError = true;
                        e.printStackTrace();
                    }
                } finally {
                    audioRecord.stop();
                    audioRecord.release();
                    audioRecord = null;
                }
            }
        });
        audioRecordThread.start();
    }

    private long getPresentationTimeUs() {
        if (!startTimestampInitialized) {
            startTimestampUs =
                    System.nanoTime() / RecorderConstant.ENCODER_PRESENTATION_TIME_DEFAULT_DIVIDER;
            startTimestampInitialized = true;
        }
        return (System.nanoTime() / RecorderConstant.ENCODER_PRESENTATION_TIME_DEFAULT_DIVIDER
                        - startTimestampUs);
    }

    private void startMuxerIfSetUp() {
        if (audioTrackIndex >= 0 && videoTrackIndex >= 0) {
            muxer.start();
            muxerStarted = true;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void run() {
        try {
            setupVideoCodec();
            setupVirtualDisplay();

            setupAudioCodec();
            setupAudioRecord();

            muxer = new MediaMuxer(this.outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            startAudioRecord();

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long lastAudioTimestampUs = -1;

            while (!stopped && !asyncError) {
                int encoderStatus;

                encoderStatus = audioEncoder.dequeueOutputBuffer(bufferInfo, 0);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.w(TAG, "audio encoder.dequeueOutputBuffer: try again");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (audioTrackIndex > 0) {
                        Log.e(TAG, "audioTrackIndex less than zero");
                        break;
                    }
                    audioTrackIndex = muxer.addTrack(audioEncoder.getOutputFormat());
                    startMuxerIfSetUp();
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from audio encoder.dequeueOutputBuffer: "
                            + encoderStatus);
                } else {
                    ByteBuffer encodedData = audioEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                        Log.e(TAG, "encodedData null");
                        break;
                    }

                    if (bufferInfo.presentationTimeUs > lastAudioTimestampUs
                            && muxerStarted && bufferInfo.size != 0 &&
                            (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        lastAudioTimestampUs = bufferInfo.presentationTimeUs;
                        muxer.writeSampleData(audioTrackIndex, encodedData, bufferInfo);
                    }

                    audioEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.v(TAG, "buffer eos");
                        break;
                    }
                }

                if (videoTrackIndex >= 0 && audioTrackIndex < 0) {
                    continue; // wait for audio config before processing any video data frames
                }

                encoderStatus = videoEncoder.dequeueOutputBuffer(bufferInfo,
                        RecorderConstant.MEDIA_QUEUE_BUFFERING_DEFAULT_TIMEOUT);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.w(TAG, "encoder.dequeueOutputBuffer: try again");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (videoTrackIndex > 0) {
                        Log.e(TAG, "videoTrackIndex less than zero");
                        break;
                    }
                    videoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());
                    startMuxerIfSetUp();
                } else if (encoderStatus < 0) {
                    Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: "
                            + encoderStatus);
                } else {
                    ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
                    if (encodedData == null) {
                        Log.w(TAG, "videoEncoder, encodedData null");
                        break;
                    }

                    if (bufferInfo.size != 0 &&
                            (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        bufferInfo.presentationTimeUs = getPresentationTimeUs();
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo);
                    }

                    videoEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.v(TAG, "videoEncoder, buffer eos");
                        break;
                    }
                }
            }
        } catch (Exception mainException) {
            mainException.printStackTrace();
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
                muxer = null;
            }

            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }

            if (surface != null) {
                surface.release();
                surface = null;
            }

            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }

            if (audioRecordThread != null) {
                audioStopped = true;
                try {
                    audioRecordThread.join();
                    audioRecordThread = null;
                } catch (InterruptedException e) {
                    Log.w(TAG, "Error releasing resources, audioRecordThread: ", e);
                    e.printStackTrace();
                }
            }

            if (audioEncoder != null) {
                audioEncoder.stop();
                audioEncoder.release();
                audioEncoder = null;
            }
        }
    }
}