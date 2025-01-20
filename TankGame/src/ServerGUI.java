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

    private AtomicBoolean isRunning = new AtomicBoolean(false);

    // Color palette
    private static final Color COLOR_DARK_TEAL   = Color.decode("#264D59");
    private static final Color COLOR_TEAL        = Color.decode("#43978D");
    private static final Color COLOR_YELLOW      = Color.decode("#F9E07F");
    private static final Color COLOR_ORANGE      = Color.decode("#F9AD6A");
    private static final Color COLOR_REDORANGE   = Color.decode("#D46C4E");

    public ServerGUI() {
        super("Tank Game Server");

        // Create the server, passing 'this' so the server can call updateClientCount(...)
        server = new GameServer(this);

        setSize(400, 250);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(COLOR_DARK_TEAL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        statusLabel = new JLabel("Server is OFF");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        statusLabel.setForeground(Color.WHITE);

        clientCountLabel = new JLabel("Clients Connected: 0");
        clientCountLabel.setForeground(Color.WHITE);
        clientCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

        startButton = new JButton("Start Server");
        startButton.setBackground(COLOR_YELLOW);
        startButton.setForeground(Color.BLACK);
        startButton.setFocusPainted(false);
        startButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        startButton.addActionListener(e -> onStartServer());

        stopButton = new JButton("Stop Server");
        stopButton.setBackground(COLOR_ORANGE);
        stopButton.setForeground(Color.BLACK);
        stopButton.setFocusPainted(false);
        stopButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> onStopServer());

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        mainPanel.add(statusLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        mainPanel.add(clientCountLabel, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.weightx = 0.5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(startButton, gbc);

        gbc.gridx = 1;
        mainPanel.add(stopButton, gbc);

        add(mainPanel);
    }

    private void onStartServer() {
        if (!isRunning.get()) {
            isRunning.set(true);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            statusLabel.setText("Server is ON");
            statusLabel.setForeground(COLOR_YELLOW);

            // Start the server in a background thread so the UI doesn't freeze
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
            statusLabel.setForeground(Color.WHITE);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
