package com.example.galerinitemizle;

import org.junit.Test;
import java.util.Calendar;
import java.util.Locale;
import static org.junit.Assert.*;

public class GalleryMediaTest {
    @Test public void captureDateWinsAndFormatsDayMonthYear() {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2024, Calendar.FEBRUARY, 29, 12, 0);
        GalleryMedia item = new GalleryMedia("content://media/external/video/media/1", true,
                calendar.getTimeInMillis(), 1000);
        assertTrue(item.captureDate);
        assertEquals(calendar.getTimeInMillis(), item.dateMillis);
        assertEquals("29/02/2024", item.formattedDate());
    }

    @Test public void dateAddedConvertsSecondsToMilliseconds() {
        GalleryMedia item = new GalleryMedia("image", false, 0, 1700000000L);
        assertFalse(item.captureDate);
        assertEquals(1700000000000L, item.dateMillis);
    }

    @Test public void missingOrOverflowedDateDoesNotBecome1970() {
        assertNull(new GalleryMedia("image", false, 0, 0).formattedDate());
        assertNull(new GalleryMedia("image", false, -1, Long.MAX_VALUE).formattedDate());
    }

    @Test public void collectionIsPartOfIdentityAndMetadataChangesAreVisible() {
        GalleryMedia image = new GalleryMedia("content://media/external/images/media/1", false, 1000, 0);
        assertNotEquals(image, new GalleryMedia("content://media/external/video/media/1", true, 1000, 0));
        assertNotEquals(image, new GalleryMedia(image.uri, false, 2000, 0));
        assertEquals(image, new GalleryMedia(image.uri, false, 1000, 99));
    }

    @Test public void sizeUsesKilobytesMegabytesAndGigabytesWithLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("0 KB", sized(0).formattedSize());
            assertEquals("0,001 KB", sized(1).formattedSize());
            assertEquals("1 KB", sized(1024).formattedSize());
            assertEquals("1,5 KB", sized(1536).formattedSize());
            assertEquals("1 MB", sized(1024L * 1024).formattedSize());
            assertEquals("1,5 MB", sized(1536L * 1024).formattedSize());
            assertEquals("1 GB", sized(1024L * 1024 * 1024).formattedSize());
            assertEquals("1,5 GB", sized(1536L * 1024 * 1024).formattedSize());
            assertEquals("5 GB", sized(5L * 1024 * 1024 * 1024).formattedSize());
            Locale.setDefault(Locale.US);
            assertEquals("1.5 MB", sized(1536L * 1024).formattedSize());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void unknownSizeIsDistinctAndSizeUpdatesChangeMetadata() {
        assertNull(sized(-1).formattedSize());
        assertNull(new GalleryMedia("image", false, 0, 0).formattedSize());
        assertNotEquals(sized(-1), sized(0));
        assertNotEquals(sized(1024), sized(2048));
        GalleryMedia first = sized(1024);
        GalleryMedia second = new GalleryMedia("image", false, 0, 0, 1024);
        assertNotSame(first, second);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        String largest = sized(Long.MAX_VALUE).formattedSize();
        assertNotNull(largest);
        assertTrue(largest.endsWith(" GB"));
    }

    private GalleryMedia sized(long bytes) { return new GalleryMedia("image", false, 0, 0, bytes); }
}
