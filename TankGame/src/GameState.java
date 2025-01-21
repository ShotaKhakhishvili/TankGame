import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sent from server to client each frame, describing
 * the authoritative positions/sizes/colors, etc.
 */
public class GameState implements Serializable {
    private static final long serialVersionUID = 1L;

    // All players in the game
    public List<PlayerData> players = new ArrayList<>();

    // All bullets in the game
    public List<BulletData> bullets = new ArrayList<>();

    // Single buff (or you could support multiple)
    public List<BuffData> buffs;

    // Nested data classes
    public static class PlayerData implements Serializable {
        public int playerId;
        public String username;
        public int score;
        public int x, y;           // top-left of the tank
        public int width, height;  // tank body size
        public int tubeWidth, tubeHeight;  // turret size
        public double turretAngle;
        public int health;
    }

    public static class BulletData implements Serializable {
        public int x, y;
        public int diameter;
    }

    public static class BuffData implements Serializable {
        public enum buffType {sizeDecrease, bulletIncrease, damageIncrease, speedIncrease, reloadSpeedIncrease};
        public int x, y, diameter;
        public String color;  // We'll send color as a string to be interpreted by client
        public boolean visible;
    }
}
