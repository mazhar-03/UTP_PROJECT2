import java.io.*;
import java.net.Socket;
import java.util.concurrent.Callable;

class ClientHandler implements Callable<Void> {
    private Socket clientSocket;
    private Server server;

    public ClientHandler(Socket clientSocket, Server server) {
        this.clientSocket = clientSocket;
        this.server = server;
    }

    @Override
    public Void call() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
        ) {
            writer.write("Please provide your username:");
            writer.newLine();
            writer.flush();

            String clientName = reader.readLine();
            if (clientName == null || clientName.trim().isEmpty()) {
                clientName = "Client-" + clientSocket.getPort();
            }

            String message;
            while ((message = reader.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    server.broadcastMessage(clientName + " has left the chat.", clientSocket);
                    break;
                }

                boolean containsBannedPhrase = server.getBannedPhrases().stream().anyMatch(message::contains);
                if (!containsBannedPhrase) {
                    String formattedMessage = clientName + ": " + message;
                    server.broadcastMessage(formattedMessage, clientSocket);
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            server.removeClient(clientSocket);
        }
        return null;
    }
}
