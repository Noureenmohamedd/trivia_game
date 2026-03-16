package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import model.ScoreRecord;
import model.User;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final UserManager userManager;
    private final GameManager gameManager;
    private final ScoreManager scoreManager;
    private final QuestionManager questionManager;
    private final ConfigManager configManager;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile User user;
    private volatile boolean running = true;
    private volatile GameSession currentSession;
    private volatile String teamName;

    public ClientHandler(Socket socket, UserManager userManager, GameManager gameManager,
                         ScoreManager scoreManager, QuestionManager questionManager, ConfigManager configManager) {
        this.socket = socket;
        this.userManager = userManager;
        this.gameManager = gameManager;
        this.scoreManager = scoreManager;
        this.questionManager = questionManager;
        this.configManager = configManager;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            sendWelcome();

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("-".equals(line) || line.equalsIgnoreCase("QUIT")) {
                    if (currentSession != null) {
                        currentSession.removePlayer(this);
                    }
                    sendMessage("Goodbye.");
                    break;
                }
                handleCommand(line);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void sendWelcome() {
        sendMessage("=== Multiplayer Trivia Game ===\n" +
                "Type - anytime to quit\n");
    }

    private void handleCommand(String line) {
        try {
            String[] parts;
            if (line.contains("|")) {
                parts = line.split("\\|", -1);
            } else {
                parts = line.split("\\s+");
            }
            String command = parts[0].trim().toUpperCase();

            if (user == null) {
                switch (command) {
                    case "REGISTER" -> handleRegister(parts);
                    case "LOGIN" -> handleLogin(parts);
                    default -> sendMessage("400 Please login or register first.");
                }
                return;
            }

            if (currentSession != null && command.equals("ANSWER")) {
                if (parts.length < 2) {
                    sendMessage("400 Usage: ANSWER A");
                    return;
                }
                currentSession.submitAnswer(this, parts[1]);
                return;
            }

            switch (command) {
                case "MENU" -> sendMenu();
                case "PLAY_SINGLE" -> handlePlaySingle(parts);
                case "CREATE_TEAM" -> handleCreateTeam(parts);
                case "JOIN_TEAM" -> handleJoinTeam(parts);
                case "LIST_TEAMS" -> sendMessage(gameManager.listTeams());
                case "START_TEAM_GAME" -> handleStartTeamGame(parts);
                case "HISTORY" -> handleHistory();
                case "LOGOUT" -> handleLogout();
                case "ANSWER" -> sendMessage("400 NO_ACTIVE_QUESTION");
                default -> sendMessage("400 INVALID_COMMAND");
            }
        } catch (Exception e) {
            sendMessage("500 ERROR " + e.getMessage());
        }
    }

    private void handleRegister(String[] parts) throws IOException {
        if (parts.length < 4) {
            sendMessage("400 Usage: REGISTER <name> <username> <password>");
            return;
        }
        String name = parts[1].trim();
        String username = parts[2].trim();
        String password = parts[3].trim();
        boolean success = userManager.register(name, username, password);
        if (success) {
            sendMessage("200 REGISTER_SUCCESS");
        } else {
            sendMessage("409 USERNAME_ALREADY_EXISTS");
        }
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            sendMessage("400 Usage: LOGIN <username> <password>");
            return;
        }
        String username = parts[1].trim();
        String password = parts[2].trim();
        int code = userManager.login(username, password);
        if (code == 200) {
            this.user = userManager.getUser(username);
            sendMessage("200 LOGIN_SUCCESS|" + user.getName());
            sendMenu();
        } else if (code == 401) {
            sendMessage("401 UNAUTHORIZED");
        } else {
            sendMessage("404 USER_NOT_FOUND");
        }
    }

    private void sendMenu() {
        sendMessage("200 MENU_READY");
    }

    private void handlePlaySingle(String[] parts) {
        if (parts.length < 4) {
            sendMessage("400 Usage: PLAY_SINGLE <category> <difficulty> <questionCount>");
            return;
        }
        int count;
        try {
            count = Integer.parseInt(parts[3].trim());
        } catch (NumberFormatException e) {
            sendMessage("400 questionCount must be a number");
            return;
        }
        String category = parts[1].trim();
        String difficulty = parts[2].trim();
        if (!questionManager.isValidCategory(category) || !questionManager.isValidDifficulty(difficulty)) {
            sendMessage("400 INVALID_CATEGORY_OR_DIFFICULTY");
            return;
        }
        if (count <= 0 || count > 5) {
            sendMessage("400 INVALID_QUESTION_COUNT");
            return;
        }
        String response = gameManager.startSinglePlayerGame(this, category, difficulty, count);
        sendMessage(response);
    }

    private void handleCreateTeam(String[] parts) {
        if (parts.length < 2) {
            sendMessage("400 Usage: CREATE_TEAM <teamName>");
            return;
        }
        String team = parts[1].trim();
        if (team.isEmpty()) {
            sendMessage("400 INVALID_TEAM_NAME");
            return;
        }
        sendMessage(gameManager.createTeam(team, this));
    }

    private void handleJoinTeam(String[] parts) {
        if (parts.length < 2) {
            sendMessage("400 Usage: JOIN_TEAM <teamName>");
            return;
        }
        String team = parts[1].trim();
        if (team.isEmpty()) {
            sendMessage("400 INVALID_TEAM_NAME");
            return;
        }
        sendMessage(gameManager.joinTeam(team, this));
    }

    private void handleStartTeamGame(String[] parts) {
        // Legacy: START_TEAM_GAME <teamA> <teamB> <category> <difficulty> <questionCount>
        // New: START_TEAM_GAME|<teamCount>|team1|team2|...|<category>|<difficulty>|<questionCount>
        if (parts.length >= 6 && !parts[1].matches("\\d+")) {
            int count;
            try {
                count = Integer.parseInt(parts[5].trim());
            } catch (NumberFormatException e) {
                sendMessage("400 questionCount must be a number");
                return;
            }
            String category = parts[3].trim();
            String difficulty = parts[4].trim();
            if (!questionManager.isValidCategory(category) || !questionManager.isValidDifficulty(difficulty)) {
                sendMessage("400 INVALID_CATEGORY_OR_DIFFICULTY");
                return;
            }
            if (count <= 0 || count > 5) {
                sendMessage("400 INVALID_QUESTION_COUNT");
                return;
            }
            sendMessage(gameManager.startTeamGame(parts[1].trim(), parts[2].trim(), this, parts[3].trim(), parts[4].trim(), count));
            return;
        }

        if (parts.length < 5) {
            sendMessage("400 Usage: START_TEAM_GAME|<teamCount>|team1|team2|...|<category>|<difficulty>|<questionCount>");
            return;
        }

        int teamCount;
        try {
            teamCount = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            sendMessage("400 INVALID_TEAM_COUNT");
            return;
        }
        int expectedLen = 1 /*cmd*/ + 1 /*teamCount*/ + teamCount /*teams*/ + 3 /*cat,diff,count*/;
        if (teamCount < 2 || parts.length != expectedLen) {
            sendMessage("400 INVALID_START_TEAM_GAME_FORMAT");
            return;
        }

        List<String> teamNames = new java.util.ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            String tn = parts[2 + i].trim();
            if (tn.isEmpty()) {
                sendMessage("400 INVALID_TEAM_NAME");
                return;
            }
            teamNames.add(tn);
        }
        String category = parts[2 + teamCount].trim();
        String difficulty = parts[3 + teamCount].trim();
        int count;
        try {
            count = Integer.parseInt(parts[4 + teamCount].trim());
        } catch (NumberFormatException e) {
            sendMessage("400 questionCount must be a number");
            return;
        }

        if (!questionManager.isValidCategory(category) || !questionManager.isValidDifficulty(difficulty)) {
            sendMessage("400 INVALID_CATEGORY_OR_DIFFICULTY");
            return;
        }
        if (count <= 0 || count > 5) {
            sendMessage("400 INVALID_QUESTION_COUNT");
            return;
        }

        sendMessage(gameManager.startMultiTeamGame(teamNames, this, category, difficulty, count));
    }

    private void handleHistory() {
        int max = configManager.getInt("MAX_HISTORY_RECORDS", 10);
        List<ScoreRecord> records = scoreManager.getHistory(user.getUsername(), max);
        if (records.isEmpty()) {
            sendMessage("No score history yet.");
            return;
        }
        StringBuilder sb = new StringBuilder("Last score records:\n");
       for (ScoreRecord record : records) {

    sb.append("\n--- Game Record ---\n");
    sb.append("Mode       : ").append(record.getMode()).append("\n");
    sb.append("Category   : ").append(record.getCategory()).append("\n");
    sb.append("Difficulty : ").append(record.getDifficulty()).append("\n");
    sb.append("Score      : ").append(record.getScore()).append("\n");
    sb.append("Correct    : ").append(record.getCorrect()).append("\n");
    sb.append("Wrong      : ").append(record.getIncorrect()).append("\n");
    sb.append("Played At  : ").append(record.getTimestamp()).append("\n");
}
        sendMessage(sb.toString());
    }

    public void sendMessage(String message) {
        writer.println(message);
    }

    public String getUsername() {
        return user != null ? user.getUsername() : "unknown";
    }

    public String getDisplayName() {
        if (user == null) return getUsername();
        String n = user.getName();
        return (n == null || n.isBlank()) ? user.getUsername() : n;
    }

    public void setCurrentSession(GameSession currentSession) {
        this.currentSession = currentSession;
    }

    public GameSession getCurrentSession() {
        return currentSession;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    private void handleLogout() {
        if (currentSession != null) {
            sendMessage("400 CANNOT_LOGOUT_DURING_GAME");
            return;
        }
        this.user = null;
        this.teamName = null;
        sendMessage("200 LOGOUT_SUCCESS");
        sendWelcome();
    }

    private void cleanup() {
        running = false;
        gameManager.handleDisconnect(this);
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
