/*
 * Dialogue Guard: which interfaces are dialogues, which keys are dialogue keys, and which keys each dialogue acts on.
 * BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import java.awt.event.KeyEvent;
import net.runelite.api.gameval.InterfaceID;

/** Plain lookups with no client state, kept apart from the plugin so they can be tested on their own. */
final class DialogueKeys
{
	/** The kinds of key the guard cares about. */
	enum Kind
	{
		/** 1-9 on the top row or the numpad: picks an option, and types a digit into chat */
		DIGIT,
		/** continues a page, and types a space into chat */
		SPACE,
		/** advances nothing, but sends whatever is in the chat box */
		ENTER,
		/** anything else */
		OTHER
	}

	/** Interface groups that take a key to advance: NPC/player chat, option menus, message/sprite boxes, level-ups, make-x. */
	private static final int[] DIALOGUE_GROUPS = {
		InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.MESSAGEBOX,
		InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY, InterfaceID.SKILLMULTI,
	};

	private DialogueKeys()
	{
	}

	static boolean isDialogue(int groupId)
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

	static Kind kind(int keyCode)
	{
		if ((keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9)
			|| (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9))
		{
			return Kind.DIGIT;
		}
		switch (keyCode)
		{
			case KeyEvent.VK_SPACE:
				return Kind.SPACE;
			case KeyEvent.VK_ENTER:
				return Kind.ENTER;
			default:
				return Kind.OTHER;
		}
	}

	/** Modifier presses (and keys Java can't name) say nothing about whether the player is typing. */
	static boolean isModifier(int keyCode)
	{
		return keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_ALT
			|| keyCode == KeyEvent.VK_META || keyCode == KeyEvent.VK_CAPS_LOCK || keyCode == KeyEvent.VK_UNDEFINED;
	}

	/**
	 * Whether a page acts on this key: option menus take digits, make-x takes digits and space (make the selected
	 * amount), everything else is click-to-continue and takes space. Enter advances nothing.
	 */
	static boolean pageAccepts(int groupId, int keyCode)
	{
		Kind kind = kind(keyCode);
		switch (groupId)
		{
			case InterfaceID.CHATMENU:
				return kind == Kind.DIGIT;
			case InterfaceID.SKILLMULTI:
				return kind == Kind.DIGIT || kind == Kind.SPACE;
			default:
				return kind == Kind.SPACE;
		}
	}
}
