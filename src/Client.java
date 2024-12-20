import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {
    private final String serverHost;
    private final int serverPort;
    private final String username;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final ExecutorService executorService;

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
        }
        this.serverHost = host;
        this.serverPort = port;
        this.username = user;
        this.executorService = Executors.newFixedThreadPool(2);
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

            //killing the app
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (socket != null && !socket.isClosed()) {
                        writer.write("exit"); // Notify the server
                        writer.newLine();
                        writer.flush();
                        closeConnection();
                    }
                } catch (IOException e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
            }));

            // start a task to listen for incoming messages
            executorService.submit(() -> {
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
            });

            // Handle outgoing messages by send
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String userInput;
            while ((userInput = consoleReader.readLine()) != null) {
                //cleans the client's resources and stops the reading user input.
                if (userInput.equalsIgnoreCase("exit")) {
                    writer.write(userInput);
                    writer.newLine();
                    writer.flush();
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
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) {
        // reading the args array
        String configFilePath = args[0];

        try {
            // Create and start the client using the provided configuration file
            Client client = new Client(configFilePath);
            client.start();
        } catch (Exception e) {
            System.err.println("Error starting the client: " + e.getMessage());
        }
    }
}
