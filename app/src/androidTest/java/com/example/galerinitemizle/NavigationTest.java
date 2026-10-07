package com.example.galerinitemizle;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NavigationTest {
    @Test public void pagesPreserveQueueAndRestorePhotoAfterRecreation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(context.getPackageName(), permission);
        List<Uri> fixtures = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) fixtures.add(createPhoto(context, i));
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                waitReady(scenario);
                screenshot(context, "01-sort");
                scenario.onActivity(activity -> {
                    View actions = activity.findViewById(R.id.swipeActions);
                    assertTrue("Actions must fit above navigation", actions.getBottom() <= activity.findViewById(R.id.pageSort).getHeight());
                });
                scenario.onActivity(activity -> activity.findViewById(R.id.discardButton).performClick());
                waitReady(scenario);
                scenario.onActivity(activity -> {
                    GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
                    assertEquals(1, model.session.trashCount());
                    ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_queue);
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.pageQueue).getVisibility());
                    assertEquals(View.GONE, activity.findViewById(R.id.pageSort).getVisibility());
                });
                screenshot(context, "02-queue");
                scenario.recreate();
                waitReady(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(R.id.nav_queue, ((BottomNavigationView) activity.findViewById(R.id.navigation)).getSelectedItemId());
                    assertEquals(1, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
                    ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_menu);
                });
                screenshot(context, "03-menu");
                scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
                scenario.onActivity(activity -> {
                    assertEquals(R.id.nav_sort, ((BottomNavigationView) activity.findViewById(R.id.navigation)).getSelectedItemId());
                    ((BottomNavigationView) activity.findViewById(R.id.navigation)).setSelectedItemId(R.id.nav_queue);
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                SystemClock.sleep(500);
                scenario.onActivity(activity -> {
                    RecyclerView grid = activity.findViewById(R.id.queueGrid);
                    RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(0);
                    assertNotNull(holder);
                    holder.itemView.findViewById(R.id.restoreButton).performClick();
                    assertEquals(0, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.queueEmpty).getVisibility());
                });
                screenshot(context, "04-empty");
            }
        } finally {
            // Only remove test-owned fixtures created above.
            for (Uri uri : fixtures) context.getContentResolver().delete(uri, null, null);
        }
    }

    private void waitReady(ActivityScenario<MainActivity> scenario) {
        long until = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < until) {
            scenario.onActivity(activity -> {
                GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
                ready.set(!model.loading && !((SwipeDeckView) activity.findViewById(R.id.deck)).isBusy());
                assertFalse(model.loadFailed);
            });
            if (ready.get()) return;
            SystemClock.sleep(80);
        }
        fail("Gallery did not become ready");
    }

    private Uri createPhoto(Context context, int index) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "galerini-ui-test-" + System.nanoTime() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GaleriniUiTest");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        assertNotNull(uri);
        Bitmap bitmap = Bitmap.createBitmap(720, 1000, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(197 + index * 8, 212, 216));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(244, 222, 169));
        canvas.drawCircle(500, 240, 100, paint);
        paint.setColor(Color.rgb(66, 105, 83));
        canvas.drawOval(-200, 480, 1000, 1500, paint);
        paint.setColor(Color.rgb(45, 76, 66));
        canvas.drawOval(180, 640, 1100, 1300, paint);
        try (OutputStream output = context.getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output); }
        bitmap.recycle();
        values.clear();
        values.put(MediaStore.Images.Media.IS_PENDING, 0);
        context.getContentResolver().update(uri, values, null, null);
        return uri;
    }

    private void screenshot(Context context, String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(500);
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory = context.getExternalFilesDir("ui-review");
        assertNotNull(directory);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        screenshot.recycle();
    }
}
