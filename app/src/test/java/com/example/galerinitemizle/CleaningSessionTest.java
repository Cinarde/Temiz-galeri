package com.example.galerinitemizle;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class CleaningSessionTest {
    private CleaningSession session() {
        CleaningSession state = new CleaningSession();
        state.reconcile(Arrays.asList("A", "B", "C", "D"));
        return state;
    }

    @Test public void rightSwipeDoesNotQueueDeletion() {
        CleaningSession state = session();
        state.swipe(true);
        assertEquals("B", state.top());
        assertEquals(0, state.trashCount());
        assertTrue(state.canUndo());
    }

    @Test public void undoRestoresLastLeftSwipeEvenAfterRightSwipes() {
        CleaningSession state = session();
        state.swipe(false); // A queued
        state.swipe(true);  // B kept
        state.swipe(false); // C queued
        state.swipe(true);  // D kept, deck exhausted
        state.undoLastTrash();
        assertEquals("C", state.top());
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
        state.undoLastTrash();
        assertEquals("A", state.top());
        assertEquals("C", state.next());
        assertEquals(0, state.trashCount());
    }

    @Test public void repeatedUndoAndDiscardNeverDuplicatesQueue() {
        CleaningSession state = session();
        state.swipe(false);
        state.undoLastTrash();
        state.swipe(false);
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
        state.undoLastTrash();
        state.undoLastTrash();
        assertEquals(4, state.remainingCount());
    }

    @Test public void requestingOrCancellingBatchDoesNotConsumeQueue() {
        CleaningSession state = session();
        state.swipe(false);
        state.swipe(false);
        assertEquals(Collections.singletonList("A"), state.trashBatch(1));
        assertEquals(2, state.trashCount());
        state.undoLastTrash();
        assertEquals("B", state.top());
    }

    @Test public void confirmationRemovesOnlyConfirmedItemsAndTheirUndoHistory() {
        CleaningSession state = session();
        state.swipe(false);
        state.swipe(false);
        state.confirmTrashed(state.trashBatch(1));
        assertEquals(Collections.singletonList("B"), state.trashBatch(100));
        state.undoLastTrash();
        assertEquals("B", state.top());
        assertFalse(state.canUndo());
        assertEquals(3, state.remainingCount());
    }

    @Test public void refreshKeepsDecisionsAndUndoOrderButDropsInaccessibleItems() {
        CleaningSession state = session();
        state.swipe(false); // A queued
        state.swipe(true);  // B kept
        state.swipe(false); // C queued
        state.undoLastTrash(); // C on top
        state.reconcile(Arrays.asList("E", "D", "C", "B")); // A no longer accessible
        assertEquals("C", state.top());
        assertEquals("D", state.next());
        assertEquals(3, state.remainingCount()); // C, D, E; B stays kept
        assertEquals(1, state.trashCount()); // A stays pending despite temporary loss of access.
        assertTrue(state.canUndo()); // Accessible right-swipe B can still be reversed.
    }

    @Test public void emptySessionActionsAreHarmless() {
        CleaningSession state = new CleaningSession();
        state.swipe(false);
        state.undoLastTrash();
        assertNull(state.top());
        assertNull(state.next());
        assertTrue(state.trashBatch(100).isEmpty());
    }

    @Test public void restoringSpecificPhotoPreservesRemainingUndoHistory() {
        CleaningSession state = session();
        state.swipe(false); // A
        state.swipe(false); // B
        state.swipe(false); // C
        state.restore("B");
        state.restore("B"); // A rapid second tap must be harmless.
        assertEquals("B", state.top());
        assertEquals(Arrays.asList("A", "C"), state.trashBatch(100));
        state.undoLastTrash();
        assertEquals("C", state.top());
        assertEquals("B", state.next());
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
    }

    @Test public void historyFilteringDoesNotRemoveAccessibleQueuedPhotos() {
        CleaningSession state = session();
        state.swipe(true); // A already handled
        state.swipe(false); // B queued
        state.reconcile(Arrays.asList("A", "B", "C", "D"), Arrays.asList("C", "D"));
        assertEquals("C", state.top());
        assertEquals(2, state.remainingCount());
        assertEquals(Collections.singletonList("B"), state.trashBatch(100));
        assertEquals("B", state.undoLastTrash());
    }

    @Test public void restoredQueueKeepsLastLeftSwipeOrderAndStaysOutsideDeck() {
        CleaningSession state = new CleaningSession();
        state.restoreQueued(Arrays.asList("A", "B"));
        state.reconcile(Arrays.asList("A", "B", "C", "D"), Arrays.asList("C", "D"));
        assertEquals("C", state.top());
        assertEquals("B", state.undoLastTrash());
        assertEquals("B", state.top());
        state.reconcile(Arrays.asList("A", "B", "C", "D"), Arrays.asList("B", "C", "D"));
        assertEquals("B", state.top());
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
    }

    @Test public void undoReversesBothDirectionsInChronologicalOrder() {
        CleaningSession state = session();
        state.swipe(false); // A
        state.swipe(true); // B
        assertEquals("B", state.undoLastSwipe());
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
        assertEquals("A", state.undoLastSwipe());
        assertEquals("A", state.top());
        assertEquals("B", state.next());
        assertEquals(4, state.remainingCount());
        assertFalse(state.canUndo());
    }

    @Test public void unreadablePendingPhotoDoesNotDisappearOnRefresh() {
        CleaningSession state = session();
        state.swipe(false);
        state.reconcile(Collections.emptyList(), Collections.emptyList());
        assertEquals(Collections.singletonList("A"), state.trashBatch(100));
        assertNull(state.top());
        assertFalse(state.canUndo());
        state.restoreSwipeOrder(Collections.singletonList("A"));
        state.reconcile(Arrays.asList("A", "B"), Collections.singletonList("B"));
        assertEquals("A", state.undoLastSwipe());
    }
}
