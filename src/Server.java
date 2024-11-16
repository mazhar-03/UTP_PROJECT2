import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private int port;
    private String serverName;
    private List<String> bannedPhrases;
    private final Map<String, Socket> clients;
    private final ExecutorService clientPool;

    public Server(String configFilePath) {
        loadConfiguration(configFilePath);
        this.clients = new HashMap<>();
        this.clientPool = Executors.newCachedThreadPool();
    }

    private void loadConfiguration(String configFilePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            this.port = Integer.parseInt(reader.readLine().split("=")[1].trim());
            this.serverName = reader.readLine().split("=")[1].trim();
            this.bannedPhrases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("bannedPhrase=")) {
                    this.bannedPhrases.add(line.split("=", 2)[1].trim());
                }
            }
            System.out.println("Server configuration loaded successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load server configuration: " + e.getMessage());
        }
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println(serverName + " is running on port " + port + ".");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(new ClientHandler(clientSocket, this));
            }
        } catch (IOException e) {
            System.err.println("Error in server: " + e.getMessage());
        } finally {
            clientPool.shutdown();
        }
    }

    public synchronized void addClient(String username, Socket socket) {
        clients.put(username, socket);
    }

    public synchronized void removeClient(Socket socket) {
        String username = null;
        for (Map.Entry<String, Socket> entry : clients.entrySet()) {
            if (entry.getValue().equals(socket)) {
                username = entry.getKey();
                break;
            }
        }
        if (username != null) {
            clients.remove(username);
        }
    }

    public synchronized List<String> getClientNames() {
        return new ArrayList<>(clients.keySet());
    }

    public synchronized boolean sendMessageToUser(String message, String username) {
        if (containsBannedPhrase(message)) {
            return false;
        }
        Socket socket = clients.get(username);
        if (socket != null) {
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                writer.write(message);
                writer.newLine();
                writer.flush();
                return true;
            } catch (IOException e) {
                System.err.println("Error sending message to user " + username + ": " + e.getMessage());
            }
        }
        return false;
    }

    public synchronized void broadcastMessage(String message, Socket senderSocket) {
        if (containsBannedPhrase(message)) {
            notifySenderOfBlockedMessage(senderSocket);
            return;
        }
        for (Map.Entry<String, Socket> entry : clients.entrySet()) {
            Socket socket = entry.getValue();
            if (socket != senderSocket) {
                try {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    writer.write(message);
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    System.err.println("Error broadcasting message to " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
    }

    public synchronized void broadcastMessageExcluding(String message, Socket senderSocket, String[] excludedUsers) {
        if (containsBannedPhrase(message)) {
            notifySenderOfBlockedMessage(senderSocket);
            return;
        }
        Set<String> excludedSet = new HashSet<>(Arrays.asList(excludedUsers));
        for (Map.Entry<String, Socket> entry : clients.entrySet()) {
            if (!excludedSet.contains(entry.getKey()) && !entry.getValue().equals(senderSocket)) {
                try {
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(entry.getValue().getOutputStream()));
                    writer.write(message);
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    System.err.println("Error broadcasting message to " + entry.getKey() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean containsBannedPhrase(String message) {
        return bannedPhrases.stream().anyMatch(message.toLowerCase()::contains);
    }

    private void notifySenderOfBlockedMessage(Socket senderSocket) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(senderSocket.getOutputStream()));
            writer.write("[BLOCKED] Your message contains a banned phrase and was not sent.");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error notifying sender about blocked message: " + e.getMessage());
        }
    }

    public List<String> getBannedPhrases() {
        return bannedPhrases;
    }

    public static void main(String[] args) {
        String configFilePath = "server_details.txt"; // Update this path as needed
        Server server = new Server(configFilePath);
        server.start();
    }
}