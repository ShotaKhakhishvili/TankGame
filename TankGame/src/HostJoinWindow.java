import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class HostJoinWindow extends JFrame {

    public HostJoinWindow() {
        // Basic window setup
        setTitle("Host or Join");
        setSize(300, 200);             // small and compact
        setLocationRelativeTo(null);   // center on screen
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Use a background color from your palette
        getContentPane().setBackground(new Color(0x41436A)); // dark bluish background

        // A main panel with a vertical layout
        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(new Color(0x41436A));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // Label at the top
        JLabel titleLabel = new JLabel("Tank Game");
        titleLabel.setForeground(new Color(0xFE9677)); // peach color
        titleLabel.setFont(new Font("Verdana", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // "Host" button
        JButton hostButton = new JButton("Host");
        hostButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        hostButton.setFocusPainted(false);

        // Host button styling
        hostButton.setBackground(new Color(0xF64668));  // bright pinkish
        hostButton.setForeground(Color.WHITE);
        hostButton.setFont(new Font("Verdana", Font.BOLD, 14));

        // On click, close this window and call host logic
        hostButton.addActionListener(e -> {
            dispose();
            // Example: Your actual hosting class/logic:
            ServerGUI.main();
        });

        // "Join" button
        JButton joinButton = new JButton("Join");
        joinButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinButton.setFocusPainted(false);

        // Join button styling
        joinButton.setBackground(new Color(0x984063)); // purple-ish from the palette
        joinButton.setForeground(Color.WHITE);
        joinButton.setFont(new Font("Verdana", Font.BOLD, 14));

        // On click, close this window and call join logic
        joinButton.addActionListener(e -> {
            dispose();
            // Example: Your actual joining class/logic:
             GameClient.main();
        });

        // Add some spacing and add components
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(hostButton);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(joinButton);
        mainPanel.add(Box.createVerticalStrut(20));

        // Add the panel to the frame
        add(mainPanel);

        // Show the window
        setVisible(true);
    }

    // For testing/demo purposes, you can run this main method:
    public static void main(String[] args) {
        SwingUtilities.invokeLater(HostJoinWindow::new);
    }
}
