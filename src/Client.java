import java.io.*;
import java.net.Socket;

public class Client {
    private final String serverHost;
    private final int serverPort;
    private final String username;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Client(String configFilePath) {
        String host = null;
        int port = 0;
        String user = null;
        try (BufferedReader configReader = new BufferedReader(new FileReader(configFilePath))) {
            host = configReader.readLine().split("=")[1].trim();
            port = Integer.parseInt(configReader.readLine().split("=")[1].trim());
            user = configReader.readLine().split("=")[1].trim();
        } catch (IOException e) {
            System.err.println("Error reading client configuration: " + e.getMessage());
            System.exit(1);
        }
        this.serverHost = host;
        this.serverPort = port;
        this.username = user;
    }

    public void start() {
        try {
            // Connect to the server
            socket = new Socket(serverHost, serverPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            System.out.println("Connected to the server as " + username);

            // Send username to the server
            writer.write(username);
            writer.newLine();
            writer.flush();

            // Start a thread to listen for incoming messages
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = reader.readLine()) != null) {
                        System.out.println(serverMessage);
                    }
                } catch (IOException e) {
                    if (!e.getMessage().contains("Socket closed")) {
                        System.err.println("Connection to server lost.");
                    }
                }
            }).start();

            // Handle outgoing messages
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                if (userInput.equalsIgnoreCase("exit")) {
                    System.out.println("Disconnecting...");
                    break;
                }
                writer.write(userInput);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("Could not connect to the server: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Check if the configuration file path is provided as a program argument
        if (args.length < 1) {
            System.err.println("Usage: java Client <config_file_path>");
            return;
        }

        String configFilePath = args[0]; // Get the configuration file path from the arguments

        try {
            // Create and start the client using the provided configuration file
            Client client = new Client(configFilePath);
            client.start();
        } catch (Exception e) {
            System.err.println("Error starting the client: " + e.getMessage());
        }
    }
}
