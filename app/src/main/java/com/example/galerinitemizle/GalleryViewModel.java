package com.example.galerinitemizle;

import android.app.Application;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;

public class GalleryViewModel extends AndroidViewModel {
    static final String HISTORY_PREFERENCES = "photo_history";
    static final String PROCESSED_URIS = "processed_uris";
    private static final String QUEUED_URIS = "queued_uris_ordered";
    private static final String SWIPE_ORDER = "swipe_order";
    final CleaningSession session = new CleaningSession();
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    final ArrayList<String> pendingTrash = new ArrayList<>();
    boolean loading;
    boolean requestingTrash;
    boolean loadFailed;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private final SharedPreferences preferences;
    private final HashSet<String> processedUris;
    // Preserve unconfirmed left swipes across restarts, including temporarily inaccessible photos.
    private final LinkedHashSet<String> queuedUris = new LinkedHashSet<>();
    private final LinkedHashSet<String> swipeOrder = new LinkedHashSet<>();

    public GalleryViewModel(@NonNull Application application) {
        this(application, application.getSharedPreferences(HISTORY_PREFERENCES, Context.MODE_PRIVATE));
    }

    GalleryViewModel(Application application, SharedPreferences preferences) {
        super(application);
        this.preferences = preferences;
        // Never mutate the Set returned by SharedPreferences itself.
        processedUris = new HashSet<>(preferences.getStringSet(PROCESSED_URIS, Collections.emptySet()));
        try {
            JSONArray savedQueue = new JSONArray(preferences.getString(QUEUED_URIS, "[]"));
            for (int i = 0; i < savedQueue.length(); i++) queuedUris.add(savedQueue.getString(i));
        } catch (JSONException ignored) {
            // Invalid queue data must not erase the separately stored swipe history.
        }
        processedUris.addAll(queuedUris);
        try {
            JSONArray savedOrder = new JSONArray(preferences.getString(SWIPE_ORDER, new JSONArray(queuedUris).toString()));
            for (int i = 0; i < savedOrder.length(); i++) {
                String uri = savedOrder.getString(i);
                if (processedUris.contains(uri)) swipeOrder.add(uri);
            }
        } catch (JSONException ignored) {
            swipeOrder.addAll(queuedUris);
        }
        session.restoreQueued(queuedUris);
        session.restoreSwipeOrder(swipeOrder);
    }

    void onPhotoSwiped(boolean keep) {
        String uri = session.top();
        if (uri == null) return;
        session.swipe(keep);
        processedUris.add(uri); // Both left and right swipes are permanently filtered.
        swipeOrder.remove(uri);
        swipeOrder.add(uri);
        if (!keep) queuedUris.add(uri);
        saveHistory();
        changed();
    }

    void undoLastTrash() {
        String uri = session.undoLastTrash();
        if (uri == null) return;
        swipeOrder.remove(uri);
        processedUris.remove(uri);
        queuedUris.remove(uri);
        saveHistory();
        changed();
    }

    void undoLastSwipe() {
        String uri = session.undoLastSwipe();
        if (uri == null) return;
        swipeOrder.remove(uri);
        processedUris.remove(uri);
        queuedUris.remove(uri);
        saveHistory();
        changed();
    }

    void restorePhoto(String uri) {
        if (!session.restore(uri)) return;
        swipeOrder.remove(uri);
        processedUris.remove(uri);
        queuedUris.remove(uri);
        saveHistory();
        changed();
    }

    void confirmTrashed(List<String> confirmed) {
        session.confirmTrashed(confirmed);
        queuedUris.removeAll(confirmed);
        swipeOrder.removeAll(confirmed);
        // Keep processed history even if a photo is later restored by another gallery.
        saveHistory();
    }

    void restorePendingRequest(List<String> restored) {
        pendingTrash.clear();
        pendingTrash.addAll(restored);
        requestingTrash = true;
        processedUris.addAll(restored);
        queuedUris.addAll(restored);
        swipeOrder.addAll(restored);
        session.restoreQueued(queuedUris);
        session.restoreSwipeOrder(swipeOrder);
        saveHistory();
    }

    private void saveHistory() {
        preferences.edit()
                .putStringSet(PROCESSED_URIS, new HashSet<>(processedUris))
                .putString(QUEUED_URIS, new JSONArray(queuedUris).toString())
                .putString(SWIPE_ORDER, new JSONArray(swipeOrder).toString())
                .apply(); // Disk writes are asynchronous; each editor gets its own Set snapshot.
    }

    void reviewHistoryAgain(boolean hasAccess) {
        // Re-show handled originals without dropping pending deletion choices.
        processedUris.clear();
        processedUris.addAll(queuedUris);
        swipeOrder.retainAll(queuedUris);
        session.reviewKeptAgain();
        saveHistory();
        refresh(hasAccess);
    }

    void changed() { changes.setValue(changes.getValue() + 1); }

    void refresh(boolean hasAccess) {
        if (requestingTrash) return; // Freeze the exact batch until the OS returns a result.
        int request = ++generation;
        loadFailed = false;
        if (!hasAccess) {
            loading = false;
            session.reconcile(Collections.emptyList());
            changed();
            return;
        }
        loading = true;
        changed();
        Set<String> processedSnapshot = new HashSet<>(processedUris);
        io.execute(() -> {
            try {
                PhotoQuery photos = queryPhotos(processedSnapshot);
                main.post(() -> {
                    if (request != generation) return;
                    session.restoreQueued(queuedUris);
                    session.restoreSwipeOrder(swipeOrder);
                    session.reconcile(photos.accessible, photos.candidates);
                    loading = false;
                    changed();
                });
            } catch (RuntimeException error) {
                main.post(() -> {
                    if (request != generation) return;
                    loading = false;
                    loadFailed = true;
                    changed();
                });
            }
        });
    }

    private PhotoQuery queryPhotos(Set<String> processed) {
        PhotoQuery result = new PhotoQuery();
        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String[] projection = {MediaStore.Images.Media._ID};
        String selection = MediaStore.Images.Media.IS_TRASHED + " = 0 AND "
                + MediaStore.Images.Media.IS_PENDING + " = 0";
        String order = MediaStore.Images.Media.DATE_ADDED + " DESC, "
                + MediaStore.Images.Media._ID + " DESC";
        try (Cursor cursor = getApplication().getContentResolver().query(
                collection, projection, selection, null, order)) {
            if (cursor == null) throw new IllegalStateException("MediaStore unavailable");
            int id = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                String uri = ContentUris.withAppendedId(collection, cursor.getLong(id)).toString();
                // Retain accessibility information for queued photos, but never add a
                // previously swiped photo to the new deck. HashSet lookup is O(1) on average.
                result.accessible.add(uri);
                if (!processed.contains(uri)) result.candidates.add(uri);
            }
        }
        return result;
    }

    private static final class PhotoQuery {
        final List<String> accessible = new ArrayList<>();
        final List<String> candidates = new ArrayList<>();
    }

    @Override protected void onCleared() {
        ++generation;
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
