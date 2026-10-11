package com.dialogueguard;

import static com.dialogueguard.DialogueKeys.Kind.DIGIT;
import static com.dialogueguard.DialogueKeys.Kind.ENTER;
import static com.dialogueguard.DialogueKeys.Kind.OTHER;
import static com.dialogueguard.DialogueKeys.Kind.SPACE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.event.KeyEvent;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

public class DialogueKeysTest
{
	@Test
	public void digitsOnTheTopRowAndTheNumpadAreBothDigits()
	{
		for (int k = KeyEvent.VK_1; k <= KeyEvent.VK_9; k++)
		{
			assertEquals(DIGIT, DialogueKeys.kind(k));
		}
		for (int k = KeyEvent.VK_NUMPAD1; k <= KeyEvent.VK_NUMPAD9; k++)
		{
			assertEquals(DIGIT, DialogueKeys.kind(k));
		}
	}

	@Test
	public void zeroIsNotADialogueKey()
	{
		// no dialogue has a tenth option, so 0 is ordinary typing
		assertEquals(OTHER, DialogueKeys.kind(KeyEvent.VK_0));
		assertEquals(OTHER, DialogueKeys.kind(KeyEvent.VK_NUMPAD0));
	}

	@Test
	public void spaceEnterAndLetters()
	{
		assertEquals(SPACE, DialogueKeys.kind(KeyEvent.VK_SPACE));
		assertEquals(ENTER, DialogueKeys.kind(KeyEvent.VK_ENTER));
		assertEquals(OTHER, DialogueKeys.kind(KeyEvent.VK_A));
	}

	@Test
	public void optionMenusTakeDigitsOnly()
	{
		assertTrue(DialogueKeys.pageAccepts(InterfaceID.CHATMENU, KeyEvent.VK_1));
		assertTrue(DialogueKeys.pageAccepts(InterfaceID.CHATMENU, KeyEvent.VK_NUMPAD2));
		assertFalse(DialogueKeys.pageAccepts(InterfaceID.CHATMENU, KeyEvent.VK_SPACE));
		assertFalse(DialogueKeys.pageAccepts(InterfaceID.CHATMENU, KeyEvent.VK_ENTER));
	}

	@Test
	public void makeXTakesDigitsAndSpace()
	{
		assertTrue(DialogueKeys.pageAccepts(InterfaceID.SKILLMULTI, KeyEvent.VK_3));
		assertTrue(DialogueKeys.pageAccepts(InterfaceID.SKILLMULTI, KeyEvent.VK_SPACE));
		assertFalse(DialogueKeys.pageAccepts(InterfaceID.SKILLMULTI, KeyEvent.VK_ENTER));
	}

	@Test
	public void continuePagesTakeSpaceOnly()
	{
		int[] pages = {InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.MESSAGEBOX, InterfaceID.OBJECTBOX,
			InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY};
		for (int page : pages)
		{
			assertTrue(DialogueKeys.pageAccepts(page, KeyEvent.VK_SPACE));
			assertFalse(DialogueKeys.pageAccepts(page, KeyEvent.VK_1));
			assertFalse(DialogueKeys.pageAccepts(page, KeyEvent.VK_ENTER));
		}
	}

	@Test
	public void everyDialogueTypeIsRecognisedAndNothingElse()
	{
		int[] dialogues = {InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.MESSAGEBOX,
			InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY, InterfaceID.SKILLMULTI};
		for (int d : dialogues)
		{
			assertTrue(DialogueKeys.isDialogue(d));
		}
		assertFalse(DialogueKeys.isDialogue(InterfaceID.CHATBOX));
		assertFalse(DialogueKeys.isDialogue(-1));
	}

	@Test
	public void modifiersAreNotTyping()
	{
		assertTrue(DialogueKeys.isModifier(KeyEvent.VK_SHIFT));
		assertFalse(DialogueKeys.isModifier(KeyEvent.VK_A));
		assertFalse(DialogueKeys.isModifier(KeyEvent.VK_1));
	}
}
