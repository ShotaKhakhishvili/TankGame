import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;

public class LoginClient extends JFrame {
    private JTextField usernameField, hostField, portField;
    private JLabel statusLabel;

    public static void main(String[] args) {
        new LoginClient().setVisible(true);
    }

    public LoginClient() {
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
        String username = usernameField.getText().trim();
        String host = hostField.getText().trim();
        int port = Integer.parseInt(portField.getText().trim());

        if (username.isEmpty()) {
            statusLabel.setText("Username cannot be empty.");
            return;
        }

        try(Socket socket = new Socket(host, port)) {
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            int playerId = in.readInt();

            // 1) Send the desired username
            LoginAttempt loginAttempt = new LoginAttempt();
            loginAttempt.username = username;
            out.writeObject(loginAttempt);
            out.flush();

            while(true) {
                Object input = in.readObject();

                if (input instanceof LoginAttempt) {
                    if (((LoginAttempt) input).accessAllowed) {
                        SwingUtilities.invokeLater(() -> {
                            new GameClient().setVisible(true);
                        });

                        this.setVisible(false);

                        break;
                    }
                }
                else {
                    System.out.println(input.getClass());
                    break;
                }
            }

        } catch (IOException e) {
            statusLabel.setText("Some problem occurred while connection");
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
