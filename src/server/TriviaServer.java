package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TriviaServer {
    public static void main(String[] args) {
        UserManager userManager = new UserManager();
        QuestionManager questionManager = new QuestionManager();
        ScoreManager scoreManager = new ScoreManager();
        ConfigManager configManager = new ConfigManager();

        try {
            configManager.load();
            userManager.load();
            questionManager.load();
            scoreManager.load();
        } catch (IOException e) {
            System.err.println("Failed to load server data: " + e.getMessage());
            return;
        }

        GameManager gameManager = new GameManager(questionManager, scoreManager, configManager);
        int port = configManager.getInt("SERVER_PORT", 5000);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Trivia Server started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Client connected: " + socket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(socket, userManager, gameManager, scoreManager, questionManager, configManager);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
