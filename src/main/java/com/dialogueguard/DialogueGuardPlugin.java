/*
 * Dialogue Guard — stops dialogue-skipping keys from spilling into the chat box.
 * BSD-2-Clause, see LICENSE.
 *
 * This class is the RuneLite wiring: it feeds dialogue open/close events and key events into GuardState (the
 * decision logic) and acts on its answer. Two extras live here because they need the client: the leading-space
 * rule (space is dropped while the chat box is empty) and clearing digit-only leftovers when a dialogue opens.
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
import net.runelite.api.gameval.InterfaceID;
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
	private static final String KEY_SAVED = "keysSaved";
	/** the all-time counter is written at most this often while keys are being swallowed (and always on shutdown) */
	private static final long PERSIST_INTERVAL_MS = 1000;
	/** chat-box contents that are safe to clear: digits and spaces only */
	private static final Pattern JUNK = Pattern.compile("[0-9 ]+");

	/** Interface groups that take a key to advance: NPC/player chat, option menus, message/sprite boxes, level-ups, make-x. */
	private static final int[] DIALOGUE_GROUPS = {
		InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.MESSAGEBOX,
		InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY, InterfaceID.SKILLMULTI,
	};

	@Inject private Client client;
	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private OverlayManager overlayManager;
	@Inject private DialogueGuardConfig config;
	@Inject private DialogueGuardHint hint;

	private final GuardState state = new GuardState(() -> config.windowMs());
	/** keys swallowed since the plugin was installed; persisted in the config */
	private final AtomicInteger counter = new AtomicInteger();

	/** the decision made in keyPressed, so keyTyped / keyReleased for the same key follow it */
	private volatile boolean swallowingCurrentKey;
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
		swallowingCurrentKey = false;
		counter.set(loadSaved());
		keyManager.registerKeyListener(this);
		overlayManager.add(hint);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(hint);
		persistSaved();
		state.reset();
	}

	/** Keys swallowed all-time, for the hint. */
	int swallowedCount()
	{
		return counter.get();
	}

	// ------------------------------------------------------------------ all-time counter

	private int loadSaved()
	{
		try
		{
			String v = configManager.getConfiguration(CONFIG_GROUP, KEY_SAVED);
			return v == null ? 0 : Integer.parseInt(v);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void persistSaved()
	{
		configManager.setConfiguration(CONFIG_GROUP, KEY_SAVED, String.valueOf(counter.get()));
	}

	/** One more key eaten. The hint only flashes for dialogue spill-over; a dropped leading space is silent. */
	private void recordSwallow(long now, boolean flash)
	{
		counter.incrementAndGet();
		if (now - lastPersist > PERSIST_INTERVAL_MS)
		{
			lastPersist = now;
			persistSaved();
		}
		if (flash)
		{
			hint.flash(counter.get());
		}
	}

	// ------------------------------------------------------------------ dialogue state (client thread)

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			state.reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (isDialogue(e.getGroupId()))
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
		if (isDialogue(e.getGroupId()))
		{
			state.dialogueClosed(e.getGroupId());
		}
	}

	private static boolean isDialogue(int groupId)
	{
		for (int g : DIALOGUE_GROUPS)
		{
			if (g == groupId)
			{
				return true;
			}
		}
		return false;
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
		if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9)
		{
			return config.guardDigits();
		}
		if (keyCode == KeyEvent.VK_SPACE)
		{
			return config.guardSpace();
		}
		if (keyCode == KeyEvent.VK_ENTER)
		{
			return config.guardEnter();
		}
		return false;
	}

	private static boolean isModifier(int keyCode)
	{
		return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
			|| keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_CAPS_LOCK || keyCode == KeyEvent.VK_UNDEFINED;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		swallowingCurrentKey = false;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int code = e.getKeyCode();
		if (!isGuarded(code))
		{
			if (!isModifier(code))
			{
				state.otherKeyPressed();
			}
			return;
		}
		long now = System.currentTimeMillis();
		if (state.guardedKeyPressed(now) == GuardState.Decision.SWALLOW)
		{
			swallow(e, now, true);
			return;
		}
		// Not spill-over. One more rule for space: a message never starts with one.
		if (code == KeyEvent.VK_SPACE && config.noLeadingSpace() && !state.dialogueOpen() && chatInputIsBlank())
		{
			state.touch(now);
			swallow(e, now, false);
		}
	}

	private void swallow(KeyEvent e, long now, boolean flash)
	{
		swallowingCurrentKey = true;
		recordSwallow(now, flash);
		e.consume();
	}

	/** True when the chat box has nothing typed in it yet (or only spaces). */
	private boolean chatInputIsBlank()
	{
		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		return typed == null || typed.trim().isEmpty();
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (swallowingCurrentKey)
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
		if (swallowingCurrentKey)
		{
			e.consume();
		}
	}
}
