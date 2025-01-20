import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {

    // All active client handlers, keyed by player ID
    private Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    private GameLogic gameLogic = new GameLogic();

    private ServerSocket serverSocket = null;
    private volatile boolean running = false;

    private int nextPlayerId = 1;
    private ServerGUI gui; // reference to the GUI

    public GameServer(ServerGUI gui) {
        this.gui = gui;
    }

    /**
     * Start the server on a given port.
     */
    public void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started, listening on port " + port);

            // Main game loop in a separate thread
            new Thread(this::gameLoop).start();

            // Accept loop
            while (running) {
                Socket clientSocket = serverSocket.accept();
                if (!running) break; // in case stopServer() was called

                System.out.println("New client connected: " + clientSocket);

                int playerId = nextPlayerId++;
                ClientHandler handler = new ClientHandler(playerId, clientSocket, this);
                clients.put(playerId, handler);
                handler.start();

                // Register in the game logic
                gameLogic.addPlayer(playerId);

                // Update the GUI client count
                gui.updateClientCount(clients.size());
            }

        } catch (IOException e) {
            System.out.println("Server stopped or port unavailable: " + e.getMessage());
        } finally {
            // Cleanup if the loop ends
            closeAllClients();
            closeServerSocket();
        }
    }

    /**
     * The main update loop for the server, ~60 FPS.
     */
    private void gameLoop() {
        final int FPS = 60;
        final long frameTime = 1000 / FPS;

        while (running) {
            long start = System.currentTimeMillis();

            gameLogic.update();
            broadcastGameState();

            long end = System.currentTimeMillis();
            long sleepTime = frameTime - (end - start);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Cleanly stop the server: closes the server socket
     * and all client connections, ends the accept loop.
     */
    public void stopServer() {
        running = false;

        // This will break the accept() call
        closeServerSocket();

        // Close all client connections
        closeAllClients();
        clients.clear();

        // Reset nextPlayerId if you want
        // nextPlayerId = 1;

        // Update the GUI to show 0 clients
        gui.updateClientCount(0);

        System.out.println("Server stopped.");
    }

    /**
     * Called from ClientHandler when a new Command arrives.
     */
    public void receiveCommand(int playerId, Command cmd) {
        gameLogic.handleCommand(playerId, cmd);
    }

    public void receiveLoginAttempt(int playerId, LoginAttempt loginAttempt) {
        try {
            loginAttempt.accessAllowed = gameLogic.handleLoginAttempt(playerId, loginAttempt);
            clients.get(playerId).answerLoginAttempt(loginAttempt);
        }catch (IOException e){
            System.out.println("Some problem answering a login attempt for player ID: " + playerId);
        }
    }

    /**
     * Send the current game state to all clients.
     */
    private void broadcastGameState() {
        GameState state = gameLogic.buildGameState();
        for (ClientHandler ch : clients.values()) {
            ch.sendGameState(state);
        }
    }

    /**
     * Called from ClientHandler.close() if a client disconnects.
     */
    public void removeClient(int playerId) {
        clients.remove(playerId);
        gameLogic.removePlayer(playerId);
        gui.updateClientCount(clients.size());
    }

    /**
     * Helper to close the server socket if open.
     */
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper to close all clients.
     */
    private void closeAllClients() {
        for (ClientHandler ch : clients.values()) {
            ch.interrupt();  // forcibly close each client
        }
    }


}
