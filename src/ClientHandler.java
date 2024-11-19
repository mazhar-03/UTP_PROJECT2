import java.io.*;
import java.net.Socket;

class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Server server;

    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
    }

    public void run() {
        String clientName = null;
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            // Reading username and adding client
            clientName = reader.readLine();

            // Attempt to add the client
            if (!server.addClient(clientName, clientSocket)) {
                writer.write("Username already in use. Connection rejected.");
                writer.newLine();
                writer.flush();
                return; // Exit the thread if username is duplicate
            }


//            server.broadcastMessage(clientName + " has joined the chat.", clientSocket);
            server.broadcastMessage(clientName + " has entered the chat.", clientSocket);

            writer.write("Connected clients: " + String.join(", ", server.getClientNames()));
            writer.newLine();
            writer.write("Instructions:");
            writer.newLine();
            writer.write("1. Type your message to broadcast it to everyone.");
            writer.newLine();
            writer.write("2. Use '/send <username1,username2> <message>' to send to specific users.");
            writer.newLine();
            writer.write("3. Use '/exclude <username1,username2> <message>' to broadcast excluding specific users.");
            writer.newLine();
            writer.write("4. Use '/banned' to query banned phrases.");
            writer.newLine();
            writer.write("5. Type 'exit' to disconnect.");
            writer.newLine();
            writer.flush();

            // Listening for messages
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.trim().isEmpty()) {
                    sendMessage("Cannot send an empty message. Please type something.");
                    continue;
                }

                if (message.equalsIgnoreCase("exit")) {
//                    server.broadcastMessage(clientName + " has left the chat.", clientSocket);
                    server.removeClient(clientSocket);
                    break; // Exit the loop gracefully
                }


                if (message.equalsIgnoreCase("/banned")) {
                    sendMessage("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
                    continue;
                }

                if (message.startsWith("/send")) {
                    handleSendCommand(message, clientName);
                    continue;
                }

                if (message.startsWith("/exclude")) {
                    handleExcludeCommand(message, clientName);
                    continue;
                }

                if (server.getBannedPhrases().stream().anyMatch(message.toLowerCase()::contains)) {
                    sendMessage("Your message contains a banned phrase and will not be broadcast.");
                } else {
                    server.broadcastMessage(clientName + ": " + message, clientSocket);
                }
            }
        } catch (IOException e) {
            if (e.getMessage() == null && !e.getMessage().contains("Connection reset")) {
                System.err.println("Error handling client: " + e.getMessage());
            }
        }
        finally {
            if (clientName != null) {
                server.removeClient(clientSocket);
                server.broadcastMessage(clientName + " has left the chat.", clientSocket);
            }
        }
    }

    private void handleSendCommand(String message, String clientName) throws IOException {
        String[] parts = message.split(" ", 3);

        if (parts.length < 3) {
            sendMessage("Usage: /send <username1,username2> <message>");
            return;
        }

        String[] recipients = parts[1].split(",");
        String userMessage = parts[2];

        if (server.getBannedPhrases().stream().anyMatch(userMessage.toLowerCase()::contains)) {
            sendMessage("Your message contains a banned phrase and will not be sent.");
            return;
        }

        for (String recipient : recipients) {
            recipient = recipient.trim();
            boolean success = server.sendMessageToUser(clientName + " (private): " + userMessage, recipient);
            if (!success) {
                sendMessage("User " + recipient + " not found. Available clients: " + String.join(", ", server.getClientNames()));
            }
        }
    }

    private void handleExcludeCommand(String message, String clientName) throws IOException {
        String[] parts = message.split(" ", 3);
        if (parts.length < 3) {
            sendMessage("Usage: /exclude <username1,username2> <message>");
            return;
        }

        String[] excludedUsers = parts[1].split(",");
        String userMessage = parts[2];

        if (server.getBannedPhrases().stream().anyMatch(userMessage.toLowerCase()::contains)) {
            sendMessage("Your message contains a banned phrase and will not be broadcast.");
            return;
        }

        server.broadcastMessageExcluding(clientName + ": " + userMessage, clientSocket, excludedUsers);
    }


    public void sendMessage(String message) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
}