import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class GamePanel extends JPanel {
    private GameState currentState;

    /**
     * We'll store the local player's ID so we can color them differently.
     */
    private int localPlayerId = -1;

    // (Optional) local player’s username field.
    private String username;

    public GamePanel(String username) {
        this.username = username; // only if you need to store it locally
        setFocusable(true);
        setBackground(Color.BLACK);
    }

    /**
     * Called by the client to inform this panel "your local ID is X".
     */
    public void setLocalPlayerId(int id) {
        this.localPlayerId = id;
    }

    public void setGameState(GameState gs) {
        this.currentState = gs;
        repaint();
    }

    public GameState getGameState() {
        return currentState;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentState == null) return;

        Graphics2D g2d = (Graphics2D) g;

        // 1. Define your “virtual” game world size (change as needed)
        final int worldWidth = 1920;
        final int worldHeight = 1080;

        // 2. Compute scale factors
        double scaleX = getWidth()  / (double) worldWidth;
        double scaleY = getHeight() / (double) worldHeight;

        // 3. Save the original (unscaled) transform
        AffineTransform originalTransform = g2d.getTransform();

        // 4. Apply scaling so we can draw in “world” coordinates
        g2d.scale(scaleX, scaleY);

        // --- Draw the "scaled" game world ------------------------

        // Background
        g2d.setColor(new Color(43, 42, 42));
        g2d.fillRect(0, 0, worldWidth, worldHeight);

        // Draw players (tanks)
        for (GameState.PlayerData p : currentState.players) {
            // If a player's health is 0, skip drawing it (example logic)
            if (p.health == 0) continue;

            // Local player's color vs. others
            if (p.playerId == localPlayerId) {
                g2d.setColor(new Color(250, 172, 106)); // local player's color
            } else {
                g2d.setColor(new Color(212, 108, 79));  // other players' color
            }

            // Tank body
            g2d.fillRect(p.x, p.y, p.width, p.height);

            // Health bar above the tank
            int hbHeight = 10;
            int hbY = p.y - hbHeight - 5;
            g2d.setColor(Color.RED);
            g2d.fillRect(p.x, hbY, p.width, hbHeight);

            // Green portion of the health bar
            int greenWidth = (int) ((p.health / 100.0) * p.width);
            g2d.setColor(Color.GREEN);
            g2d.fillRect(p.x, hbY, greenWidth, hbHeight);

            // Turret (rotated around center of tank)
            double angle = p.turretAngle;
            int cx = p.x + p.width / 2;
            int cy = p.y + p.height / 2;

            AffineTransform old = g2d.getTransform();
            g2d.translate(cx, cy);
            g2d.rotate(angle);
            g2d.setColor(new Color(249, 225, 127));
            g2d.fillRect(0, -p.tubeHeight / 2, p.tubeWidth, p.tubeHeight);
            g2d.setTransform(old);
        }

        // Draw bullets
        if (currentState.bullets != null) {
            g2d.setColor(new Color(245, 232, 132)); // bullet color
            for (GameState.BulletData b : currentState.bullets) {
                g2d.fillOval(b.x, b.y, b.diameter, b.diameter);
            }
        }

        // Draw buff if visible

        if (currentState.buffs != null) {
            for(int i = 0; i < currentState.buffs.size(); i++) {
                if(currentState.buffs.get(i).visible) {
                    Color buffColor = Color.decode(currentState.buffs.get(i).color);
                    g2d.setColor(buffColor);
                    g2d.fillOval(currentState.buffs.get(i).x, currentState.buffs.get(i).y,
                            currentState.buffs.get(i).diameter, currentState.buffs.get(i).diameter);
                }
            }
        }

        // --- Done drawing the scaled world. Restore original transform ---
        g2d.setTransform(originalTransform);

        // -------------------------------------------
        //  Draw local player's username in screen coords (below the tank)
        // -------------------------------------------
        for (GameState.PlayerData p : currentState.players) {
            // We rely on p.username from the server
            if (p.playerId == localPlayerId && p.username != null) {
                int screenX = (int) (p.x * scaleX);
                int screenY = (int) (p.y * scaleY);
                int screenW = (int) (p.width  * scaleX);
                int screenH = (int) (p.height * scaleY);

                Font font = new Font("Arial", Font.BOLD, 14);
                g2d.setFont(font);
                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(p.username);

                // Position text *under* the tank base
                int textX = screenX + (screenW - textWidth) / 2;
                // Move down by the tank’s full height plus a bit
                int textY = screenY + screenH + fm.getAscent() + 5;

                // Draw the username in white
                g2d.setColor(Color.WHITE);
                g2d.drawString(p.username, textX, textY);
            }
        }

        // -------------------------------------------
        //  Stylized Leaderboard: Top 10 players by score
        // -------------------------------------------
        List<GameState.PlayerData> sortedPlayers = new ArrayList<>(currentState.players);
        // Sort descending by score
        sortedPlayers.sort((p1, p2) -> Integer.compare(p2.score, p1.score));

        int maxLeaders = Math.min(10, sortedPlayers.size());

        // Prepare lines: first a header, then each entry
        List<String> lines = new ArrayList<>();
        lines.add("LEADERBOARD");
        for (int i = 0; i < maxLeaders; i++) {
            GameState.PlayerData pData = sortedPlayers.get(i);
            // e.g. "1) Alice - 500"
            String entry = String.format("%d) %s - %d", (i + 1), pData.username, pData.score);
            lines.add(entry);
        }

        // Use a bold, larger font
        Font leaderFont = new Font("Verdana", Font.BOLD, 18);
        g2d.setFont(leaderFont);
        FontMetrics fm = g2d.getFontMetrics();

        // Measure the widest line for the background box
        int boxWidth = 0;
        for (String line : lines) {
            int w = fm.stringWidth(line);
            if (w > boxWidth) boxWidth = w;
        }
        // Height is number of lines times lineHeight, plus some padding
        int lineHeight = fm.getHeight();
        int boxHeight = lines.size() * lineHeight + 10;

        // Leaderboard box position in screen coordinates
        int boxX = 10;
        int boxY = 20;

        // Draw a semi-transparent rectangle as the background
        g2d.setColor(new Color(60, 60, 60, 200));  // dark gray with some alpha
        g2d.fillRect(boxX, boxY, boxWidth + 20, boxHeight + 10);

        // Now draw the lines in a matching color to the game’s palette
        g2d.setColor(new Color(245, 232, 132));  // a lighter, golden color
        int textYPos = boxY + fm.getAscent() + 5;
        for (String line : lines) {
            int textXPos = boxX + 10; // left padding
            g2d.drawString(line, textXPos, textYPos);
            textYPos += lineHeight;
        }
    }
}
