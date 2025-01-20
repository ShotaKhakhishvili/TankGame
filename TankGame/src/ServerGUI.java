import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerGUI extends JFrame {

    private GameServer server;
    private JLabel statusLabel;
    private JLabel clientCountLabel;
    private JButton startButton;
    private JButton stopButton;

    // Track whether server is running
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    // --- Color Palette (matches your request) ---
    private static final Color COLOR_BG_DARK   = new Color(0x41436A);  // Dark bluish
    private static final Color COLOR_PURPLE    = new Color(0x984063);  // Purplish
    private static final Color COLOR_HOT_PINK  = new Color(0xF64668);  // Bright pink
    private static final Color COLOR_PEACH     = new Color(0xFE9677);  // Light peach
    private static final Color COLOR_WHITE     = Color.WHITE;

    public ServerGUI() {
        super("Tank Game Server");

        // Create the server (pass 'this' so GameServer can call updateClientCount)
        server = new GameServer(this);

        setSize(400, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main panel with GridBagLayout
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(COLOR_BG_DARK);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // Status label (Server ON/OFF)
        statusLabel = new JLabel("Server is OFF");
        statusLabel.setFont(new Font("Verdana", Font.BOLD, 20));
        statusLabel.setForeground(COLOR_PEACH);

        // Client count label
        clientCountLabel = new JLabel("Clients Connected: 0");
        clientCountLabel.setForeground(COLOR_WHITE);
        clientCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

        // Start button
        startButton = new JButton("Start Server");
        startButton.setFocusPainted(false);
        styleButton(startButton, COLOR_HOT_PINK, COLOR_WHITE);

        startButton.addActionListener(e -> onStartServer());

        // Stop button
        stopButton = new JButton("Stop Server");
        stopButton.setFocusPainted(false);
        styleButton(stopButton, COLOR_PURPLE, COLOR_WHITE);
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> onStopServer());

        // Add components to the panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(statusLabel, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 2;
        mainPanel.add(clientCountLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(startButton, gbc);

        gbc.gridx = 1;
        mainPanel.add(stopButton, gbc);

        add(mainPanel);
    }

    /**
     * Helper method to give a uniform style to buttons.
     */
    private void styleButton(JButton button, Color bgColor, Color fgColor) {
        button.setBackground(bgColor);
        button.setForeground(fgColor);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
    }

    private void onStartServer() {
        if (!isRunning.get()) {
            isRunning.set(true);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            statusLabel.setText("Server is ON");
            statusLabel.setForeground(COLOR_PEACH);

            // Start server in background thread
            new Thread(() -> {
                server.startServer(12345);
            }).start();
        }
    }

    private void onStopServer() {
        if (isRunning.get()) {
            server.stopServer();
            isRunning.set(false);

            startButton.setEnabled(true);
            stopButton.setEnabled(false);

            statusLabel.setText("Server is OFF");
            statusLabel.setForeground(COLOR_PEACH);
            clientCountLabel.setText("Clients Connected: 0");
        }
    }

    /**
     * Called by GameServer whenever a new client joins
     * or a client disconnects. Updates the count label.
     */
    public void updateClientCount(int count) {
        SwingUtilities.invokeLater(() -> {
            clientCountLabel.setText("Clients Connected: " + count);
        });
    }

    public static void main(String... args) {
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
