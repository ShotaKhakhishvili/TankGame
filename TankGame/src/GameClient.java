import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;

/**
 * GameClient connects to the server, receives "playerId",
 * then continuously receives GameState objects to render in GamePanel.
 * It sends Command objects when user input changes.
 */
public class GameClient extends JFrame {
    private JTextField usernameField, hostField, portField;
    private JLabel statusLabel;
    private Socket socket;
    private String username;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    private GamePanel gamePanel;

    // This Command holds the current user input (WASD, turret angle, etc.).
    private Command currentCommand = new Command();

    // The ID of this client, as assigned by the server
    private int localPlayerId = -1;

    public static void main(String[] args) {
        new GameClient().setVisible(true);
    }

    public GameClient(Socket socket, int localPlayerId,ObjectOutputStream out, ObjectInputStream in){
        this.socket = socket;
        this.localPlayerId = localPlayerId;
        this.out = out;
        this.in = in;

        setTitle("Tank Game");
        repaint();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        gamePanel = new GamePanel(username);
        this.add(gamePanel, BorderLayout.CENTER);

//        // Connect to the server
        connectToServer();

        // Setup input listeners on the gamePanel
        this.setupInputListeners();

        // Make sure gamePanel has focus
        gamePanel.setFocusable(true);
        gamePanel.requestFocusInWindow();
        gamePanel.setVisible(true);
    }

    public GameClient() {
        super("Login Screen");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 250);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);

        JLabel userLabel = new JLabel("Username:");
        usernameField = new JTextField(15);

        JLabel hostLabel = new JLabel("Host:");
        hostField = new JTextField("localhost", 15);

        JLabel portLabel = new JLabel("Port:");
        portField = new JTextField("12345", 6);

        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(e -> onLogin());

        statusLabel = new JLabel("");
        statusLabel.setForeground(Color.RED);

        gbc.gridx=0; gbc.gridy=0; panel.add(userLabel, gbc);
        gbc.gridx=1; gbc.gridy=0; panel.add(usernameField, gbc);

        gbc.gridx=0; gbc.gridy=1; panel.add(hostLabel, gbc);
        gbc.gridx=1; gbc.gridy=1; panel.add(hostField, gbc);

        gbc.gridx=0; gbc.gridy=2; panel.add(portLabel, gbc);
        gbc.gridx=1; gbc.gridy=2; panel.add(portField, gbc);

        gbc.gridwidth=2;
        gbc.gridx=0; gbc.gridy=3;
        panel.add(loginButton, gbc);

        gbc.gridy=4;
        panel.add(statusLabel, gbc);

        add(panel);
    }

    private void onLogin() {
        username = usernameField.getText().trim();
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        if (username.isEmpty()) {
            statusLabel.setText("Username cannot be empty.");
            return;
        }

        try{
            if(socket != null && socket.isConnected())
                socket.close();

            socket = new Socket(host, port);

            socket.setSoTimeout(2000);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            localPlayerId = in.readInt();

            // 1) Send the desired username
            LoginAttempt loginAttempt = new LoginAttempt();
            loginAttempt.username = username;
            out.writeObject(loginAttempt);
            out.flush();

            Object input = in.readObject();

            System.out.println(input.getClass());

            if (input instanceof LoginAttempt) {
                System.out.println(((LoginAttempt) input).accessAllowed);
                if (((LoginAttempt) input).accessAllowed) {
                    new GameClient(socket, localPlayerId, out, in).setVisible(true);
                    this.dispose();
                }
            }

        } catch (IOException e) {
            statusLabel.setText("Some problem occurred while connection");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Attempt to connect to the server. If connection fails,
     * show an error dialog and exit.
     */
    private void connectToServer() {
        gamePanel.setLocalPlayerId(localPlayerId);
        // 2) Start a background thread to listen for GameState updates
        new Thread(this::listenForGameState).start();
    }

    /**
     * Continuously reads GameState objects from the server.
     * If the server stops or connection is lost, show error window and exit.
     */
    private void listenForGameState() {
        try {
            while (true) {
                Object obj = in.readObject();
                if (obj instanceof GameState) {
                    GameState gs = (GameState) obj;
                    gamePanel.setGameState(gs);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Lost connection after being connected
            showConnectionError("Connection to the server was lost. The server may have stopped.");
        }
    }

    /**
     * Send the current Command object to the server.
     */
    private void sendCommand() {
        if (out == null) return;
        try {
            out.reset();
            out.writeObject(currentCommand);
            out.flush();
        } catch (IOException e) {
            System.out.println("Error sending command to server.");
        }
    }

    /**
     * Attach key/mouse listeners to the gamePanel so we can capture user input.
     */
    private void setupInputListeners() {
        // KeyListener for WASD
        gamePanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                boolean changed = false;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W:
                        if (!currentCommand.moveUp) {
                            currentCommand.moveUp = true;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_S:
                        if (!currentCommand.moveDown) {
                            currentCommand.moveDown = true;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_A:
                        if (!currentCommand.moveLeft) {
                            currentCommand.moveLeft = true;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_D:
                        if (!currentCommand.moveRight) {
                            currentCommand.moveRight = true;
                            changed = true;
                        }
                        break;
                }
                if (changed) sendCommand();
            }

            @Override
            public void keyReleased(KeyEvent e) {
                boolean changed = false;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W:
                        if (currentCommand.moveUp) {
                            currentCommand.moveUp = false;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_S:
                        if (currentCommand.moveDown) {
                            currentCommand.moveDown = false;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_A:
                        if (currentCommand.moveLeft) {
                            currentCommand.moveLeft = false;
                            changed = true;
                        }
                        break;
                    case KeyEvent.VK_D:
                        if (currentCommand.moveRight) {
                            currentCommand.moveRight = false;
                            changed = true;
                        }
                        break;
                }
                if (changed) sendCommand();
            }
        });

        // Mouse to aim and shoot
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Start shooting
                currentCommand.shooting = true;
                sendCommand();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Stop shooting
                currentCommand.shooting = false;
                sendCommand();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateTurretAngle(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // If holding the mouse button down, still update angle
                updateTurretAngle(e);
            }
        };

        gamePanel.addMouseListener(mouseAdapter);
        gamePanel.addMouseMotionListener(mouseAdapter);
    }

    /**
     * Compute turret angle from the local player's tank center to the mouse coordinates.
     */
    private void updateTurretAngle(MouseEvent e) {
        GameState gs = gamePanel.getGameState();
        if (gs == null) return;

        // Find local player's data
        GameState.PlayerData localPlayer = null;
        for (GameState.PlayerData pd : gs.players) {
            if (pd.playerId == localPlayerId) {
                localPlayer = pd;
                break;
            }
        }
        if (localPlayer == null) return; // local player not found (maybe dead?)

        // Calculate center of this player's tank in server coordinates
        double tankCenterX = localPlayer.x + localPlayer.width / 2.0;
        double tankCenterY = localPlayer.y + localPlayer.height / 2.0;

        // Get current panel dimensions
        int panelWidth = gamePanel.getWidth();
        int panelHeight = gamePanel.getHeight();

        // Convert mouse coordinates (panel space) to server coordinate space (1920x1080)
        double scaledMouseX = (e.getX() / (double) panelWidth) * 1920;
        double scaledMouseY = (e.getY() / (double) panelHeight) * 1080;

        // Calculate the angle from the tank center to the scaled mouse position
        double angle = Math.atan2(scaledMouseY - tankCenterY, scaledMouseX - tankCenterX);

        // Update the command with the new angle
        currentCommand.turretAngle = angle;
        sendCommand();
    }



    /**
     * Shows a small error window indicating a connection problem, then exits.
     */
    private void showConnectionError(String message) {
        // Because we are on a background thread, ensure we do this on EDT
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "Connection Error",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(0);
        });
    }
}
