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
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            // Reading username and adding client
            clientName = reader.readLine();

            // Attempt to add the client
            if (!server.addClient(clientName, clientSocket)) {
                sendMessage("Username already in use. Connection rejected.");
                return; // Exit the thread if username is duplicate
            }

            server.broadcastMessage(clientName + " has entered the chat.", clientSocket);
            sendInstructions();

            // Listening for messages
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.trim().isEmpty()) {
                    sendMessage("Cannot send an empty message. Please type something.");
                    continue;
                }
                if (message.equalsIgnoreCase("exit")) {
                    break; //exit from the loop
                }
                if (message.equalsIgnoreCase("/banned")) {
                    sendMessage("Banned phrases: " + String.join(", ", server.getBannedPhrases()));
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
            //no errors when client killed the program
            if (e.getMessage() == null && !e.getMessage().contains("Connection reset")) {
                System.err.println("Error handling client: " + e.getMessage());
            }
        } finally {
            server.removeClient(clientSocket);
            server.broadcastMessage(clientName + " has left the chat.", clientSocket);

        }
    }

    private void sendInstructions() throws IOException {
        sendMessage("Connected clients: " + String.join(", ", server.getClientNames()));
        sendMessage("Instructions:");
        sendMessage("1. Type your message to broadcast it to everyone.");
        sendMessage("2. Use '/send <username1,username2> <message>' to send to specific users.");
        sendMessage("3. Use '/exclude <username1,username2> <message>' to broadcast excluding specific users.");
        sendMessage("4. Use '/banned' to query banned phrases.");
        sendMessage("5. Type 'exit' to disconnect.");
        System.out.println();
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