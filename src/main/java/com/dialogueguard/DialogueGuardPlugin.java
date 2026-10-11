/*
 * Dialogue Guard — stops dialogue-skipping keys from spilling into the chat box.
 * BSD-2-Clause, see LICENSE.
 *
 * How it decides: a dialogue page accepts exactly one guarded key (1-9, space, enter). Once one has been sent,
 * the plugin is "armed" and swallows every further guarded key until either a new dialogue page loads (which
 * needs a key of its own) or you stop pressing guarded keys for the configured window. Any other key — a letter —
 * ends the guard at once, because that is clearly deliberate typing. Nothing is ever swallowed unless a dialogue
 * key has just been pressed, so normal chat is untouched.
 */
package com.dialogueguard;

import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
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

	/** Interface groups that take a key to advance: NPC/player chat, option menus, message/sprite boxes, level-ups, make-x. */
	private static final int[] DIALOGUE_GROUPS = {
		InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.MESSAGEBOX,
		InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY, InterfaceID.SKILLMULTI,
	};

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private OverlayManager overlayManager;
	@Inject private DialogueGuardConfig config;
	@Inject private DialogueGuardHint hint;

	/** how many dialogue interfaces are open right now (client thread writes, key thread reads) */
	private volatile int openDialogues;
	/** a guarded key has been sent to the page that is open (or was just open); further ones are spill-over */
	private volatile boolean armed;
	/** when the last guarded key was pressed, armed or not */
	private volatile long lastGuardedAt;
	/** the decision made in keyPressed, so keyTyped / keyReleased for the same key follow it */
	private volatile boolean swallowingCurrentKey;
	/** a guarded key is physically held down (pressed, no release yet): the pause can't start until it's let go */
	private volatile boolean guardedKeyHeld;
	/** if no key event arrives for this long we assume the release was lost (focus change), so a stuck "held" can't pin the guard */
	private static final long HELD_STALE_MS = 3000;

	/** keys swallowed since the plugin was installed; persisted in the config */
	private final AtomicInteger swallowed = new AtomicInteger();

	@Provides
	DialogueGuardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DialogueGuardConfig.class);
	}

	@Override
	protected void startUp()
	{
		reset();
		swallowed.set(loadSaved());
		keyManager.registerKeyListener(this);
		overlayManager.add(hint);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(this);
		overlayManager.remove(hint);
		persistSaved();
		reset();
	}

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
		configManager.setConfiguration(CONFIG_GROUP, KEY_SAVED, String.valueOf(swallowed.get()));
	}

	private volatile long lastPersist;

	/** One more key eaten: bump the all-time counter (persisted at most once a second, and on shutdown) and flash the hint. */
	private void countSwallowed()
	{
		swallowed.incrementAndGet();
		long now = System.currentTimeMillis();
		if (now - lastPersist > 1000)
		{
			lastPersist = now;
			persistSaved();
		}
		hint.flash();
	}

	private void reset()
	{
		openDialogues = 0;
		armed = false;
		lastGuardedAt = 0;
		swallowingCurrentKey = false;
		guardedKeyHeld = false;
	}

	/** Still inside the guard window: the last guarded key was recent, or one is still being held down. */
	private boolean withinWindow(long now)
	{
		long since = now - lastGuardedAt;
		if (guardedKeyHeld && since < HELD_STALE_MS)
		{
			return true;
		}
		return since < config.windowMs();
	}

	/** Keys swallowed all-time, for the hint. */
	int swallowedCount()
	{
		return swallowed.get();
	}

	// ------------------------------------------------------------------ dialogue state (client thread)

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() != GameState.LOGGED_IN)
		{
			reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (isDialogue(e.getGroupId()))
		{
			openDialogues++;
			armed = false;   // a fresh page: it needs a key of its own
			if (config.clearJunkOnDialogue())
			{
				clearJunkFromChat();
			}
		}
	}

	/** Client thread: wipe the chat box if all it holds is digits and spaces left over from an earlier dialogue. */
	private void clearJunkFromChat()
	{
		String typed = client.getVarcStrValue(VarClientID.CHATINPUT);
		if (typed == null || typed.isEmpty() || !typed.matches("[0-9 ]+"))
		{
			return;
		}
		client.setVarcStrValue(VarClientID.CHATINPUT, "");
		client.runScript(ScriptID.CHAT_PROMPT_INIT);   // redraw the input line
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (isDialogue(e.getGroupId()) && openDialogues > 0)
		{
			openDialogues--;
			// stay armed: the next guarded keys within the window are the spill-over we exist to catch
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
				armed = false;   // a real key: whatever they're typing now is on purpose
			}
			return;
		}
		long now = System.currentTimeMillis();
		boolean inWindow = withinWindow(now);
		guardedKeyHeld = true;   // until keyReleased
		if (openDialogues > 0 && !armed)
		{
			// the one key this page is waiting for
			armed = true;
			lastGuardedAt = now;
			return;
		}
		if (armed && inWindow)
		{
			// spill-over: the page already had its key (or just closed), and they're still pressing or holding.
			// Every guarded key extends the window, space included.
			lastGuardedAt = now;
			swallowingCurrentKey = true;
			countSwallowed();
			e.consume();
			return;
		}
		// the window lapsed: this is ordinary typing (a number in chat, enter to send)
		armed = false;
		if (code == KeyEvent.VK_SPACE && config.noLeadingSpace() && openDialogues == 0 && chatInputIsBlank())
		{
			// a message never starts with a space; quietly drop it (no hint: holding space outside a dialogue is common and harmless)
			lastGuardedAt = now;
			swallowingCurrentKey = true;
			swallowed.incrementAndGet();
			e.consume();
		}
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
			guardedKeyHeld = false;
			lastGuardedAt = System.currentTimeMillis();   // the pause starts now, not at the press
		}
		if (swallowingCurrentKey)
		{
			e.consume();
		}
	}
}
