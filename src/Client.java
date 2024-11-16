import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private final String serverHost;
    private final int serverPort;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Client(String host, int port) {
        this.serverHost = host;
        this.serverPort = port;
    }

    public void start() {
        try {
            socket = new Socket(serverHost, serverPort);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            System.out.println("Connected to the server.");

            // Start a thread to handle incoming messages
            Thread receiveThread = new Thread(this::receiveMessages);
            receiveThread.start();

            // Handle outgoing messages from user input
            handleOutgoingMessages();
        } catch (IOException e) {
            System.err.println("Failed to connect to the server: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handleOutgoingMessages() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Type 'exit' to disconnect.");

            while (true) {
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("exit")) {
                    sendMessage("exit");
                    System.out.println("Disconnecting from the server...");
                    break;
                }

                if (message.isBlank()) {
                    System.out.println("Cannot send an empty message. Please type something.");
                    continue;
                }

                sendMessage(message);
            }
        } catch (IOException e) {
            System.err.println("Error sending message: " + e.getMessage());
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.startsWith("[BLOCKED]")) {
                    System.out.println("Server blocked your message: " + message.substring(9));
                } else {
                    System.out.println(message);
                }
            }
        } catch (IOException e) {
            System.err.println("Error receiving messages: " + e.getMessage());
        }
    }

    public void sendMessage(String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    private void closeConnection() {
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 12345;

        Client client = new Client(host, port);
        client.start();
    }
}