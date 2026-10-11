package com.dialogueguard;

import static com.dialogueguard.GuardState.Decision.PASS;
import static com.dialogueguard.GuardState.Decision.SWALLOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class GuardStateTest
{
	private static final int CHAT = 231;
	private static final int OPTIONS = 219;
	private static final long WINDOW = 600;

	private final GuardState state = new GuardState(() -> WINDOW);

	@Test
	public void typingWithNoDialogueIsNeverTouched()
	{
		assertEquals(PASS, state.guardedKeyPressed(1000));
		assertEquals(PASS, state.guardedKeyPressed(1050));
		assertEquals(PASS, state.guardedKeyPressed(1100));
	}

	@Test
	public void eachPageTakesOneKeyAndSwallowsTheRest()
	{
		state.dialogueOpened(CHAT);
		assertEquals(PASS, state.guardedKeyPressed(1000));      // continues the page
		assertEquals(SWALLOW, state.guardedKeyPressed(1100));   // page hasn't changed yet: spill-over
		state.dialogueClosed(CHAT);
		state.dialogueOpened(CHAT);                               // next page
		assertEquals(PASS, state.guardedKeyPressed(1700));
		assertEquals(SWALLOW, state.guardedKeyPressed(1750));
	}

	@Test
	public void spillOverAfterTheDialogueEndsIsSwallowedWhileStillPressing()
	{
		state.dialogueOpened(OPTIONS);
		assertEquals(PASS, state.guardedKeyPressed(1000));
		state.dialogueClosed(OPTIONS);
		assertEquals(SWALLOW, state.guardedKeyPressed(1200));
		assertEquals(SWALLOW, state.guardedKeyPressed(1500));
		assertEquals(SWALLOW, state.guardedKeyPressed(2000));   // each press extends the window
	}

	@Test
	public void aPauseEndsTheGuard()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000);
		state.guardedKeyReleased(1050);
		state.dialogueClosed(OPTIONS);
		assertEquals(PASS, state.guardedKeyPressed(1050 + WINDOW + 1));
	}

	@Test
	public void aHeldKeyKeepsTheGuardAliveUntilRelease()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000);                            // pressed and held
		state.dialogueClosed(OPTIONS);
		// OS auto-repeat only starts after a long delay, well past the window
		assertEquals(SWALLOW, state.guardedKeyPressed(1000 + WINDOW + 500));
		state.guardedKeyReleased(2200);
		assertEquals(SWALLOW, state.guardedKeyPressed(2200 + WINDOW - 1));
		state.guardedKeyReleased(2800);
		assertEquals(PASS, state.guardedKeyPressed(2800 + WINDOW + 1));
	}

	@Test
	public void aLostReleaseCannotPinTheGuardForever()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000);
		state.dialogueClosed(OPTIONS);
		// no release ever arrives (focus moved); after the stale limit the guard lets go
		assertEquals(PASS, state.guardedKeyPressed(1000 + GuardState.HELD_STALE_MS + 1));
	}

	@Test
	public void aLetterEndsTheGuardAtOnce()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000);
		state.dialogueClosed(OPTIONS);
		state.otherKeyPressed();                                  // "h" of "hello"
		assertEquals(PASS, state.guardedKeyPressed(1100));        // the space in "hello world"
	}

	@Test
	public void aPageReloadedInPlaceDoesNotLeaveADialogueStuckOpen()
	{
		state.dialogueOpened(CHAT);
		state.dialogueOpened(CHAT);     // loaded twice without a close in between
		state.dialogueClosed(CHAT);
		assertFalse(state.dialogueOpen());
	}

	@Test
	public void resetClearsEverything()
	{
		state.dialogueOpened(CHAT);
		state.guardedKeyPressed(1000);
		state.reset();
		assertFalse(state.dialogueOpen());
		assertEquals(PASS, state.guardedKeyPressed(1010));
	}
}
