package com.example.galerinitemizle;

import android.app.Application;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoryPersistenceTest {
    @Test public void freshModelFiltersBothDirectionsAndUndoSurvivesAnotherReload() throws Exception {
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        String prefsName = "history-test-" + System.nanoTime();
        SharedPreferences prefs = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        List<Uri> fixtures = new ArrayList<>();
        List<GalleryViewModel> models = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) fixtures.add(createPhoto(app));
            String kept = normalized(fixtures.get(0));
            String queued = normalized(fixtures.get(1));
            String unseen = normalized(fixtures.get(2));
            GalleryViewModel first = newModel(app, prefs, models);
            onMain(() -> {
                first.session.reconcile(Arrays.asList(kept, queued, unseen));
                first.onPhotoSwiped(true);
                first.onPhotoSwiped(false);
            });
            Set<String> priorSnapshot = prefs.getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet());
            assertEquals(new HashSet<>(Arrays.asList(kept, queued)), priorSnapshot);
            // Commit an unrelated marker off the UI thread to wait for preceding apply() writes.
            assertTrue(prefs.edit().putBoolean("flush_test", true).commit());
            GalleryViewModel second = newModel(app, prefs, models);
            refresh(second);
            onMain(() -> {
                second.refresh(false);
                assertEquals("A permission change must not hide pending choices", 1, second.session.trashCount());
            });
            refresh(second);
            onMain(() -> {
                assertEquals(Collections.singletonList(queued), second.session.trashBatch(100));
                Set<String> deck = drainDeck(second);
                assertTrue(deck.contains(unseen));
                assertFalse(deck.contains(kept));
                assertFalse(deck.contains(queued));
                second.undoLastTrash();
                assertEquals(queued, second.session.top());
            });
            assertEquals(Collections.singleton(kept), prefs.getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet()));
            assertEquals("Previously returned preference sets must not be mutated", new HashSet<>(Arrays.asList(kept, queued)), priorSnapshot);
            GalleryViewModel third = newModel(app, prefs, models);
            refresh(third);
            onMain(() -> {
                Set<String> deck = drainDeck(third);
                assertTrue(deck.contains(queued));
                assertFalse(deck.contains(kept));
                assertEquals(0, third.session.trashCount());
            });
            GalleryViewModel fourth = newModel(app, prefs, models);
            refresh(fourth);
            onMain(() -> {
                assertTrue(fourth.session.canUndo());
                fourth.undoLastSwipe();
                assertEquals("A right swipe must remain undoable after reload", kept, fourth.session.top());
            });
            assertTrue(prefs.getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet()).isEmpty());
        } finally {
            onMain(() -> { for (GalleryViewModel model : models) model.onCleared(); });
            for (Uri uri : fixtures) app.getContentResolver().delete(uri, null, null);
            app.deleteSharedPreferences(prefsName);
        }
    }

    private static GalleryViewModel newModel(Application app, SharedPreferences prefs, List<GalleryViewModel> models) {
        AtomicReference<GalleryViewModel> result = new AtomicReference<>();
        onMain(() -> result.set(new GalleryViewModel(app, prefs)));
        models.add(result.get());
        return result.get();
    }

    private static void refresh(GalleryViewModel model) {
        onMain(() -> model.refresh(true));
        AtomicBoolean ready = new AtomicBoolean();
        long until = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < until) {
            onMain(() -> {
                assertFalse(model.loadFailed);
                ready.set(!model.loading);
            });
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("MediaStore query timed out");
    }

    private static Set<String> drainDeck(GalleryViewModel model) {
        Set<String> result = new HashSet<>();
        while (model.session.top() != null) {
            result.add(model.session.top());
            model.session.swipe(true); // Inspect candidates without modifying persistent history.
        }
        return result;
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static String normalized(Uri uri) {
        return ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(uri)).toString();
    }

    private static Uri createPhoto(Context context) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "history-test-" + System.nanoTime() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GaleriniHistoryTest");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        Bitmap bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); }
        bitmap.recycle();
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
        return uri;
    }
}
