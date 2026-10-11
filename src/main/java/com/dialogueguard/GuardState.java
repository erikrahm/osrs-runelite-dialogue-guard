/*
 * Dialogue Guard — the decision logic, kept free of RuneLite so it can be read and tested on its own.
 * BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Decides whether a dialogue key (1-9, space, enter) should reach the game.
 *
 * <p>A dialogue page accepts exactly one guarded key. Once one has been sent the guard is <em>armed</em> and
 * every further guarded key is swallowed until a new page loads (which needs a key of its own) or the player
 * stops pressing guarded keys for the window. A key that is still held down counts as pressing, so the pause
 * only starts at release. Any other key ends the guard immediately: that is deliberate typing.
 *
 * <p>Dialogue events arrive on the client thread and key events on the AWT thread; every field is volatile or
 * concurrent and no decision needs more than one of them to be consistent, so no locking is required.
 */
final class GuardState
{
	/** What to do with a guarded key press. */
	enum Decision
	{
		/** Let it through: a dialogue page is waiting for it, or the guard has lapsed. */
		PASS,
		/** Swallow it: spill-over from a dialogue that already had its key. */
		SWALLOW
	}

	/** No key event for this long means the release was lost (focus change); a stuck "held" must not pin the guard. */
	static final long HELD_STALE_MS = 3000;

	private final LongSupplier windowMs;

	/** dialogue interface groups currently open — a set, so a page reloaded in place can't double-count */
	private final Set<Integer> openDialogues = ConcurrentHashMap.newKeySet();
	/** a guarded key has been sent to the page that is open (or was just open) */
	private volatile boolean armed;
	/** when the last guarded key was pressed or released */
	private volatile long lastGuardedAt;
	/** a guarded key is physically held down */
	private volatile boolean guardedKeyHeld;

	GuardState(LongSupplier windowMs)
	{
		this.windowMs = windowMs;
	}

	void reset()
	{
		openDialogues.clear();
		armed = false;
		lastGuardedAt = 0;
		guardedKeyHeld = false;
	}

	boolean dialogueOpen()
	{
		return !openDialogues.isEmpty();
	}

	/** A dialogue page appeared (or re-appeared): it needs a key of its own. */
	void dialogueOpened(int group)
	{
		openDialogues.add(group);
		armed = false;
	}

	/** A dialogue page went away. Stays armed: the guarded keys that follow are the spill-over this exists to catch. */
	void dialogueClosed(int group)
	{
		openDialogues.remove(group);
	}

	/** A non-guarded, non-modifier key: the player is typing on purpose. */
	void otherKeyPressed()
	{
		armed = false;
	}

	/** A guarded key was pressed at {@code now}. */
	Decision guardedKeyPressed(long now)
	{
		boolean inWindow = withinWindow(now);
		guardedKeyHeld = true;
		if (dialogueOpen() && !armed)
		{
			armed = true;
			lastGuardedAt = now;
			return Decision.PASS;
		}
		if (armed && inWindow)
		{
			lastGuardedAt = now;
			return Decision.SWALLOW;
		}
		armed = false;
		return Decision.PASS;
	}

	/** A guarded key was released at {@code now}: the pause starts here, not at the press. */
	void guardedKeyReleased(long now)
	{
		guardedKeyHeld = false;
		lastGuardedAt = now;
	}

	/** Mark {@code now} as guarded-key activity without changing the armed state (used by the leading-space rule). */
	void touch(long now)
	{
		lastGuardedAt = now;
	}

	private boolean withinWindow(long now)
	{
		long since = now - lastGuardedAt;
		if (guardedKeyHeld && since < HELD_STALE_MS)
		{
			return true;
		}
		return since < windowMs.getAsLong();
	}
}
