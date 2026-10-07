package com.example.galerinitemizle;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.card.MaterialCardView;
import java.util.Objects;

/** Two reusable cards; no original-sized bitmaps or disk copies. */
public class SwipeDeckView extends FrameLayout {
    public interface Listener {
        void onSwipe(boolean keep);
        void onBusyChanged(boolean busy);
    }
    private final MaterialCardView front, back;
    private final ImageView frontImage, backImage;
    private String topUri, nextUri;
    private Listener listener;
    private float downX, downY;
    private boolean busy, animating;

    public SwipeDeckView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        back = card(context);
        front = card(context);
        backImage = (ImageView) back.getChildAt(0);
        frontImage = (ImageView) front.getChildAt(0);
        back.setScaleX(0.94f);
        back.setScaleY(0.94f);
        back.setTranslationY(dp(12));
        front.setVisibility(INVISIBLE);
        back.setVisibility(INVISIBLE);
        front.setOnTouchListener((view, event) -> handleTouch(event));
    }

    private MaterialCardView card(Context context) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(24));
        card.setCardElevation(dp(1));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(androidx.core.content.ContextCompat.getColor(context, R.color.line));
        card.setClickable(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.setMargins(dp(4), dp(4), dp(4), dp(16));
        addView(card, params);
        return card;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    public void setListener(Listener listener) { this.listener = listener; }
    public boolean isBusy() { return busy; }

    public void setPhotos(String top, String next) {
        if (Objects.equals(topUri, top) && Objects.equals(nextUri, next)) return;
        cancelGesture();
        topUri = top;
        nextUri = next;
        load(frontImage, top);
        load(backImage, next);
        front.setVisibility(top == null ? INVISIBLE : VISIBLE);
        back.setVisibility(next == null ? INVISIBLE : VISIBLE);
        front.setContentDescription(getResources().getString(R.string.photo_description));
    }

    private void load(ImageView image, String uri) {
        if (uri == null) {
            Glide.with(this).clear(image);
            image.setImageDrawable(null);
            return;
        }
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, 1080);
        int height = Math.min(getResources().getDisplayMetrics().heightPixels, 1440);
        Glide.with(this).asBitmap().load(Uri.parse(uri))
                .override(width, height).fitCenter()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image).into(image);
    }

    private boolean handleTouch(MotionEvent event) {
        if (!isEnabled() || topUri == null || animating) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY();
                setBusy(true);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX;
                front.setTranslationX(dx);
                front.setTranslationY((event.getRawY() - downY) * 0.18f);
                front.setRotation(14f * dx / Math.max(1, getWidth()));
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                float distance = event.getRawX() - downX;
                if (Math.abs(distance) > getWidth() * 0.25f) animateOut(distance > 0);
                else {
                    if (Math.abs(distance) < ViewConfiguration.get(getContext()).getScaledTouchSlop()) front.performClick();
                    returnToCenter();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                returnToCenter();
                return true;
            default: return true;
        }
    }

    public void swipe(boolean keep) {
        if (isEnabled() && !busy && topUri != null) animateOut(keep);
    }

    private void animateOut(boolean keep) {
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
        animating = false;
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
        cancelGesture();
        super.onDetachedFromWindow();
    }
}
