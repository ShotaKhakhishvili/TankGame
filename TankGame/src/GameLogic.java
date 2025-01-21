import java.awt.Color;
import java.util.*;

public class GameLogic {

    // The total map size
    private static final int MAP_WIDTH = 1920;
    private static final int MAP_HEIGHT = 1080;

    // We shrink top/bottom by these margins
    private static final int TOP_MARGIN = 10;
    private static final int BOTTOM_MARGIN = 10;

    // Player data by ID
    private Map<Integer, Player> players = new HashMap<>();
    private Set<String> usernames = new HashSet<>();

    // All bullets in the game
    private List<ServerBullet> bullets = new ArrayList<>();

    // Single buff on the map
    private List<ServerBuff> buffs = new ArrayList<>();

    public GameLogic() {
        // Initialize the buff
        for(int i = 0; i < 5; i++){
            ServerBuff buff = new ServerBuff(0,0,40);
            buff.relocate(MAP_WIDTH,MAP_HEIGHT);
            buffs.add(buff);
            buffs.get(i).visible = true;
        }
    }

    /**
     * Called when a new player joins the server.
     */
    public void addPlayer(int playerId) {
        Player p = new Player();
        p.playerId = playerId;
        p.x = 100;
        p.y = 100;
        p.width = p.defaultWidth = 50;
        p.height = p.defaultHeight = 50;
        p.tubeWidth = p.defaultTubeWidth = 40;
        p.tubeHeight = p.defaultTubeHeight = 15;
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
            int speed = (int)(5 * p.speedMultiplier);
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
        if (now - p.lastShotTime < 250 / p.reloadSpeedMultiplier) {
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
                int bulletRadius = (int)(p.bulletSizeMultiplier * b.diameter / 2);

                if (circleCollision(centerX, centerY, radius,
                        b.x + bulletRadius, b.y + bulletRadius, bulletRadius)) {
                    // bullet hit a player
                    if (b.ownerId != p.playerId) {
                        // It's not their own bullet -> do damage
                        p.health -= (int) (25 * players.get(b.ownerId).damageMultiplier);

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
        if (buffs.isEmpty()) return;

        for (Player p : players.values()) {
            if (p.dead) continue; // dead players can't pick up buff

            for(ServerBuff buff : buffs){
                if(!buff.visible) continue;

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
                            buff.setRandomBuffType();
                        } catch (InterruptedException ignored) {}
                    }).start();

                    ServerBuff.applyBuff(p, buff.buffType);
                }
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
            bd.diameter = (int)(b.diameter * players.get(b.ownerId).bulletSizeMultiplier);
            gs.bullets.add(bd);
        }

        List<GameState.BuffData> buffDatas = new ArrayList<>();

        for(ServerBuff buff : buffs){
            if(!buff.visible)continue;
            GameState.BuffData buffData = new GameState.BuffData();

            buffData.x = buff.x;
            buffData.y = buff.y;
            buffData.diameter = buff.diameter;
            buffData.color = colorToHex(buff.color);
            buffData.visible = buff.visible;

            buffDatas.add(buffData);
        }
        gs.buffs = buffDatas;

        return gs;
    }

    private static final Color[] BUFF_COLORS = {
            new Color(174, 195, 183),   // Size decrease
            new Color(224, 108, 117),   // Damage increase
            new Color(139, 48, 48),     // Bullet size increase
            new Color(106, 192, 153),   // Speed increase
            new Color(95, 158, 160)     // Reload speed increase
    };
    private static final Random RNG = new Random();

    private static String colorToHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static class ServerBuff {
        public int x, y, diameter;
        public Color color;
        public boolean visible = true;
        public GameState.BuffData.buffType buffType;

        public ServerBuff(int x, int y, int diameter) {
            this.x = x;
            this.y = y;
            this.diameter = diameter;
            setRandomBuffType();
        }

        public void setRandomBuffType(){
            GameState.BuffData.buffType[] allEnums = GameState.BuffData.buffType.values();
            Random random = new Random();
            buffType = allEnums[random.nextInt(allEnums.length)];
            switch (buffType){
                case sizeDecrease:
                    color = BUFF_COLORS[0];
                    break;
                case speedIncrease:
                    color = BUFF_COLORS[3];
                    break;
                case bulletIncrease:
                    color = BUFF_COLORS[2];
                    break;
                case damageIncrease:
                    color = BUFF_COLORS[1];
                    break;
                case reloadSpeedIncrease:
                    color = BUFF_COLORS[4];
                    break;
            }
        }

        public static void applyBuff(Player p, GameState.BuffData.buffType buff){
            switch (buff){
                case sizeDecrease:
                    buff_sizeDecrease(p);
                    break;
                case speedIncrease:
                    buff_speedIncrease(p);
                    break;
                case bulletIncrease:
                    buff_bulletIncrease(p);
                    break;
                case damageIncrease:
                    buff_damageIncrease(p);
                    break;
                case reloadSpeedIncrease:
                    buff_reloadSpeedIncrease(p);
                    break;
            }
        }
        public static void buff_sizeDecrease(Player p){
            // shrink by factor 0.5 for 10 seconds
            final double factor = 0.5;
            final long effectTime = 10000;

            p.sizeMultiplier *= factor;

            p.width      = (int)(p.defaultWidth * p.sizeMultiplier);
            p.height     = (int)(p.defaultHeight * p.sizeMultiplier);
            p.tubeWidth  = (int)(p.defaultTubeWidth * p.sizeMultiplier);
            p.tubeHeight = (int)(p.defaultTubeHeight * p.sizeMultiplier);

            // clamp after changing size
            p.x = Math.max(0, Math.min(MAP_WIDTH - p.width, p.x));
            p.y = Math.max(TOP_MARGIN, Math.min(MAP_HEIGHT - BOTTOM_MARGIN - p.height, p.y));

            // revert after 10s
            new Thread(() -> {
                try {
                    Thread.sleep(effectTime);
                } catch (InterruptedException ignored) {}

                p.sizeMultiplier /= factor;

                p.width      = (int)(p.defaultWidth * p.sizeMultiplier);
                p.height     = (int)(p.defaultHeight * p.sizeMultiplier);
                p.tubeWidth  = (int)(p.defaultTubeWidth * p.sizeMultiplier);
                p.tubeHeight = (int)(p.defaultTubeHeight * p.sizeMultiplier);

                // clamp
                p.x = Math.max(0, Math.min(MAP_WIDTH - p.width, p.x));
                p.y = Math.max(TOP_MARGIN, Math.min(MAP_HEIGHT - BOTTOM_MARGIN - p.height, p.y));
            }).start();
        }
        public static void buff_bulletIncrease(Player p){
            double factor = 2;
            long effectTime = 10000;

            p.bulletSizeMultiplier *= factor;

            // revert after 10s
            new Thread(() -> {
                try {
                    Thread.sleep(effectTime);
                } catch (InterruptedException ignored) {}

                p.bulletSizeMultiplier /= factor;
            }).start();
        }
        public static void buff_damageIncrease(Player p){
            double factor = 1.5;
            long effectTime = 10000;

            p.damageMultiplier *= factor;

            // revert after 10s
            new Thread(() -> {
                try {
                    Thread.sleep(effectTime);
                } catch (InterruptedException ignored) {}

                p.damageMultiplier /= factor;
            }).start();
        }
        public static void buff_speedIncrease(Player p){
            double factor = 1.25;
            long effectTime = 15000;

            p.speedMultiplier *= factor;

            // revert after 15s
            new Thread(() -> {
                try {
                    Thread.sleep(effectTime);
                } catch (InterruptedException ignored) {}

                p.speedMultiplier /= factor;
            }).start();
        }
        public static void buff_reloadSpeedIncrease(Player p){
            double factor = 2;
            long effectTime = 20000;

            p.reloadSpeedMultiplier *= factor;

            // revert after 20s
            new Thread(() -> {
                try {
                    Thread.sleep(effectTime);
                } catch (InterruptedException ignored) {}

                p.reloadSpeedMultiplier /= factor;
            }).start();
        }

        public void relocate(int maxW, int maxH) {
            x = RNG.nextInt(Math.max(1, maxW - diameter));
            y = RNG.nextInt(Math.max(1, maxH - diameter));
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
        int defaultWidth, defaultHeight;
        int defaultTubeWidth, defaultTubeHeight;
        int width, height;
        int tubeWidth, tubeHeight;
        double turretAngle;

        double sizeMultiplier = 1;
        double bulletSizeMultiplier = 1;
        double damageMultiplier = 1;
        double speedMultiplier = 1;
        double reloadSpeedMultiplier = 1;

        int health = 100;

        long lastShotTime;
        Command command;
        boolean dead;
    }
}
