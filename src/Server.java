import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    private int port;
    private String serverName;
    private List<String> bannedPhrases;
    private final Map<String, Socket> clients; //shared object

    public Server(String configFilePath) {
        loadConfiguration(configFilePath);
        this.clients = new HashMap<>();
    }

    private void loadConfiguration(String configFilePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            this.port = Integer.parseInt(reader.readLine().split("=")[1].trim());
            this.serverName = reader.readLine().split("=")[1].trim();
            this.bannedPhrases = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("bannedPhrase=")) {
                    bannedPhrases.add(line.split("=", 2)[1].trim());
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
                new Thread(new ClientHandler(clientSocket, this)).start();
            }
        } catch (IOException e) {
            System.err.println("Error in server: " + e.getMessage());
        }
    }

    public synchronized boolean addClient(String username, Socket socket) {
        if (clients.containsKey(username))
            return false;
        clients.put(username, socket);
        return true;
    }

    //other threads will have to wait until the method finishes before accessing or modifying the map.
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

    private void notifySenderOfBlockedMessage(Socket senderSocket) {
        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(senderSocket.getOutputStream()));
            writer.write("Your message contains a banned phrase and was not sent.");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error notifying sender about blocked message: " + e.getMessage());
        }
    }

    private boolean containsBannedPhrase(String message) {
        return bannedPhrases.stream().anyMatch(message.toLowerCase()::contains);
    }

    // accessed by multiple threads simultaneously
    public synchronized List<String> getClientNames() {
        return new ArrayList<>(clients.keySet());
    }

    public List<String> getBannedPhrases() {
        return bannedPhrases;
    }

    public static void main(String[] args) {
        String configFilePath = "config_files/server_details.txt"; // Update this path as needed
        Server server = new Server(configFilePath);
        server.start();
    }
}