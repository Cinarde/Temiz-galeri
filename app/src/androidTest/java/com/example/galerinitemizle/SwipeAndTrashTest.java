package com.example.galerinitemizle;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SwipeAndTrashTest {
    @Test public void realTouchesUndoBothDirectionsAndSystemConsentActuallyTrashes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Run this end-to-end test in a separate package, never the user's install.
        assertTrue("Use -PverificationAppId=com.example.galerinitemizle.verification", context.getPackageName().endsWith(".verification"));
        context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        List<Uri> fixtures = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                Uri fixture = createPhoto(context);
                fixtures.add(fixture);
                ids.add(ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(fixture)).toString());
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                ready(scenario);
                scenario.onActivity(activity -> {
                    GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
                    model.session.reconcile(java.util.Collections.emptyList());
                    model.session.reconcile(ids);
                    model.changed();
                });
                onView(withId(R.id.keepButton)).perform(click());
                ready(scenario);
                onView(withId(R.id.undoButton)).perform(click());
                assertTop(scenario, ids.get(0), 0);
                onView(withId(R.id.deck)).perform(swipeLeft());
                ready(scenario);
                assertTop(scenario, ids.get(1), 1);
                onView(withId(R.id.undoButton)).perform(click());
                assertTop(scenario, ids.get(0), 0);
                onView(withId(R.id.deck)).perform(swipeRight());
                ready(scenario);
                onView(withId(R.id.undoButton)).perform(click());
                assertTop(scenario, ids.get(0), 0);
                onView(withId(R.id.discardButton)).perform(click());
                ready(scenario);
                onView(withId(R.id.keepButton)).perform(click());
                ready(scenario);
                onView(withId(R.id.keepButton)).perform(click());
                ready(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(R.id.nav_queue, ((BottomNavigationView) activity.findViewById(R.id.navigation)).getSelectedItemId());
                    assertEquals(1, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
                });
                scenario.recreate();
                ready(scenario);
                onView(withId(R.id.cleanButton)).perform(click());
                consentButton(false).click();
                ready(scenario);
                scenario.onActivity(activity -> assertEquals(1, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount()));
                assertEquals(0, trashed(context, fixtures.get(0)));
                onView(withId(R.id.cleanButton)).perform(click());
                consentButton(true).click();
                ready(scenario);
                scenario.onActivity(activity -> assertEquals(0, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount()));
                assertEquals("Android must have really trashed the photo", 1, trashed(context, fixtures.get(0)));
                assertEquals(0, trashed(context, fixtures.get(1)));
                assertEquals(0, trashed(context, fixtures.get(2)));
            }
        } finally {
            Bundle args = new Bundle();
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
            for (Uri uri : fixtures) context.getContentResolver().delete(uri, args);
            context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static UiObject2 consentButton(boolean approve) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 button = device.wait(Until.findObject(By.res("android", approve ? "button1" : "button2")), 5000);
        assertNotNull("Expected Android trash consent dialog", button);
        return button;
    }

    private static void assertTop(ActivityScenario<MainActivity> scenario, String uri, int count) {
        ready(scenario);
        scenario.onActivity(activity -> {
            GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
            assertEquals(uri, model.session.top());
            assertEquals(count, model.session.trashCount());
        });
    }

    private static void ready(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean ready = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            scenario.onActivity(activity -> {
                GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
                assertFalse(model.loadFailed);
                ready.set(!model.loading && !model.requestingTrash && !((SwipeDeckView) activity.findViewById(R.id.deck)).isBusy());
            });
            if (ready.get()) return;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("UI did not become ready");
    }

    private static int trashed(Context context, Uri uri) {
        Bundle args = new Bundle();
        args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.IS_TRASHED}, args, null)) {
            assertNotNull(cursor);
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private static Uri createPhoto(Context context) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "trash-regression-" + System.nanoTime() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GaleriniRegression");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xff315e47);
        try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); }
        bitmap.recycle();
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
        return uri;
    }
}
