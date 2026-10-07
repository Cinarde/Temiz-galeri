package com.example.galerinitemizle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Main-thread session state. Stores URI strings, never Bitmaps or file contents. */
public final class CleaningSession {
    private final ArrayDeque<String> deck = new ArrayDeque<>();
    private final LinkedHashSet<String> trash = new LinkedHashSet<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> swipeOrder = new ArrayDeque<>();
    private final Set<String> kept = new HashSet<>();

    public void reconcile(Collection<String> accessible) {
        reconcile(accessible, accessible);
    }

    /** Accessibility applies to the queue; history filtering applies only to the deck. */
    public void reconcile(Collection<String> accessible, Collection<String> candidates) {
        Set<String> available = new HashSet<>(accessible);
        Set<String> eligible = new HashSet<>(candidates);
        deck.removeIf(uri -> !available.contains(uri) || !eligible.contains(uri));
        // A temporary permission change is not a deletion or a cancellation.
        // Keep pending choices visible, even when their thumbnails cannot be read.
        undo.removeIf(uri -> !trash.contains(uri));
        swipeOrder.removeIf(uri -> !available.contains(uri));
        kept.retainAll(available);
        Set<String> known = new HashSet<>(deck);
        known.addAll(trash);
        known.addAll(kept);
        for (String uri : candidates) {
            if (available.contains(uri) && known.add(uri)) deck.addLast(uri);
        }
    }

    public void restoreQueued(Collection<String> queued) {
        trash.clear();
        undo.clear();
        for (String uri : queued) {
            if (trash.add(uri)) undo.addLast(uri);
        }
        deck.removeIf(trash::contains);
        kept.removeAll(trash);
    }

    public void restoreSwipeOrder(Collection<String> orderedUris) {
        swipeOrder.clear();
        swipeOrder.addAll(new LinkedHashSet<>(orderedUris));
    }

    public void reviewKeptAgain() {
        kept.clear();
        swipeOrder.removeIf(uri -> !trash.contains(uri));
    }

    /** The center button reverses the latest decision, in either direction. */
    public String undoLastSwipe() {
        String uri = swipeOrder.pollLast();
        if (uri == null) return null;
        trash.remove(uri);
        undo.remove(uri);
        kept.remove(uri);
        deck.remove(uri);
        deck.addFirst(uri);
        return uri;
    }

    public String top() { return deck.peekFirst(); }

    public String next() {
        Iterator<String> it = deck.iterator();
        if (it.hasNext()) it.next();
        return it.hasNext() ? it.next() : null;
    }

    public void swipe(boolean keep) {
        String uri = deck.pollFirst();
        if (uri == null) return;
        swipeOrder.remove(uri);
        swipeOrder.addLast(uri);
        if (keep) kept.add(uri);
        else if (trash.add(uri)) undo.addLast(uri);
    }

    /** Undo the latest LEFT swipe, even when right swipes happened afterwards. */
    public String undoLastTrash() {
        String uri = undo.pollLast();
        if (uri != null && trash.remove(uri)) {
            swipeOrder.remove(uri);
            deck.addFirst(uri);
            return uri;
        }
        return null;
    }

    public boolean restore(String uri) {
        if (trash.remove(uri)) {
            swipeOrder.remove(uri);
            undo.remove(uri);
            deck.addFirst(uri);
            return true;
        }
        return false;
    }

    public List<String> trashBatch(int limit) {
        List<String> batch = new ArrayList<>();
        for (String uri : trash) {
            if (batch.size() >= limit) break;
            batch.add(uri);
        }
        return batch;
    }

    public void confirmTrashed(Collection<String> confirmed) {
        Set<String> removed = new HashSet<>(confirmed);
        trash.removeAll(removed);
        undo.removeIf(removed::contains);
        swipeOrder.removeIf(removed::contains);
        deck.removeIf(removed::contains);
    }

    public int remainingCount() { return deck.size(); }
    public int trashCount() { return trash.size(); }
    public boolean canUndo() { return !swipeOrder.isEmpty(); }
}
