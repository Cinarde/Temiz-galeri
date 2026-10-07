package com.example.galerinitemizle;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    // Keep Binder payloads and saved in-flight state small. Each batch gets its own OS consent.
    private static final int TRASH_BATCH_SIZE = 100;
    private static final String PENDING = "pending_trash";
    private GalleryViewModel model;
    private SwipeDeckView deck;
    private TextView status, empty;
    private View progress;
    private MaterialButton access, discard, keep, undo, clean;
    private MaterialButton start, review, refreshButton;
    private BottomNavigationView navigation;
    private QueueAdapter queueAdapter;
    private View pageSort, pageQueue, pageMenu;
    private int selectedPage = R.id.nav_sort;
    private OnBackPressedCallback pageBack;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> refresh());

    private final ActivityResultLauncher<IntentSenderRequest> trashLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                int count = model.pendingTrash.size();
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Android completes the operation BEFORE returning RESULT_OK.
                    // Do not call ContentResolver.delete() or createDeleteRequest().
                    model.confirmTrashed(model.pendingTrash);
                    Toast.makeText(this, getString(R.string.trash_success, count), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, R.string.trash_cancelled, Toast.LENGTH_SHORT).show();
                }
                model.pendingTrash.clear();
                model.requestingTrash = false;
                refresh();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        model = new ViewModelProvider(this).get(GalleryViewModel.class);
        if (savedInstanceState != null && !model.requestingTrash) {
            ArrayList<String> restored = savedInstanceState.getStringArrayList(PENDING);
            if (restored != null && !restored.isEmpty()) {
                model.restorePendingRequest(restored);
            }
        }
        deck = findViewById(R.id.deck);
        status = findViewById(R.id.status);
        empty = findViewById(R.id.emptyMessage);
        progress = findViewById(R.id.progress);
        access = findViewById(R.id.accessButton);
        discard = findViewById(R.id.discardButton);
        keep = findViewById(R.id.keepButton);
        undo = findViewById(R.id.undoButton);
        clean = findViewById(R.id.cleanButton);
        start = findViewById(R.id.startButton);
        review = findViewById(R.id.reviewButton);
        refreshButton = findViewById(R.id.refreshButton);
        pageSort = findViewById(R.id.pageSort);
        pageQueue = findViewById(R.id.pageQueue);
        pageMenu = findViewById(R.id.pageMenu);
        pageSort.getViewTreeObserver().addOnGlobalLayoutListener(this::sizePhotoArea);
        navigation = findViewById(R.id.navigation);
        RecyclerView grid = findViewById(R.id.queueGrid);
        grid.setLayoutManager(new GridLayoutManager(this, getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2));
        queueAdapter = new QueueAdapter(uri -> {
            if (!canEdit()) return;
            model.restorePhoto(uri);
            Toast.makeText(this, R.string.restored_message, Toast.LENGTH_SHORT).show();
        });
        grid.setAdapter(queueAdapter);
        pageBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { navigation.setSelectedItemId(R.id.nav_sort); }
        };
        getOnBackPressedDispatcher().addCallback(this, pageBack);
        navigation.setOnItemSelectedListener(item -> {
            showPage(item.getItemId());
            return true;
        });
        findViewById(R.id.backToSort).setOnClickListener(view -> navigation.setSelectedItemId(R.id.nav_sort));
        review.setOnClickListener(view -> navigation.setSelectedItemId(R.id.nav_queue));
        refreshButton.setOnClickListener(view -> refresh());
        findViewById(R.id.reviewHistoryButton).setOnClickListener(view ->
                new MaterialAlertDialogBuilder(this).setTitle(R.string.review_history)
                        .setMessage(R.string.review_history_body)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.continue_action, (dialog, which) -> model.reviewHistoryAgain(hasAccess())).show());
        findViewById(R.id.howButton).setOnClickListener(view -> showHelp(R.string.how_title, R.string.how_body));
        findViewById(R.id.trashInfoButton).setOnClickListener(view -> showHelp(R.string.trash_info_title, R.string.trash_info_body));
        start.setOnClickListener(view -> {
            if (!hasAccess()) requestAccess();
            else if (model.loadFailed) refresh();
            else if (model.session.trashCount() > 0) navigation.setSelectedItemId(R.id.nav_queue);
            else refresh();
        });
        deck.setListener(new SwipeDeckView.Listener() {
            @Override public void onSwipe(boolean shouldKeep) {
                model.onPhotoSwiped(shouldKeep);
                if (model.session.top() == null && model.session.trashCount() > 0) {
                    navigation.setSelectedItemId(R.id.nav_queue);
                }
            }
            @Override public void onBusyChanged(boolean busy) { updateButtons(); }
        });
        discard.setOnClickListener(view -> deck.swipe(false));
        keep.setOnClickListener(view -> deck.swipe(true));
        undo.setOnClickListener(view -> {
            model.undoLastSwipe();
        });
        undo.setTooltipText(getString(R.string.undo));
        access.setOnClickListener(view -> {
            if (model.loadFailed && hasAccess()) refresh();
            else requestAccess();
        });
        clean.setOnClickListener(view -> {
            if (model.session.trashCount() > TRASH_BATCH_SIZE) {
                new MaterialAlertDialogBuilder(this).setTitle(R.string.batch_title)
                        .setMessage(getString(R.string.batch_message, model.session.trashCount(), TRASH_BATCH_SIZE))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.continue_action, (dialog, which) -> requestTrash()).show();
            } else requestTrash();
        });
        model.changes.observe(this, ignored -> render());
        int restoredPage = savedInstanceState == null ? R.id.nav_sort : savedInstanceState.getInt("selected_page", R.id.nav_sort);
        navigation.setSelectedItemId(restoredPage);
    }

    private void showHelp(int title, int body) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body)
                .setPositiveButton(R.string.understood, null).show();
    }

    private void sizePhotoArea() {
        if (selectedPage != R.id.nav_sort || pageSort.getHeight() == 0) return;
        LinearLayout content = findViewById(R.id.sortContent);
        View photo = findViewById(R.id.photoArea);
        int otherHeight = content.getPaddingTop() + content.getPaddingBottom();
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            otherHeight += params.topMargin + params.bottomMargin;
            if (child != photo) otherHeight += child.getMeasuredHeight();
        }
        // Keep controls in the viewport; small screens can scroll instead of crushing the card.
        int minHeight = Math.round((model.session.top() == null ? 310 : 220)
                * getResources().getDisplayMetrics().density);
        int height = Math.max(minHeight, pageSort.getHeight() - otherHeight);
        if (photo.getLayoutParams().height != height) {
            photo.getLayoutParams().height = height;
            photo.requestLayout();
        }
    }

    private void showPage(int page) {
        deck.cancelGesture();
        selectedPage = page;
        pageSort.setVisibility(page == R.id.nav_sort ? View.VISIBLE : View.GONE);
        pageQueue.setVisibility(page == R.id.nav_queue ? View.VISIBLE : View.GONE);
        pageMenu.setVisibility(page == R.id.nav_menu ? View.VISIBLE : View.GONE);
        pageBack.setEnabled(page != R.id.nav_sort);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        // Android 14 selected-photo access may change while the app is in the background.
        refresh();
    }

    @Override protected void onStop() {
        deck.cancelGesture();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_page", selectedPage);
        if (model.requestingTrash) outState.putStringArrayList(PENDING, new ArrayList<>(model.pendingTrash));
        super.onSaveInstanceState(outState);
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean fullAccess() {
        return Build.VERSION.SDK_INT >= 33
                ? granted(Manifest.permission.READ_MEDIA_IMAGES)
                : granted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private boolean partialAccess() {
        return Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
    }

    private boolean hasAccess() { return fullAccess() || partialAccess(); }

    private void requestAccess() {
        String primary = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("asked_permission", false);
        if (fullAccess() || (asked && !hasAccess() && !shouldShowRequestPermissionRationale(primary))) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("asked_permission", true).apply();
        if (Build.VERSION.SDK_INT >= 34) {
            permissionLauncher.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED});
        } else permissionLauncher.launch(new String[]{primary});
    }

    private void refresh() {
        if (deck != null) deck.cancelGesture();
        model.refresh(hasAccess());
    }

    private void render() {
        boolean accessible = hasAccess();
        boolean show = accessible && !model.loading && !model.loadFailed;
        boolean sorting = selectedPage == R.id.nav_sort;
        deck.setPhotos(show && sorting ? model.session.top() : null, show && sorting ? model.session.next() : null);
        deck.setEnabled(show && sorting && !model.requestingTrash);
        progress.setVisibility(model.loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.emptyPanel).setVisibility(!model.loading && (!show || model.session.top() == null) ? View.VISIBLE : View.GONE);
        empty.setText(!accessible ? R.string.need_access : model.loadFailed ? R.string.load_error : R.string.empty_gallery);
        ((TextView) findViewById(R.id.emptyTitle)).setText(!accessible ? R.string.start_title : model.loadFailed ? R.string.error_title : R.string.done_title);
        status.setText(accessible ? getString(!fullAccess() && partialAccess() ? R.string.sort_status_partial : R.string.sort_status,
                model.session.remainingCount()) : getString(R.string.sort_status_hint));
        access.setText(model.loadFailed && accessible ? R.string.retry : accessible ? R.string.manage_access : R.string.grant_access);
        ((TextView) findViewById(R.id.accessStatus)).setText(fullAccess() ? R.string.access_full : partialAccess() ? R.string.access_partial : R.string.access_none);
        start.setText(!accessible ? R.string.grant_access : model.loadFailed ? R.string.retry : model.session.trashCount() > 0 ? R.string.review_queue : R.string.refresh_gallery);
        findViewById(R.id.swipeActions).setVisibility(show && (model.session.top() != null || model.session.canUndo()) ? View.VISIBLE : View.GONE);
        int count = model.session.trashCount();
        review.setText(getString(R.string.review_count, count));
        review.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.queueSummary)).setText(count > 0 ? getString(R.string.queue_summary, count) : getString(R.string.queue_empty_summary));
        findViewById(R.id.queueEmpty).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.queueFooter).setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        queueAdapter.submitList(selectedPage == R.id.nav_queue
                ? model.session.trashBatch(Integer.MAX_VALUE) : new ArrayList<>());
        if (count > 0) navigation.getOrCreateBadge(R.id.nav_queue).setNumber(count);
        else navigation.removeBadge(R.id.nav_queue);
        clean.setText(getString(R.string.clean_count, model.session.trashCount()));
        updateButtons();
    }

    private boolean canEdit() {
        return hasAccess() && !model.loading && !model.loadFailed && !model.requestingTrash && !deck.isBusy();
    }

    private void updateButtons() {
        if (clean == null) return;
        boolean ready = canEdit();
        discard.setEnabled(ready && model.session.top() != null);
        keep.setEnabled(ready && model.session.top() != null);
        undo.setEnabled(ready && model.session.canUndo());
        clean.setEnabled(ready && model.session.trashCount() > 0);
        access.setEnabled(!model.loading && !model.requestingTrash && !deck.isBusy());
        start.setEnabled(!model.loading && !model.requestingTrash);
        refreshButton.setEnabled(ready);
        findViewById(R.id.reviewHistoryButton).setEnabled(ready);
        queueAdapter.setActionsEnabled(ready);
    }

    private void requestTrash() {
        if (model.requestingTrash || model.loading || deck.isBusy() || !hasAccess()) return;
        ArrayList<String> batch = new ArrayList<>(model.session.trashBatch(TRASH_BATCH_SIZE));
        if (batch.isEmpty()) return;
        ArrayList<Uri> uris = new ArrayList<>();
        for (String value : batch) uris.add(Uri.parse(value));
        try {
            // API 30+: true means move to the system trash, NOT permanently delete.
            PendingIntent consent = MediaStore.createTrashRequest(getContentResolver(), uris, true);
            model.pendingTrash.clear();
            model.pendingTrash.addAll(batch);
            model.requestingTrash = true;
            model.changed();
            trashLauncher.launch(new IntentSenderRequest.Builder(consent.getIntentSender()).build());
        } catch (RuntimeException error) {
            model.pendingTrash.clear();
            model.requestingTrash = false;
            Toast.makeText(this, R.string.trash_error, Toast.LENGTH_LONG).show();
            refresh();
        }
    }
}
