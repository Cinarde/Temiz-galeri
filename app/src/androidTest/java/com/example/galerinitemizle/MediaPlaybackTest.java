package com.example.galerinitemizle;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;
import static com.example.galerinitemizle.SwipeAndTrashTest.*;

@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MediaPlaybackTest {
    @Test public void mixedMediaPlaysReleasesUndoesAndTrashesWithConsent() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Use the isolated verification package", context.getPackageName().endsWith(".verification"));
        context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        String[] permissions = Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        for (String permission : permissions) InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(), permission);
        List<Uri> fixtures = new ArrayList<>();
        try {
            fixtures.add(MediaTestFixtures.video(context));
            fixtures.add(createPhoto(context));
            String video = ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(fixtures.get(0))).toString();
            String image = ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), ContentUris.parseId(fixtures.get(1))).toString();
            GalleryMedia expectedVideo;
            try (Cursor cursor = context.getContentResolver().query(fixtures.get(0),
                    new String[]{MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED,
                            MediaStore.MediaColumns.SIZE}, null, null, null)) {
                assertNotNull(cursor);
                assertTrue(cursor.moveToFirst());
                expectedVideo = new GalleryMedia(video, true, cursor.getLong(0), cursor.getLong(1), cursor.getLong(2));
            }
            final long photoSize;
            try (android.os.ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(fixtures.get(1), "r")) {
                assertNotNull(descriptor);
                photoSize = descriptor.getStatSize();
                assertTrue(photoSize > 0);
            }
            try (android.os.ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(fixtures.get(0), "r")) {
                assertNotNull(descriptor);
                assertTrue(expectedVideo.sizeBytes > 0);
                assertEquals(descriptor.getStatSize(), expectedVideo.sizeBytes);
            }
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                ready(scenario);
                scenario.onActivity(activity -> {
                    GalleryViewModel model = new ViewModelProvider(activity).get(GalleryViewModel.class);
                    assertTrue(model.mediaFor(video).video);
                    assertFalse(model.mediaFor(image).video);
                    assertEquals(photoSize, model.mediaFor(image).sizeBytes);
                    assertEquals(expectedVideo, model.mediaFor(video));
                    // Verify BOTH collections entered the real query result before fixing test order.
                    List<String> queried = new ArrayList<>();
                    while (model.session.top() != null) {
                        queried.add(model.session.top());
                        model.session.swipe(true);
                    }
                    assertTrue(queried.containsAll(Arrays.asList(video, image)));
                    model.session.reconcile(Collections.emptyList());
                    model.session.reconcile(Arrays.asList(video, image));
                    model.changed();
                    ViewGroup deck = activity.findViewById(R.id.deck);
                    TextView date = activity.findViewById(R.id.currentMediaDate);
                    assertEquals(SwipeDeckView.dateLabel(activity, model.mediaFor(video)), date.getText().toString());
                    assertEquals(((TextView) activity.findViewById(R.id.status)).getTextSize(), date.getTextSize(), 0f);
                    assertEquals(View.VISIBLE, date.getVisibility());
                    assertNotSame("Date must be outside the card", deck, date.getParent());
                    assertSize(activity, expectedVideo);
                    assertNoPlayers(activity);
                });
                play(scenario);
                verifySeeking(scenario, video);
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                        new File(context.getExternalFilesDir(null), "video-card.png"));
                onView(withId(R.id.deck)).perform(swipeRight());
                ready(scenario);
                scenario.onActivity(activity -> {
                    assertNoPlayers(activity);
                    assertEquals(image, new ViewModelProvider(activity).get(GalleryViewModel.class).session.top());
                    assertSize(activity, new ViewModelProvider(activity).get(GalleryViewModel.class).mediaFor(image));
                });
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(
                        new File(context.getExternalFilesDir(null), "photo-size.png"));
                onView(withId(R.id.undoButton)).perform(click());
                scenario.onActivity(activity -> {
                    assertNoPlayers(activity);
                    assertSize(activity, expectedVideo);
                });
                play(scenario);
                onView(withId(R.id.nav_menu)).perform(click());
                scenario.onActivity(activity -> {
                    assertNoPlayers(activity);
                    assertEquals(View.GONE, activity.findViewById(R.id.currentMediaSizePanel).getVisibility());
                });
                onView(withId(R.id.nav_sort)).perform(click());
                play(scenario);
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome();
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(MediaPlaybackTest::assertNoPlayers);
                context.startActivity(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                ready(scenario);
                scenario.onActivity(MediaPlaybackTest::assertNoPlayers);
                play(scenario);
                onView(withId(R.id.deck)).perform(swipeLeft());
                ready(scenario);
                scenario.onActivity(activity -> {
                    assertNoPlayers(activity);
                    assertEquals(1, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
                });
                onView(withId(R.id.undoButton)).perform(click());
                ready(scenario);
                scenario.onActivity(activity -> {
                    assertEquals(video, new ViewModelProvider(activity).get(GalleryViewModel.class).session.top());
                    assertEquals(0, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
                });
                play(scenario);
                onView(withId(R.id.discardButton)).perform(click());
                ready(scenario);
                onView(withId(R.id.discardButton)).perform(click());
                ready(scenario);
                onView(withId(R.id.nav_queue)).perform(click());
                scenario.recreate();
                ready(scenario);
                scenario.onActivity(activity -> assertEquals(2, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount()));
                onView(withId(R.id.cleanButton)).perform(click());
                consentButton(false).click();
                ready(scenario);
                assertEquals(0, trashed(context, fixtures.get(0)));
                assertEquals(0, trashed(context, fixtures.get(1)));
                onView(withId(R.id.cleanButton)).perform(click());
                consentButton(true).click();
                ready(scenario);
                assertEquals(1, trashed(context, fixtures.get(0)));
                assertEquals(1, trashed(context, fixtures.get(1)));
                scenario.onActivity(activity -> assertEquals(0, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount()));
                assertTrue(context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE)
                        .getStringSet(GalleryViewModel.PROCESSED_URIS, Collections.emptySet()).containsAll(Arrays.asList(video, image)));
            }
        } finally {
            Bundle args = new Bundle();
            args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
            for (Uri uri : fixtures) context.getContentResolver().delete(uri, args);
            context.getSharedPreferences(GalleryViewModel.HISTORY_PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static void assertSize(MainActivity activity, GalleryMedia media) {
        TextView size = activity.findViewById(R.id.currentMediaSize);
        assertEquals(media.formattedSize(), size.getText().toString());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.currentMediaSizePanel).getVisibility());
        assertNotSame("Size belongs above the card", activity.findViewById(R.id.deck), size.getParent().getParent());
    }

    private static void assertNoPlayers(MainActivity activity) {
        ViewGroup deck = activity.findViewById(R.id.deck);
        for (int index = 0; index < deck.getChildCount(); index++) {
            assertNull(((PlayerView) deck.getChildAt(index).findViewById(R.id.videoPlayer)).getPlayer());
        }
    }

    private static void verifySeeking(ActivityScenario<MainActivity> scenario, String video) {
        onView(allOf(withId(androidx.media3.ui.R.id.exo_play_pause), isDisplayed())).perform(click());
        onView(allOf(withId(androidx.media3.ui.R.id.exo_progress), isDisplayed())).perform(swipeRight());
        scenario.onActivity(activity -> {
            ViewGroup deck = activity.findViewById(R.id.deck);
            PlayerView view = deck.getChildAt(1).findViewById(R.id.videoPlayer);
            Player player = view.getPlayer();
            assertNotNull("Scrubbing must not release the player", player);
            assertFalse(player.getPlayWhenReady());
            assertTrue("Drag should seek forward", player.getCurrentPosition() > 5000);
            TextView duration = view.findViewById(androidx.media3.ui.R.id.exo_duration);
            assertFalse("Duration must be visible", duration.getText().toString().isEmpty());
            assertNotEquals("00:00", duration.getText().toString());
            assertEquals(video, new ViewModelProvider(activity).get(GalleryViewModel.class).session.top());
            assertEquals(0, new ViewModelProvider(activity).get(GalleryViewModel.class).session.trashCount());
        });
        onView(allOf(withId(androidx.media3.ui.R.id.exo_progress), isDisplayed())).perform(swipeLeft());
        scenario.onActivity(activity -> {
            ViewGroup deck = activity.findViewById(R.id.deck);
            Player player = ((PlayerView) deck.getChildAt(1).findViewById(R.id.videoPlayer)).getPlayer();
            assertNotNull(player);
            assertTrue("Drag should seek backward", player.getCurrentPosition() < 2500);
        });
        onView(allOf(withId(androidx.media3.ui.R.id.exo_ffwd), isDisplayed())).perform(click());
        scenario.onActivity(activity -> {
            ViewGroup deck = activity.findViewById(R.id.deck);
            Player player = ((PlayerView) deck.getChildAt(1).findViewById(R.id.videoPlayer)).getPlayer();
            assertNotNull(player);
            assertTrue(player.getCurrentPosition() >= 7000);
        });
        onView(allOf(withId(androidx.media3.ui.R.id.exo_rew), isDisplayed())).perform(click());
        scenario.onActivity(activity -> {
            ViewGroup deck = activity.findViewById(R.id.deck);
            Player player = ((PlayerView) deck.getChildAt(1).findViewById(R.id.videoPlayer)).getPlayer();
            assertNotNull(player);
            assertEquals(0, player.getCurrentPosition());
        });
        onView(allOf(withId(androidx.media3.ui.R.id.exo_play_pause), isDisplayed())).perform(click());
    }

    private static void play(ActivityScenario<MainActivity> scenario) {
        AtomicBoolean focused = new AtomicBoolean();
        long focusDeadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> focused.set(activity.hasWindowFocus()));
            if (focused.get()) break;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < focusDeadline);
        assertTrue("Activity must be focused before injecting a play tap", focused.get());
        // Window focus can arrive before the launcher's return animation finishes.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000);
        onView(allOf(withId(R.id.videoToggle), isDisplayed())).perform(click());
        AtomicBoolean playing = new AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            scenario.onActivity(activity -> {
                ViewGroup deck = activity.findViewById(R.id.deck);
                assertNull("Back card must never own a player", ((PlayerView) deck.getChildAt(0).findViewById(R.id.videoPlayer)).getPlayer());
                PlayerView view = deck.getChildAt(1).findViewById(R.id.videoPlayer);
                Player player = view.getPlayer();
                if (player != null) {
                    assertNull(player.getPlayerError());
                    playing.set(view.getVisibility() == View.VISIBLE && player.isPlaying() && player.getCurrentPosition() > 100);
                }
            });
            if (playing.get()) return;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        scenario.onActivity(activity -> {
            ViewGroup deck = activity.findViewById(R.id.deck);
            PlayerView view = deck.getChildAt(1).findViewById(R.id.videoPlayer);
            fail("Video never began playback. Player=" + view.getPlayer() + ", enabled=" + deck.isEnabled()
                    + ", busy=" + ((SwipeDeckView) deck).isBusy() + ", state=" + activity.getLifecycle().getCurrentState());
        });
    }
}
