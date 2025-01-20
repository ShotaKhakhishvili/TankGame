import java.net.Socket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

/**
 * Handles communication with a single client.
 * A separate thread is used to read commands from the client
 * and pass them to the server's game logic.
 */
public class ClientHandler extends Thread {
    private int playerId;
    private Socket socket;
    private GameServer server;

    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean running = true;
    private boolean loginCompleted = false;

    public ClientHandler(int playerId, Socket socket, GameServer server) {
        this.playerId = playerId;
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // 1) Construct output stream first
            out = new ObjectOutputStream(socket.getOutputStream());
            // 2) Immediately send the playerId to this client
            out.writeInt(playerId);
            out.flush();

            // 3) Then construct input stream
            in = new ObjectInputStream(socket.getInputStream());

            // Continuously read commands from this client
            while (running) {
                Object obj = in.readObject();
                if (obj instanceof Command) {
                    Command cmd = (Command) obj;
                    server.receiveCommand(playerId, cmd);
                }else if(obj instanceof LoginAttempt){
                    LoginAttempt loginAttempt = (LoginAttempt)obj;
                    server.receiveLoginAttempt(playerId, loginAttempt);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Client " + playerId + " disconnected.");
        } finally {
            close();
        }
    }

    public void sendGameState(GameState state) {
        if (out != null && loginCompleted) {
            try {
                out.reset();
                out.writeObject(state);
                out.flush();
            } catch (IOException e) {
                System.out.println("Failed to send game state to player " + playerId);
                close();
            }
        }
    }

    public void answerLoginAttempt(LoginAttempt loginAttempt) throws IOException {
        if(out != null){
            out.reset();
            out.writeObject(loginAttempt);
            out.flush();

            System.out.println("The login was successful with an username " + loginAttempt.username);
            loginCompleted = true;
        }
    }

    private void close() {
        running = false;
        server.removeClient(playerId);

        try { if (in != null) in.close(); } catch (IOException e) {}
        try { if (out != null) out.close(); } catch (IOException e) {}
        try { if (socket != null) socket.close(); } catch (IOException e) {}
    }
}
