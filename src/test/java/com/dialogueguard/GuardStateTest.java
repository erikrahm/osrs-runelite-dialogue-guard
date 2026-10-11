package com.dialogueguard;

import static com.dialogueguard.GuardState.Decision.PASS;
import static com.dialogueguard.GuardState.Decision.BLOCK;
import static com.dialogueguard.GuardState.Decision.HOLD;
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
		assertEquals(PASS, state.guardedKeyPressed(1000, true));
		assertEquals(PASS, state.guardedKeyPressed(1050, true));
		assertEquals(PASS, state.guardedKeyPressed(1100, true));
	}

	@Test
	public void eachPageTakesOneKeyAndBlocksTheRest()
	{
		state.dialogueOpened(CHAT);
		assertEquals(PASS, state.guardedKeyPressed(1000, true));      // continues the page
		assertEquals(HOLD, state.guardedKeyPressed(1100, true));      // "Please wait...": could land in chat as it closes
		assertEquals(HOLD, state.guardedKeyPressed(1150, false));     // any guarded key, not just the one it took
		state.dialogueClosed(CHAT);
		state.dialogueOpened(CHAT);                                     // next page
		assertEquals(PASS, state.guardedKeyPressed(1200, true));
		assertEquals(HOLD, state.guardedKeyPressed(1250, true));
		state.dialogueClosed(CHAT);
		assertEquals(BLOCK, state.guardedKeyPressed(1300, true));     // closed: now it's spill-over
	}

	@Test
	public void aSpaceMashedOntoAnOptionMenuDoesNotCostTheOption()
	{
		state.dialogueOpened(CHAT);
		assertEquals(PASS, state.guardedKeyPressed(1000, true));      // space continues the chat page
		state.dialogueClosed(CHAT);
		state.dialogueOpened(OPTIONS);
		assertEquals(PASS, state.guardedKeyPressed(1100, false));     // still mashing space: the menu ignores it
		assertEquals(PASS, state.guardedKeyPressed(1150, false));
		assertEquals(PASS, state.guardedKeyPressed(1200, true));      // the 1 that picks the option
		assertEquals(HOLD, state.guardedKeyPressed(1250, true));
	}

	@Test
	public void aPauseFreesAPageThatIgnoredItsKey()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000, true);                            // a digit the menu has no option for
		state.guardedKeyReleased(1050);
		assertEquals(PASS, state.guardedKeyPressed(1050 + WINDOW + 1, true));
	}

	@Test
	public void aPageThatClosesBeforeItsKeyKeepsTheGuardArmed()
	{
		state.dialogueOpened(CHAT);
		state.guardedKeyPressed(1000, true);
		state.dialogueClosed(CHAT);
		state.dialogueOpened(CHAT);                                     // opened and closed again with no key between
		state.dialogueClosed(CHAT);
		assertEquals(BLOCK, state.guardedKeyPressed(1100, true));
	}

	@Test
	public void aDialogueClosedWithoutAKeyDoesNotArm()
	{
		state.dialogueOpened(OPTIONS);
		state.dialogueClosed(OPTIONS);                                  // walked away, or picked with the mouse
		assertEquals(PASS, state.guardedKeyPressed(1000, true));
	}

	@Test
	public void spillOverAfterTheDialogueEndsIsBlockedWhileStillPressing()
	{
		state.dialogueOpened(OPTIONS);
		assertEquals(PASS, state.guardedKeyPressed(1000, true));
		state.dialogueClosed(OPTIONS);
		assertEquals(BLOCK, state.guardedKeyPressed(1200, true));
		assertEquals(BLOCK, state.guardedKeyPressed(1500, true));
		assertEquals(BLOCK, state.guardedKeyPressed(2000, true));   // each press extends the window
	}

	@Test
	public void aPauseEndsTheGuard()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000, true);
		state.guardedKeyReleased(1050);
		state.dialogueClosed(OPTIONS);
		assertEquals(PASS, state.guardedKeyPressed(1050 + WINDOW + 1, true));
	}

	@Test
	public void aHeldKeyKeepsTheGuardAliveUntilRelease()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000, true);                            // pressed and held
		state.dialogueClosed(OPTIONS);
		// OS auto-repeat only starts after a long delay, well past the window
		assertEquals(BLOCK, state.guardedKeyPressed(1000 + WINDOW + 500, true));
		state.guardedKeyReleased(2200);
		assertEquals(BLOCK, state.guardedKeyPressed(2200 + WINDOW - 1, true));
		state.guardedKeyReleased(2800);
		assertEquals(PASS, state.guardedKeyPressed(2800 + WINDOW + 1, true));
	}

	@Test
	public void aLostReleaseCannotPinTheGuardForever()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000, true);
		state.dialogueClosed(OPTIONS);
		// no release ever arrives (focus moved); after the stale limit the guard lets go
		assertEquals(PASS, state.guardedKeyPressed(1000 + GuardState.HELD_STALE_MS + 1, true));
	}

	@Test
	public void aLetterEndsTheGuardAtOnce()
	{
		state.dialogueOpened(OPTIONS);
		state.guardedKeyPressed(1000, true);
		state.dialogueClosed(OPTIONS);
		state.otherKeyPressed();                                  // "h" of "hello"
		assertEquals(PASS, state.guardedKeyPressed(1100, true));        // the space in "hello world"
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
		state.guardedKeyPressed(1000, true);
		state.reset();
		assertFalse(state.dialogueOpen());
		assertEquals(PASS, state.guardedKeyPressed(1010, true));
	}
}
