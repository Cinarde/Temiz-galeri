package com.example.galerinitemizle;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Generates a tiny animated grayscale clip locally; no network or personal media. */
final class MediaTestFixtures {
    static Uri video(Context context) throws Exception {
        File file = File.createTempFile("media-regression-", ".mp4", context.getCacheDir());
        try {
            encode(file);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/GaleriniRegression");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            assertNotNull(uri);
            try {
                try (FileInputStream input = new FileInputStream(file);
                     OutputStream output = context.getContentResolver().openOutputStream(uri)) {
                    assertNotNull(output);
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
                return uri;
            } catch (Exception error) {
                context.getContentResolver().delete(uri, null, null);
                throw error;
            }
        } finally {
            if (file.exists() && !file.delete()) Log.w("MediaTestFixtures", "Could not remove temporary test video");
        }
    }

    private static void encode(File file) throws Exception {
        int width = 160, height = 240, fps = 15, frames = 120;
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 150000);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec codec = MediaCodec.createEncoderByType("video/avc");
        MediaMuxer muxer = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started = false;
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            int frame = 0, track = -1;
            boolean inputDone = false, outputDone = false;
            byte[] pixels = new byte[width * height * 3 / 2];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long deadline = SystemClock.uptimeMillis() + 20000;
            while (!outputDone && SystemClock.uptimeMillis() < deadline) {
                if (!inputDone) {
                    int input = codec.dequeueInputBuffer(10000);
                    if (input >= 0) {
                        if (frame == frames) {
                            codec.queueInputBuffer(input, 0, 0, frame * 1000000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            Arrays.fill(pixels, 0, width * height, (byte) (40 + frame % 120));
                            Arrays.fill(pixels, width * height, pixels.length, (byte) 128);
                            ByteBuffer buffer = codec.getInputBuffer(input);
                            assertNotNull(buffer);
                            buffer.clear(); buffer.put(pixels);
                            codec.queueInputBuffer(input, 0, pixels.length, frame * 1000000L / fps, 0);
                            frame++;
                        }
                    }
                }
                int output = codec.dequeueOutputBuffer(info, 10000);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start(); started = true;
                } else if (output >= 0) {
                    if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        ByteBuffer buffer = codec.getOutputBuffer(output);
                        assertNotNull(buffer);
                        buffer.position(info.offset); buffer.limit(info.offset + info.size);
                        muxer.writeSampleData(track, buffer, info);
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(output, false);
                }
            }
            assertTrue("Test video encoding timed out", outputDone);
        } finally {
            codec.release();
            if (started) muxer.stop();
            muxer.release();
        }
    }
}
