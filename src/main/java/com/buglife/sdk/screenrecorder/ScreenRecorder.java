package com.buglife.sdk.screenrecorder;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.buglife.sdk.Attachment;
import com.buglife.sdk.Buglife;
import com.buglife.sdk.Log;
import com.buglife.sdk.R;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.buglife.sdk.Attachment.TYPE_MP4;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class ScreenRecorder {
    private static final int VIDEO_SCALE = 25;
    private static final int MAX_RECORD_TIME_MS = 30 * 1000;

    private final @NonNull Handler mMainThread = new Handler(Looper.getMainLooper());
    private final @NonNull Context mContext;
    private final File mOutputDirectory;
    private File mOutputFilePath;
    private @Nullable OverlayView mOverlayView;
    private final @NonNull WindowManager mWindowManager;
    private boolean mIsRecording;
    private CountDownTimer mCountdownTimer;
    private ScreenProjector mScreenProjector;

    public ScreenRecorder(Context context, int resultCode, Intent data) {
        mContext = context;
        File externalCacheDir = context.getExternalCacheDir();
        mOutputDirectory = new File(externalCacheDir, "Buglife");
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mScreenProjector = new ScreenProjector.Builder(context)
                .setResultCode(resultCode)
                .setResultData(data)
                .build();
    }

    public void start() {
        showOverlay();
        startRecording();
    }

    private void showOverlay() {
        mOverlayView = new OverlayView(mContext, new OverlayView.OverlayViewClickListener() {
            @Override
            public void onResize() {
                mWindowManager.updateViewLayout(mOverlayView, mOverlayView.getLayoutParams());
            }

            @Override
            public void onStopButtonClicked() {
                stopRecording();
            }
        });

        mWindowManager.addView(mOverlayView, OverlayView.getLayoutParams(mContext));
    }

    private void hideOverlay() {
        if (mOverlayView != null) {
            mWindowManager.removeView(mOverlayView);
            mOverlayView = null;
        }
    }

    private void startRecording() {
        Log.d("Starting screen recording");

        if (!mOutputDirectory.exists() && !mOutputDirectory.mkdirs()) {
            Log.e("Unable to create directory for screen recording output: " + mOutputDirectory);
            Toast.makeText(mContext, "Oops! Looks like there was a problem writing video to your device's external storage.", Toast.LENGTH_SHORT).show();
            return;
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        final int scaledDisplayWidth = (displayMetrics.widthPixels * VIDEO_SCALE) / 100;
        final int scaledDisplayHeight = (displayMetrics.heightPixels * VIDEO_SCALE) / 100;

        final DateFormat fileFormat = new SimpleDateFormat("'Buglife_'yyyy-MM-dd-HH-mm-ss'.mp4'", Locale.US);
        String outputFilename = fileFormat.format(new Date());
        mOutputFilePath = new File(mOutputDirectory, outputFilename);
        Log.d("output file path = " + mOutputFilePath);

        // Start projecting
        mScreenProjector.setScreenEncoder(new ScreenFileEncoder.Builder()
                .setWidth(scaledDisplayWidth)
                .setHeight(scaledDisplayHeight)
                .setOutputFile(mOutputFilePath)
                .build());
        mScreenProjector.start();

        mIsRecording = true;
        mCountdownTimer = new CountDownTimer(MAX_RECORD_TIME_MS, 1000) { //create a timer that ticks every second
            public void onTick(long millisecondsUntilFinished) {
                Button stopButton = mOverlayView.getStopButton();
                String base = mContext.getString(R.string.stop_recording);
                stopButton.setText(base + " " + String.valueOf(millisecondsUntilFinished/1000));
            }

            public void onFinish() {
                stopRecording();
            }

        }.start();

        Log.d("Screen recording started");
    }

    private void stopRecording() {
        Log.d("Stopping screen recording");

        if (!mIsRecording) {
            throw new Buglife.BuglifeException("Attempted to stop screen recorder, but it isn't currently recording");
        }
        mCountdownTimer.cancel();

        mIsRecording = false;

        hideOverlay();

        mScreenProjector.stop();

        MediaScannerConnection.scanFile(mContext, new String[]{mOutputFilePath.getAbsolutePath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(final String path, final Uri uri) {
                mMainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Recording complete: " + uri);
                        onRecordingFinished(path);
                    }
                });
            }
        });
    }

    private void onRecordingFinished(String path) {
        Attachment attachment;
        File file = new File(path);

        attachment = new Attachment.Builder("ScreenRecording.mp4", TYPE_MP4).build(file, true);

        Buglife.addAttachment(attachment);
        Buglife.showReporter();
    }
}