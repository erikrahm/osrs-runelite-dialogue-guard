/*
 * Dialogue Guard: stops dialogue-skipping keys from spilling into the chat box.
 * BSD-2-Clause, see LICENSE.
 *
 * This class is the RuneLite wiring: it feeds dialogue open/close events and key events into GuardState (the
 * decision logic) and acts on its answer. Two extras live here because they need the client: the leading-space
 * rule (space is dropped while the chat box is empty) and clearing digit-only leftovers when a dialogue opens.
 *
 * Only presses (and the keyTyped that follows) are ever consumed. Releases always go through: a stray release is
 * harmless, while a swallowed one could leave the client or another plugin thinking a key is still held.
 */
package com.dialogueguard;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Dialogue Guard",
	description = "Stops dialogue-skipping keys (1-9, space, enter) from spilling into the chat box when a dialogue ends while you're still pressing them",
	tags = {"dialogue", "chat", "keyboard", "spam", "qol"}
)
public class DialogueGuardPlugin extends Plugin implements KeyListener
{
	static final String CONFIG_GROUP = "dialogueguard";
	/** config key the all-time counter is persisted under (not shown in the settings panel) */
	private static final String KEY_BLOCKED = "keysBlocked";
	/** the all-time counter is written at most this often while keys are being blocked (and always on shutdown) */
	private static final long PERSIST_INTERVAL_MS = 1000;
	/** chat-box contents that are safe to clear: digits and spaces only */
	private static final Pattern JUNK = Pattern.compile("[0-9 ]+");

	@Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private OverlayManager overlayManager;
	@Inject private DialogueGuardConfig config;
	@Inject private DialogueGuardHint hint;

	private final GuardState state = new GuardState(() -> config.windowMs());
	/** keys blocked since the plugin was installed; persisted in the config */
	private final AtomicInteger counter = new AtomicInteger();

	/** the decision made in keyPressed, so the keyTyped for the same key follows it */
	private volatile boolean blockingCurrentKey;
	private volatile long lastPersist;

	@Provides
	DialogueGuardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DialogueGuardConfig.class);
	}

	@Override
	protected void startUp()
	{
		state.reset();
		blockingCurrentKey = false;
		counter.set(loadBlocked());
		keyManager.registerKeyListener(this);
		overlayManager.add(hint);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(hint);
		persistBlocked();
		state.reset();
	}

	// ------------------------------------------------------------------ all-time counter

	private int loadBlocked()
	{
		try
		{
			String v = configManager.getConfiguration(CONFIG_GROUP, KEY_BLOCKED);
			return v == null ? 0 : Integer.parseInt(v);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void persistBlocked()
	{
		configManager.setConfiguration(CONFIG_GROUP, KEY_BLOCKED, String.valueOf(counter.get()));
	}

	/** One more key kept out of the chat box. The hint only flashes for dialogue spill-over; a dropped leading space is silent. */
	private void recordBlock(long now, boolean flash)
	{
		counter.incrementAndGet();
		if (now - lastPersist > PERSIST_INTERVAL_MS)
		{
			lastPersist = now;
			persistBlocked();
		}
		if (flash && config.showHint())
		{
			hint.flash(counter.get());
		}
	}

	// ------------------------------------------------------------------ dialogue state (client thread)

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		// Only where interfaces really go away without close events. A region load (LOADING) keeps an open dialogue
		// open, and forgetting it would let the leading-space rule eat the space that continues it.
		if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			state.reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (DialogueKeys.isDialogue(e.getGroupId()))
		{
			state.dialogueOpened(e.getGroupId());
			if (config.clearJunkOnDialogue())
			{
				clearJunkFromChat();
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (DialogueKeys.isDialogue(e.getGroupId()))
		{
			state.dialogueClosed(e.getGroupId());
		}
	}

	/** Wipe the chat box if all it holds is digits and spaces left over from an earlier dialogue. Client thread. */
	private void clearJunkFromChat()
	{
		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		if (typed == null || typed.isEmpty() || !JUNK.matcher(typed).matches())
		{
			return;
		}
		client.setVarcStrValue(VarClientID.CHATINPUT, "");
		client.runScript(ScriptID.CHAT_PROMPT_INIT);   // redraw the input line
	}

	// ------------------------------------------------------------------ keys (AWT thread)

	private boolean isGuarded(int keyCode)
	{
		switch (DialogueKeys.kind(keyCode))
		{
			case DIGIT:
				return config.guardDigits();
			case SPACE:
				return config.guardSpace();
			case ENTER:
				return config.guardEnter();
			default:
				return false;
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		blockingCurrentKey = false;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int code = e.getKeyCode();
		if (!isGuarded(code))
		{
			if (!DialogueKeys.isModifier(code))
			{
				state.otherKeyPressed();
			}
			return;
		}
		long now = System.currentTimeMillis();
		switch (state.guardedKeyPressed(now, DialogueKeys.pageAccepts(state.page(), code)))
		{
			case HOLD:
				// the open page already has its key: blocked, but not counted or shown
				blockingCurrentKey = true;
				e.consume();
				return;
			case BLOCK:
				block(e, now, true);
				return;
			case PASS:
				break;
		}
		// Not spill-over. One more rule for space: a message never starts with one.
		if (DialogueKeys.kind(code) == DialogueKeys.Kind.SPACE && config.noLeadingSpace() && !state.dialogueOpen() && chatInputIsBlank())
		{
			state.touch(now);
			block(e, now, false);
		}
	}

	private void block(KeyEvent e, long now, boolean flash)
	{
		blockingCurrentKey = true;
		recordBlock(now, flash);
		e.consume();
	}

	/**
	 * True when the chat line has nothing typed in it yet (or only spaces) and is where keys are going. A chat-box
	 * prompt (bank or GE search, "Enter amount", a name) keeps its text elsewhere and leaves the chat line empty,
	 * so the rule must stand aside while one is open or it would eat the space in "dragon bones".
	 */
	private boolean chatInputIsBlank()
	{
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0)
		{
			return false;
		}
		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		return typed == null || typed.trim().isEmpty();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (blockingCurrentKey)
		{
			e.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (isGuarded(e.getKeyCode()))
		{
			state.guardedKeyReleased(System.currentTimeMillis());
		}
	}
}
