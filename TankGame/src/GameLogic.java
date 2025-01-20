import java.awt.Color;
import java.util.*;

public class GameLogic {

    // The total map size
    private final int MAP_WIDTH = 1920;
    private final int MAP_HEIGHT = 1080;

    // We shrink top/bottom by these margins
    private static final int TOP_MARGIN = 50;
    private static final int BOTTOM_MARGIN = 50;

    // Player data by ID
    private Map<Integer, Player> players = new HashMap<>();
    private Set<String> usernames = new HashSet<>();

    // All bullets in the game
    private List<ServerBullet> bullets = new ArrayList<>();

    // Single buff on the map
    private ServerBuff buff;

    public GameLogic() {
        // Initialize the buff
        buff = new ServerBuff(300, 300, 40);
        buff.visible = true;
    }

    /**
     * Called when a new player joins the server.
     */
    public void addPlayer(int playerId) {
        Player p = new Player();
        p.playerId = playerId;
        p.x = 100;
        p.y = 100;
        p.width = 50;
        p.height = 50;
        p.tubeWidth = 40;
        p.tubeHeight = 15;
        p.health = 100;

        // So the player can shoot immediately
        p.lastShotTime = 0;

        // Alive by default
        p.dead = false;

        players.put(playerId, p);
    }

    public void removePlayer(int playerId) {
        usernames.remove(players.get(playerId).username);
        players.remove(playerId);
    }

    /**
     * The server receives a Command (WASD, shooting, turret angle)
     * and applies it to the appropriate player for processing
     * in the next update() call.
     */
    public void handleCommand(int playerId, Command cmd) {
        Player p = players.get(playerId);
        if (p == null) return;
        p.command = cmd;
    }

    public boolean handleLoginAttempt(int playerId, LoginAttempt loginAttempt){
        boolean answer = !usernames.contains(loginAttempt.username);
        if(answer) {
            usernames.add(loginAttempt.username);
            players.get(playerId).username = loginAttempt.username;
        }
        return answer;
    }

    /**
     * Called ~60 times per second by the server's game loop.
     * Updates players, bullets, collisions, etc.
     */
    public void update() {
        updatePlayers();
        updateBullets();
        checkBuffCollisions();  // check if a player collides with the buff
    }

    private void updatePlayers() {
        for (Player p : players.values()) {
            if (p.dead) {
                // skip movement, shooting, etc. if they're "dead"
                continue;
            }

            Command cmd = p.command;
            if (cmd == null) continue;

            // Movement
            int speed = 5;
            if (cmd.moveUp)    p.y -= speed;
            if (cmd.moveDown)  p.y += speed;
            if (cmd.moveLeft)  p.x -= speed;
            if (cmd.moveRight) p.x += speed;

            // Clamp X: can't leave left or right edges
            p.x = Math.max(0, Math.min(MAP_WIDTH - p.width, p.x));
            // Clamp Y: can't go above TOP_MARGIN or below (MAP_HEIGHT - BOTTOM_MARGIN)
            p.y = Math.max(TOP_MARGIN, Math.min(MAP_HEIGHT - BOTTOM_MARGIN - p.height, p.y));

            // Update turret angle
            p.turretAngle = cmd.turretAngle;

            // Shooting (with a 0.25s cooldown)
            if (cmd.shooting) {
                spawnBullet(p);
                // If you only want 1 bullet on mouse click, reset:
                // p.command.shooting = false;
            }
        }
    }

    /**
     * Attempt to spawn a bullet from player p if cooldown has passed.
     */
    private void spawnBullet(Player p) {
        long now = System.currentTimeMillis();
        // 250 ms = 0.25s
        if (now - p.lastShotTime < 250) {
            return; // too soon
        }
        p.lastShotTime = now;

        int centerX = p.x + p.width / 2;
        int centerY = p.y + p.height / 2;
        double angle = p.turretAngle;

        int bulletStartX = (int) (centerX + Math.cos(angle) * p.tubeWidth);
        int bulletStartY = (int) (centerY + Math.sin(angle) * p.tubeWidth);

        int speed = 15;
        int diameter = 12;

        // Note the 'ownerId' param so we know who fired it
        bullets.add(new ServerBullet(bulletStartX, bulletStartY, angle, speed, diameter, p.playerId));
    }

    private void updateBullets() {
        // We'll move bullets AND check collisions with players in a single pass
        Iterator<ServerBullet> it = bullets.iterator();
        while (it.hasNext()) {
            ServerBullet b = it.next();
            b.move();

            // Check if out of bounds
            if (b.x < 0 || b.x > MAP_WIDTH || b.y < 0 || b.y > MAP_HEIGHT) {
                it.remove();
                continue;
            }

            // Now check collisions with players
            boolean bulletRemoved = false;
            for (Player p : players.values()) {
                if (p.dead) continue; // can't hit dead players
                // approximate a circle for the tank
                int centerX = p.x + p.width / 2;
                int centerY = p.y + p.height / 2;
                int radius = p.width / 2;

                // approximate bullet as circle with radius diameter/2
                int bulletRadius = b.diameter / 2;

                if (circleCollision(centerX, centerY, radius,
                        b.x + bulletRadius, b.y + bulletRadius, bulletRadius)) {
                    // bullet hit a player
                    if (b.ownerId != p.playerId) {
                        // It's not their own bullet -> do damage
                        p.health -= 25;

                        // remove bullet
                        it.remove();
                        bulletRemoved = true;

                        // If health <= 0, "kill" them and respawn in 3s
                        if (p.health <= 0) {
                            players.get(b.ownerId).score++;
                            p.dead = true;
                            p.health = 0;
                            scheduleRespawn(p, 3000);
                        }
                    }
                    // If it's their own bullet, do nothing
                    break;
                }
            }
            if (bulletRemoved) {
                // don't continue checking collisions for this bullet
                continue;
            }
        }
    }

    /**
     * Start a background thread that waits 'delayMs' and then respawns
     * this player with full health at a random location.
     */
    private void scheduleRespawn(Player p, int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {}

            // Respawn at random location within the same top/bottom margins
            p.x = getRandomX(p.width);
            p.y = getRandomY(p.height);

            p.health = 100;
            p.dead = false;
        }).start();
    }

    private int getRandomX(int playerWidth) {
        int maxX = MAP_WIDTH - playerWidth;
        if (maxX < 0) return 0;
        return (int) (Math.random() * (maxX + 1));
    }

    private int getRandomY(int playerHeight) {
        int minY = TOP_MARGIN;
        int maxY = MAP_HEIGHT - BOTTOM_MARGIN - playerHeight;
        if (maxY < minY) return minY;
        return minY + (int) (Math.random() * (maxY - minY + 1));
    }

    /**
     * Collisions with the buff (shrink effect)
     */
    private void checkBuffCollisions() {
        if (!buff.visible) return;

        for (Player p : players.values()) {
            if (p.dead) continue; // dead players can't pick up buff

            int centerX = p.x + p.width/2;
            int centerY = p.y + p.height/2;
            int radius = p.width/2;

            int buffCenterX = buff.x + buff.diameter/2;
            int buffCenterY = buff.y + buff.diameter/2;
            int buffRadius = buff.diameter/2;

            if (circleCollision(centerX, centerY, radius,
                    buffCenterX, buffCenterY, buffRadius)) {
                // pick up buff
                buff.visible = false;

                // respawn buff after random time
                new Thread(() -> {
                    try {
                        Thread.sleep(5000 + (int)(Math.random()*5000));
                        buff.relocate(MAP_WIDTH, MAP_HEIGHT);
                    } catch (InterruptedException ignored) {}
                }).start();

                // shrink by factor 0.5 for 10 seconds
                final double factor = 0.5;
                p.width      = Math.max((int)(p.width      * factor), 10);
                p.height     = Math.max((int)(p.height     * factor), 10);
                p.tubeWidth  = Math.max((int)(p.tubeWidth  * factor), 10);
                p.tubeHeight = Math.max((int)(p.tubeHeight * factor), 5);

                // clamp after changing size
                p.x = Math.max(0, Math.min(MAP_WIDTH - p.width, p.x));
                p.y = Math.max(TOP_MARGIN, Math.min(MAP_HEIGHT - BOTTOM_MARGIN - p.height, p.y));

                // revert after 10s
                new Thread(() -> {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {}

                    p.width      = Math.max((int)(p.width      / factor), 10);
                    p.height     = Math.max((int)(p.height     / factor), 10);
                    p.tubeWidth  = Math.max((int)(p.tubeWidth  / factor), 10);
                    p.tubeHeight = Math.max((int)(p.tubeHeight / factor), 5);

                    // clamp
                    p.x = Math.max(0, Math.min(MAP_WIDTH - p.width, p.x));
                    p.y = Math.max(TOP_MARGIN, Math.min(MAP_HEIGHT - BOTTOM_MARGIN - p.height, p.y));
                }).start();

                break;
            }
        }
    }

    private boolean circleCollision(int x1, int y1, int r1, int x2, int y2, int r2) {
        int dx = x1 - x2;
        int dy = y1 - y2;
        int distSq = dx*dx + dy*dy;
        int radii = r1 + r2;
        return distSq < (radii*radii);
    }

    public GameState buildGameState() {
        GameState gs = new GameState();

        for (Player p : players.values()) {
            GameState.PlayerData pd = new GameState.PlayerData();
            pd.playerId = p.playerId;
            pd.username = p.username;
            pd.score = p.score;
            pd.x = p.x;
            pd.y = p.y;
            pd.width = p.width;
            pd.height = p.height;
            pd.tubeWidth = p.tubeWidth;
            pd.tubeHeight = p.tubeHeight;
            pd.turretAngle = p.turretAngle;
            pd.health = p.health;
            gs.players.add(pd);
        }

        for (ServerBullet b : bullets) {
            GameState.BulletData bd = new GameState.BulletData();
            bd.x = b.x;
            bd.y = b.y;
            bd.diameter = b.diameter;
            gs.bullets.add(bd);
        }

        GameState.BuffData buffData = new GameState.BuffData();
        buffData.x = buff.x;
        buffData.y = buff.y;
        buffData.diameter = buff.diameter;
        buffData.color = colorToHex(buff.color);
        buffData.visible = buff.visible;
        gs.buff = buffData;

        return gs;
    }

    private static final Color[] BUFF_COLORS = {
            new Color(217,162,134),
            new Color(155,72,72)
    };
    private static final Random RNG = new Random();

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static class ServerBuff {
        public int x, y, diameter;
        public Color color;
        public boolean visible = true;

        public ServerBuff(int x, int y, int diameter) {
            this.x = x;
            this.y = y;
            this.diameter = diameter;
            this.color = BUFF_COLORS[RNG.nextInt(BUFF_COLORS.length)];
        }

        public void relocate(int maxW, int maxH) {
            x = RNG.nextInt(Math.max(1, maxW - diameter));
            y = RNG.nextInt(Math.max(1, maxH - diameter));
            color = BUFF_COLORS[RNG.nextInt(BUFF_COLORS.length)];
            visible = true;
        }
    }

    private static class ServerBullet {
        public int x, y;
        public double angle;
        public int speed;
        public int diameter;
        public int ownerId;

        public ServerBullet(int x, int y, double angle, int speed, int diameter, int ownerId) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.speed = speed;
            this.diameter = diameter;
            this.ownerId = ownerId;
        }

        public void move() {
            x += (int)(Math.cos(angle) * speed);
            y += (int)(Math.sin(angle) * speed);
        }
    }

    private static class Player {
        int playerId;
        String username = "player";
        int score;
        int x, y;
        int width, height;
        int tubeWidth, tubeHeight;
        double turretAngle;
        int health = 100;

        long lastShotTime;
        Command command;
        boolean dead;
    }
}
