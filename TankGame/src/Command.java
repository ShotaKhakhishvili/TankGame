import java.io.Serializable;

/**
 * Sent from client to server whenever user input changes:
 * WASD movement, turret angle, shooting flag, etc.
 */
public class Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public boolean moveUp;
    public boolean moveDown;
    public boolean moveLeft;
    public boolean moveRight;

    // Turret direction (radians)
    public double turretAngle;

    // Shooting on/off
    public boolean shooting;
}
