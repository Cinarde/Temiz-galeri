package com.example.galerinitemizle;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class VideoPermissionUpgradeTest {
    /** Launch with images granted and videos revoked, just like the earlier photo-only app. */
    @Test public void photoOnlyInstallRequestsVideoAccessAndKeepsHistory() {
        assumeTrue(Build.VERSION.SDK_INT >= 33);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(context.getPackageName().endsWith(".verification"));
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO));
        SharedPreferences history = context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences activityPrefs = context.getSharedPreferences(MainActivity.class.getName(), Context.MODE_PRIVATE);
        history.edit().clear().putStringSet(GalleryViewModel.PROCESSED_URIS, Collections.singleton("upgrade-test-marker")).commit();
        activityPrefs.edit().clear().putBoolean("asked_permission", true).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 allow = device.wait(Until.findObject(By.res("com.google.android.permissioncontroller", "permission_allow_all_button")), 5000);
            if (allow == null) allow = device.wait(Until.findObject(By.res("com.google.android.permissioncontroller", "permission_allow_button")), 2000);
            // Android may reuse a previous media-group grant without showing a new dialog.
            if (allow != null) allow.click();
            SwipeAndTrashTest.ready(scenario);
            assertEquals("Video access must be granted after the upgrade request", PackageManager.PERMISSION_GRANTED,
                    context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO));
            assertTrue(activityPrefs.getBoolean("asked_video_permission", false));
            assertTrue(history.getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet()).contains("upgrade-test-marker"));
            scenario.onActivity(activity -> assertEquals(View.GONE, activity.findViewById(R.id.expandAccessButton).getVisibility()));
            scenario.recreate();
            SwipeAndTrashTest.ready(scenario);
            assertTrue(history.getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet()).contains("upgrade-test-marker"));
        } finally {
            history.edit().clear().commit();
            activityPrefs.edit().clear().commit();
        }
    }
}
