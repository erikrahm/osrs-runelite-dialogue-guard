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

	@ConfigItem(keyName = "showHint", name = "Show hint", position = 4,
		description = "Briefly show how many keys were swallowed above the chat box, so a swallowed key never looks like a dead keyboard.")
	default boolean showHint()
	{
		return true;
	}
}
