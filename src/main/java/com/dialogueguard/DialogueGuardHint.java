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
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class DialogueGuardHint extends Overlay
{
	private static final long SHOW_MS = 1500;
	private static final Color BG = new Color(0, 0, 0, 160);
	private static final Color FG = new Color(255, 200, 80);
	private static final int PAD_X = 5;
	private static final int PAD_Y = 2;
	/** gap between the hint and the chat box's right edge */
	private static final int INSET_X = 4;
	/** gap above the chat box; smaller than INSET_X so the two gaps look the same against the chat box's frame */
	private static final int INSET_Y = 2;
	/** what the hint sits above: the message area, or the tab row when chat is collapsed in resizable mode */
	private static final int[] ANCHORS = {InterfaceID.Chatbox.CHATAREA, InterfaceID.Chatbox.CONTROLS};

	private final Client client;
	private final DialogueGuardConfig config;
	private final Font font = FontManager.getRunescapeSmallFont();

	/** keys blocked in the current burst (a burst ends once the hint has faded) */
	private int burst;
	private volatile long showUntil;
	/** the line to draw, built when a key is blocked rather than every frame */
	private volatile String text = "";

	@Inject
	public DialogueGuardHint(Client client, DialogueGuardConfig config)
	{
		this.client = client;
		this.config = config;
		// Placed by hand against the chat box rather than with ABOVE_CHATBOX_RIGHT: in fixed mode RuneLite moves that
		// corner to the bottom right of the game view and clips it there, and a snap corner lays out each frame with
		// the previous frame's width, so the first frame of a hint would hang off the right edge.
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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
		Rectangle chat = chatBounds();
		if (chat == null)
		{
			return null;
		}
		String line = text;
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(line) + 2 * PAD_X;
		int h = fm.getHeight() + 2 * PAD_Y;
		// right-aligned to the chat box, just above it; kept on the canvas if the chat box is narrower than the line
		int x = Math.max(0, Math.min(chat.x + chat.width - INSET_X - w, client.getCanvasWidth() - w));
		int y = Math.max(0, chat.y - INSET_Y - h);
		g.setColor(BG);
		g.fillRoundRect(x, y, w, h, 6, 6);
		g.setColor(FG);
		g.drawString(line, x + PAD_X, y + fm.getAscent() + PAD_Y);
		// drawn at absolute coordinates, so nothing for the renderer to lay out
		return null;
	}

	private Rectangle chatBounds()
	{
		for (int id : ANCHORS)
		{
			Widget w = client.getWidget(id);
			if (w != null && !w.isHidden())
			{
				Rectangle b = w.getBounds();
				if (b != null && b.width > 0)
				{
					return b;
				}
			}
		}
		return null;
	}
}
