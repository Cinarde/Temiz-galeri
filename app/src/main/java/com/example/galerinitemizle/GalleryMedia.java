package com.example.galerinitemizle;

import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** Metadata only. URI keys stay identical to earlier versions of the app. */
public final class GalleryMedia {
    public final String uri;
    public final boolean video;
    public final long dateMillis;
    public final boolean captureDate;
    public final long sizeBytes;

    public GalleryMedia(String uri, boolean video, long dateTakenMillis, long dateAddedSeconds) {
        this(uri, video, dateTakenMillis, dateAddedSeconds, -1);
    }

    public GalleryMedia(String uri, boolean video, long dateTakenMillis, long dateAddedSeconds, long sizeBytes) {
        this.uri = uri;
        this.video = video;
        this.sizeBytes = sizeBytes < 0 ? -1 : sizeBytes;
        captureDate = dateTakenMillis > 0;
        dateMillis = captureDate ? dateTakenMillis
                : dateAddedSeconds > 0 && dateAddedSeconds <= Long.MAX_VALUE / 1000
                ? dateAddedSeconds * 1000 : 0;
    }

    public String formattedDate() {
        return dateMillis > 0 ? new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(dateMillis)) : null;
    }

    /** MediaStore file length; unknown sizes are never presented as zero. */
    public String formattedSize() {
        if (sizeBytes < 0) return null;
        long divisor = 1024;
        String unit = "KB";
        if (sizeBytes >= 1024L * 1024 * 1024) {
            divisor = 1024L * 1024 * 1024;
            unit = "GB";
        } else if (sizeBytes >= 1024L * 1024) {
            divisor = 1024L * 1024;
            unit = "MB";
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
        format.setGroupingUsed(false);
        // Keep tiny, nonempty files distinguishable from 0 KB.
        format.setMaximumFractionDigits(sizeBytes < 1024 ? 3 : 2);
        return format.format(sizeBytes / (double) divisor) + " " + unit;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof GalleryMedia)) return false;
        GalleryMedia item = (GalleryMedia) other;
        return uri.equals(item.uri) && video == item.video && dateMillis == item.dateMillis
                && captureDate == item.captureDate && sizeBytes == item.sizeBytes;
    }

    @Override public int hashCode() { return Objects.hash(uri, video, dateMillis, captureDate, sizeBytes); }
}
