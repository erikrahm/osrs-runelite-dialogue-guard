/*
 * Dialogue Guard: the decision logic, kept free of RuneLite so it can be read and tested on its own.
 * BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Decides whether a dialogue key (1-9, space, enter) should reach the game.
 *
 * <p>A dialogue page is <em>taken</em> by the first key it accepts (space continues a chat page, a digit picks
 * an option). A key the page ignores, such as a space mashed onto an option menu, goes through without taking
 * it, so the {@code 1} that follows still works. Once a page is taken every further guarded key is blocked until
 * the next page loads. That is not just tidiness: the plugin sees a key before the client handles it, so a key
 * let through just as the last page closes would land in the chat box.
 *
 * <p>Every guarded key pressed on a dialogue also <em>arms</em> the guard. After the dialogue closes, an armed
 * guard blocks further guarded keys until the player stops pressing them for the window. A pause of the same
 * length also frees a taken page, in case it ignored the key after all. A key that is still held down counts as
 * pressing, so the pause only starts at release. Any other key ends the guard immediately: that is deliberate
 * typing.
 *
 * <p>Dialogue events arrive on the client thread and key events on the AWT thread; every field is volatile or
 * concurrent and no decision needs more than one of them to be consistent, so no locking is required.
 */
final class GuardState
{
	/** What to do with a guarded key press. */
	enum Decision
	{
		/** Let it through: a dialogue is open to take it, or the guard has lapsed. */
		PASS,
		/** Block it quietly: the open page already has its key, and would mostly ignore this one anyway. */
		HOLD,
		/** Block it: spill-over from a dialogue that just closed, which would otherwise land in the chat box. */
		BLOCK
	}

	/** No key event for this long means the release was lost (focus change); a stuck "held" must not pin the guard. */
	static final long HELD_STALE_MS = 3000;

	private final LongSupplier windowMs;

	/** dialogue interface groups currently open. A set, so a page reloaded in place cannot double-count. */
	private final Set<Integer> openDialogues = ConcurrentHashMap.newKeySet();
	/** the most recently opened page */
	private volatile int page = -1;
	/** the open page has had a key it accepts */
	private volatile boolean pageTaken;
	/** guarded keys were pressed on the dialogue that is open (or was just open) */
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
		page = -1;
		pageTaken = false;
		armed = false;
		lastGuardedAt = 0;
		guardedKeyHeld = false;
	}

	boolean dialogueOpen()
	{
		return !openDialogues.isEmpty();
	}

	/** The page the next guarded key goes to, or -1 if none has opened. */
	int page()
	{
		return page;
	}

	/**
	 * A dialogue page appeared (or re-appeared): it is waiting for a key. The guard stays armed: a page can open and
	 * close again before a key reaches it, and the player mashing through it is still mashing.
	 */
	void dialogueOpened(int group)
	{
		openDialogues.add(group);
		page = group;
		pageTaken = false;
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

	/** A guarded key was pressed at {@code now}; {@code pageAccepts} says whether the open page acts on it. */
	Decision guardedKeyPressed(long now, boolean pageAccepts)
	{
		boolean inWindow = withinWindow(now);
		guardedKeyHeld = true;
		if (dialogueOpen())
		{
			armed = true;
			lastGuardedAt = now;
			if (pageTaken && inWindow)
			{
				return Decision.HOLD;
			}
			pageTaken = pageAccepts;
			return Decision.PASS;
		}
		if (armed && inWindow)
		{
			lastGuardedAt = now;
			return Decision.BLOCK;
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
