package com.example.galerinitemizle;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.Objects;

/** Two preview cards, with at most one player owned by the top card. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class SwipeDeckView extends FrameLayout {
    public interface Listener {
        void onSwipe(boolean keep);
        void onBusyChanged(boolean busy);
    }
    private final MaterialCardView front, back;
    private final ImageView frontImage, backImage;
    private final PlayerView playerView;
    private final MaterialButton videoToggle;
    private GalleryMedia topItem, nextItem;
    private ExoPlayer player;
    private Listener listener;
    private float downX, downY;
    private boolean busy, animating, dragging;
    private boolean hostActive;
    private boolean controllerGesture;

    public SwipeDeckView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        back = card(context);
        front = card(context);
        frontImage = front.findViewById(R.id.mediaPreview);
        backImage = back.findViewById(R.id.mediaPreview);
        playerView = front.findViewById(R.id.videoPlayer);
        videoToggle = front.findViewById(R.id.videoToggle);
        videoToggle.setOnClickListener(view -> toggleVideo());
        back.setScaleX(0.94f);
        back.setScaleY(0.94f);
        back.setTranslationY(dp(12));
        back.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        front.setVisibility(INVISIBLE);
        back.setVisibility(INVISIBLE);
    }

    private MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(24));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(androidx.core.content.ContextCompat.getColor(context, R.color.line));
        LayoutInflater.from(context).inflate(R.layout.card_media, card, true);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.setMargins(dp(4), dp(4), dp(4), dp(16));
        addView(card, params);
        return card;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isBusy() { return busy; }
    public void setHostActive(boolean active) {
        hostActive = active;
        if (!active) releasePlayer();
    }

    public void setMedia(GalleryMedia top, GalleryMedia next) {
        if (Objects.equals(topItem, top) && Objects.equals(nextItem, next)) return;
        releasePlayer();
        cancelGesture();
        topItem = top;
        nextItem = next;
        load(frontImage, top);
        load(backImage, next);
        front.setVisibility(top == null ? INVISIBLE : VISIBLE);
        back.setVisibility(next == null ? INVISIBLE : VISIBLE);
        videoToggle.setVisibility(top != null && top.video ? VISIBLE : GONE);
        front.setContentDescription(getResources().getString(R.string.photo_description));
    }

    static String dateLabel(Context context, GalleryMedia item) {
        if (item == null) return "";
        String type = context.getString(item.video ? R.string.media_video : R.string.media_photo);
        String date = item.formattedDate();
        String detail = date == null ? context.getString(R.string.date_unknown)
                : context.getString(item.captureDate ? R.string.capture_date : R.string.added_date, date);
        return type + " · " + detail;
    }

    private void load(ImageView image, GalleryMedia item) {
        if (item == null) {
            Glide.with(this).clear(image);
            image.setImageDrawable(null);
            return;
        }
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, 1080);
        int height = Math.min(getResources().getDisplayMetrics().heightPixels, 1440);
        // For video this decodes a thumbnail only. The back card never creates a player.
        Glide.with(this).asBitmap().load(Uri.parse(item.uri)).override(width, height).fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image).into(image);
    }

    private void toggleVideo() {
        if (!hostActive || !isEnabled() || busy || topItem == null || !topItem.video) return;
        if (player == null) {
            player = new ExoPlayer.Builder(getContext())
                    .setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            playerView.setPlayer(player);
            playerView.setVisibility(VISIBLE);
            videoToggle.setVisibility(GONE);
            playerView.showController();
            player.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean playing) {
                    playerView.setKeepScreenOn(playing);
                }
                @Override public void onPlayerError(PlaybackException error) {
                    releasePlayer();
                    Toast.makeText(getContext(), R.string.video_error, Toast.LENGTH_LONG).show();
                }
            });
            player.setMediaItem(MediaItem.fromUri(topItem.uri));
            player.prepare();
            player.play();
        } else if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED) {
            player.pause();
        } else {
            if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
            player.play();
        }
    }

    public void releasePlayer() {
        playerView.setPlayer(null);
        if (player != null) {
            ExoPlayer previous = player;
            player = null;
            previous.stop();
            previous.release();
        }
        playerView.setVisibility(GONE);
        playerView.setKeepScreenOn(false);
        videoToggle.setText(R.string.play_video);
        videoToggle.setVisibility(topItem != null && topItem.video ? VISIBLE : GONE);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            View controls = playerView.findViewById(R.id.playbackControls);
            Rect bounds = new Rect();
            controllerGesture = player != null && controls != null
                    && controls.getGlobalVisibleRect(bounds)
                    && bounds.contains((int) event.getRawX(), (int) event.getRawY());
            if (controllerGesture) getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (controllerGesture) getParent().requestDisallowInterceptTouchEvent(false);
            controllerGesture = false;
        }
        return handled;
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (videoToggle != null) videoToggle.setEnabled(enabled);
        if (!enabled && playerView != null) releasePlayer();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if (controllerGesture) return false;
        if (!isEnabled() || topItem == null || animating) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX(); downY = event.getRawY(); dragging = false;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && shouldDrag(event)) {
            beginDrag();
            return true;
        }
        // A tap reaches the playback button; a horizontal drag belongs to the deck.
        return false;
    }

    private boolean shouldDrag(MotionEvent event) {
        float dx = Math.abs(event.getRawX() - downX);
        float dy = Math.abs(event.getRawY() - downY);
        return dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() && dx > dy;
    }

    private void beginDrag() {
        if (dragging) return;
        dragging = true;
        releasePlayer();
        setBusy(true);
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (controllerGesture) return true;
        if (!isEnabled() || topItem == null || animating) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY(); dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && shouldDrag(event)) beginDrag();
                if (!dragging) return true;
                float dx = event.getRawX() - downX;
                front.setTranslationX(dx);
                front.setTranslationY((event.getRawY() - downY) * 0.18f);
                front.setRotation(14f * dx / Math.max(1, getWidth()));
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!dragging) { performClick(); return true; }
                float distance = event.getRawX() - downX;
                dragging = false;
                if (Math.abs(distance) > getWidth() * 0.25f) animateOut(distance > 0);
                else returnToCenter();
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = false;
                returnToCenter();
                return true;
            default: return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    public void swipe(boolean keep) {
        if (isEnabled() && !busy && topItem != null) animateOut(keep);
    }

    private void animateOut(boolean keep) {
        releasePlayer();
        animating = true;
        setBusy(true);
        front.animate().translationX((keep ? 1 : -1) * getWidth() * 1.4f)
                .rotation(keep ? 20 : -20).alpha(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    animating = false;
                    if (listener != null) listener.onSwipe(keep);
                    resetTransform();
                    setBusy(false);
                }).start();
    }

    private void returnToCenter() {
        animating = true;
        front.animate().translationX(0).translationY(0).rotation(0).alpha(1)
                .setDuration(180).withEndAction(() -> {
                    animating = false;
                    setBusy(false);
                }).start();
    }

    public void cancelGesture() {
        front.animate().withEndAction(null).cancel();
        animating = false; dragging = false;
        resetTransform();
        setBusy(false);
    }

    private void resetTransform() {
        front.setTranslationX(0); front.setTranslationY(0);
        front.setRotation(0); front.setAlpha(1);
    }

    private void setBusy(boolean value) {
        busy = value;
        if (listener != null) listener.onBusyChanged(value);
    }

    @Override protected void onDetachedFromWindow() {
        releasePlayer();
        cancelGesture();
        super.onDetachedFromWindow();
    }
}
