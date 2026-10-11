/*
 * Dialogue Guard — BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DialogueGuardPlugin.CONFIG_GROUP)
public interface DialogueGuardConfig extends Config
{
	@ConfigItem(keyName = "guardDigits", name = "Guard number keys", position = 0,
		description = "Swallow 1-9 (dialogue option keys) when they spill past the end of a dialogue.")
	default boolean guardDigits()
	{
		return true;
	}

	@ConfigItem(keyName = "guardSpace", name = "Guard space", position = 1,
		description = "Swallow the space bar (click-to-continue) when it spills past the end of a dialogue.")
	default boolean guardSpace()
	{
		return true;
	}

	@ConfigItem(keyName = "guardEnter", name = "Guard enter", position = 2,
		description = "Swallow enter when it spills past the end of a dialogue. This also stops a stray enter from sending whatever was already in the chat box.")
	default boolean guardEnter()
	{
		return true;
	}

	@Range(min = 100, max = 3000)
	@ConfigItem(keyName = "windowMs", name = "Guard window (ms)", position = 3,
		description = "After the key that ends a dialogue, further guarded keys are swallowed until you stop pressing them for this long. Pressing any other key (a letter) ends the guard at once.")
	default int windowMs()
	{
		return 600;
	}

	@ConfigItem(keyName = "noLeadingSpace", name = "Never start a message with a space", position = 4,
		description = "Swallow the space bar whenever the chat box is empty and no dialogue is open, so a message can never begin with spaces. Space still works in dialogues and once you've typed something.")
	default boolean noLeadingSpace()
	{
		return true;
	}

	@ConfigItem(keyName = "clearJunkOnDialogue", name = "Clear leftover junk when a dialogue opens", position = 5,
		description = "If the chat box holds only digits and spaces (a '111' left over from the last conversation) when a dialogue opens, clear it so it can't be sent later by accident. Real text is never touched.")
	default boolean clearJunkOnDialogue()
	{
		return true;
	}

	@ConfigItem(keyName = "showHint", name = "Show hint", position = 6,
		description = "Briefly show how many keys were swallowed above the chat box, so a swallowed key never looks like a dead keyboard.")
	default boolean showHint()
	{
		return true;
	}

	@ConfigItem(keyName = "showLifetime", name = "Show keys saved all-time in hint", position = 7,
		description = "Append the running total of keys this plugin has swallowed for you to the hint.")
	default boolean showLifetime()
	{
		return true;
	}
}
