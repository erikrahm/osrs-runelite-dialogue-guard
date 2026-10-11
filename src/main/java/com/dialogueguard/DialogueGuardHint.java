/*
 * Dialogue Guard — the little "swallowed N" note above the chat box, so a swallowed key never reads as a dead keyboard.
 * BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

public class DialogueGuardHint extends Overlay
{
	private static final long SHOW_MS = 1500;
	private static final Color BG = new Color(0, 0, 0, 160);
	private static final Color FG = new Color(255, 200, 80);

	private final DialogueGuardConfig config;
	/** keys swallowed in the current burst (reset once the hint has faded) */
	private final AtomicInteger burst = new AtomicInteger();
	private volatile long showUntil;
	private Font font;

	@Inject
	public DialogueGuardHint(DialogueGuardConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	/** Called from the key thread for every swallowed key. */
	void flash()
	{
		long now = System.currentTimeMillis();
		if (now > showUntil)
		{
			burst.set(0);   // a new burst
		}
		burst.incrementAndGet();
		showUntil = now + SHOW_MS;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showHint() || System.currentTimeMillis() > showUntil)
		{
			return null;
		}
		int n = burst.get();
		String text = "Dialogue Guard: swallowed " + n + (n == 1 ? " key" : " keys");
		if (font == null)
		{
			font = FontManager.getRunescapeSmallFont();
		}
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 10;
		int h = fm.getHeight() + 4;
		g.setColor(BG);
		g.fillRoundRect(0, 0, w, h, 6, 6);
		g.setColor(FG);
		g.drawString(text, 5, fm.getAscent() + 2);
		return new Dimension(w, h);
	}
}
