import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server {
    private int port;
    private String serverName;
    private List<String> bannedPhrases;
    private final ExecutorService clientPool;
    private final List<Socket> connectedClients;

    public Server(String configFilePath) {
        loadConfiguration(configFilePath);
        this.clientPool = Executors.newCachedThreadPool();
        this.connectedClients = Collections.synchronizedList(new ArrayList<>());
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
                connectedClients.add(clientSocket);
                clientPool.submit(new ClientHandler(clientSocket, this));
            }
        } catch (IOException e) {
            System.err.println("Error in server: " + e.getMessage());
        } finally {
            shutdownExecutor();
        }
    }

    private void shutdownExecutor() {
        clientPool.shutdown();
        try {
            if (!clientPool.awaitTermination(60, TimeUnit.SECONDS)) {
                clientPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientPool.shutdownNow();
        }
    }

    public synchronized void broadcastMessage(String message, Socket senderSocket) {
        synchronized (connectedClients) {
            for (Socket clientSocket : connectedClients) {
                if (clientSocket != senderSocket) {
                    try {
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                        writer.write(message);
                        writer.newLine();
                        writer.flush();
                    } catch (IOException e) {
                        System.err.println("Error sending message to a client: " + e.getMessage());
                    }
                }
            }
        }
    }

    public synchronized void removeClient(Socket clientSocket) {
        connectedClients.remove(clientSocket);
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing client socket: " + e.getMessage());
        }
    }

    public List<String> getBannedPhrases() {
        return bannedPhrases;
    }

    public String getServerName(){
        return serverName;
    }
    public static void main(String[] args) {
        String configFilePath = "server_config.txt"; // Update this path as needed
        Server server = new Server(configFilePath);
        server.start();
    }
}
