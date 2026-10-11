/*
 * Dialogue Guard: the small "blocked N" note above the chat box, so a blocked key does not look like a dead keyboard.
 * BSD-2-Clause, see LICENSE.
 */
package com.dialogueguard;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

public class DialogueGuardHint extends Overlay
{
	private static final long SHOW_MS = 1500;
	private static final Color BG = new Color(0, 0, 0, 160);
	private static final Color FG = new Color(255, 200, 80);
	private static final int PAD_X = 5;
	private static final int PAD_Y = 2;

	private final DialogueGuardConfig config;
	private final Font font = FontManager.getRunescapeSmallFont();

	/** keys blocked in the current burst (a burst ends once the hint has faded) */
	private int burst;
	private volatile long showUntil;
	/** the line to draw, built when a key is blocked rather than every frame */
	private volatile String text = "";

	@Inject
	public DialogueGuardHint(DialogueGuardConfig config)
	{
		this.config = config;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
	}

	/** Called from the key thread for every blocked key that should be shown. */
	void flash(int allTime)
	{
		long now = System.currentTimeMillis();
		if (now > showUntil)
		{
			burst = 0;
		}
		burst++;
		StringBuilder sb = new StringBuilder("Dialogue Guard: blocked ").append(burst).append(burst == 1 ? " key" : " keys");
		if (config.showLifetime())
		{
			sb.append(" (").append(String.format("%,d", allTime)).append(" all-time)");
		}
		text = sb.toString();
		showUntil = now + SHOW_MS;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showHint() || System.currentTimeMillis() > showUntil)
		{
			return null;
		}
		String line = text;
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(line) + 2 * PAD_X;
		int h = fm.getHeight() + 2 * PAD_Y;
		g.setColor(BG);
		g.fillRoundRect(0, 0, w, h, 6, 6);
		g.setColor(FG);
		g.drawString(line, PAD_X, fm.getAscent() + PAD_Y);
		return new Dimension(w, h);
	}
}
