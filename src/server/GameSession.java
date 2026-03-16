package server;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import model.Question;
import model.ScoreRecord;

public class GameSession implements Runnable {
    private final String sessionId;
    private final String mode;
    private final String category;
    private final String difficulty;
    private final List<Question> questions;
    private final List<ClientHandler> players;
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();
    private final Map<String, Integer> correctCount = new ConcurrentHashMap<>();
    private final Map<String, Integer> incorrectCount = new ConcurrentHashMap<>();
    private final Map<String, Character> submittedAnswers = new ConcurrentHashMap<>();
    private final Set<String> answeredUsers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean questionActive = new AtomicBoolean(false);
    private final ScoreManager scoreManager;
    private final int questionTimeSeconds;
    private volatile Question currentQuestion;
    private volatile QuestionTimer timer;

    public GameSession(String sessionId,
                       String mode,
                       String category,
                       String difficulty,
                       List<Question> questions,
                       List<ClientHandler> players,
                       ScoreManager scoreManager,
                       int questionTimeSeconds) {
        this.sessionId = sessionId;
        this.mode = mode;
        this.category = category;
        this.difficulty = difficulty;
        this.questions = questions;
        this.players = new CopyOnWriteArrayList<>(players);
        this.scoreManager = scoreManager;
        this.questionTimeSeconds = questionTimeSeconds;
        for (ClientHandler player : players) {
            scores.put(player.getUsername(), 0);
            correctCount.put(player.getUsername(), 0);
            incorrectCount.put(player.getUsername(), 0);
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean submitAnswer(ClientHandler player, String rawAnswer) {

    if (!questionActive.get()) {
        player.sendMessage("400 NO_ACTIVE_QUESTION");
        return false;
    }

    String username = player.getUsername();

    if (answeredUsers.contains(username)) {
        player.sendMessage("400 ANSWER_ALREADY_SUBMITTED");
        return false;
    }

    if (rawAnswer == null || rawAnswer.trim().isEmpty()) {
        player.sendMessage("400 INVALID_ANSWER Use A, B, C, or D");
        return false;
    }

    char answer = Character.toUpperCase(rawAnswer.trim().charAt(0));

    if (answer < 'A' || answer > 'D') {
        player.sendMessage("400 INVALID_ANSWER Use A, B, C, or D");
        return false;
    }

    answeredUsers.add(username);
    submittedAnswers.put(username, answer);

    player.sendMessage("200 ANSWER_ACCEPTED");

    // ⭐ هنا التعديل المهم
    if (answer == currentQuestion.getCorrectAnswer()) {

        if (questionActive.compareAndSet(true, false)) {

            broadcast(player.getDisplayName() + " answered correctly!");

            if (timer != null) {
               timer.cancel();
            }
        }
    }

    return true;
}

    private int pointsForDifficulty() {
        return switch (difficulty.toLowerCase()) {
            case "easy" -> 10;
            case "medium" -> 15;
            case "hard" -> 20;
            default -> 10;
        };
    }

    @Override
    public void run() {
        try {
            broadcast("=== GAME STARTED | Session: " + sessionId + " | Mode: " + mode + " ===");
            int qIndex = 1;
            for (Question q : questions) {
                if (players.isEmpty()) {
                    return;
                }
                currentQuestion = q;
                submittedAnswers.clear();
                answeredUsers.clear();
                questionActive.set(true);
                broadcastQuestion(qIndex, q);

                timer = new QuestionTimer(questionTimeSeconds);
                timer.await(remaining -> broadcast(remaining + " seconds remaining"));

                questionActive.set(false);
                evaluateCurrentQuestion();
                qIndex++;
            }
            endGame();
        } catch (InterruptedException e) {
            broadcast("GAME INTERRUPTED");
            Thread.currentThread().interrupt();
        }
    }

    private void broadcastQuestion(int index, Question q) {
        StringBuilder builder = new StringBuilder();
        builder.append("\nQuestion ").append(index).append(":\n\n")
                .append(q.getText()).append("\n\n");
        List<String> choices = q.getChoices();
        char label = 'A';
        for (String choice : choices) {
            builder.append(label++).append(") ").append(choice).append("\n");
        }
        builder.append("\nType A/B/C/D or '-' to quit");
        broadcast(builder.toString());
    }

    private void evaluateCurrentQuestion() {
        char correct = currentQuestion.getCorrectAnswer();
        int points = pointsForDifficulty();
        broadcast("TIME_UP. Correct answer: " + correct);

        for (ClientHandler player : players) {
            String username = player.getUsername();
            Character answer = submittedAnswers.get(username);
            if (answer != null && answer == correct) {
                scores.compute(username, (k, v) -> v == null ? points : v + points);
                correctCount.compute(username, (k, v) -> v == null ? 1 : v + 1);
                player.sendMessage("RESULT Correct! +" + points + " points. Total: " + scores.get(username));
            } else {
                incorrectCount.compute(username, (k, v) -> v == null ? 1 : v + 1);
                if (answer == null) {
                    player.sendMessage("RESULT No answer. Total: " + scores.get(username));
                } else {
                    player.sendMessage("RESULT Wrong answer. Your answer: " + answer + ". Total: " + scores.get(username));
                }
            }
        }

        broadcastScoreboard();
    }

    private void broadcastScoreboard() {
        StringBuilder builder = new StringBuilder("SCOREBOARD\n");
        for (ClientHandler player : players) {
            String u = player.getUsername();
            builder.append("- ").append(u).append(": ").append(scores.getOrDefault(u, 0)).append("\n");
        }
        broadcast(builder.toString());
    }

    private void endGame() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n=== GAME OVER ===\nFinal Scores:\n");
        for (ClientHandler player : players) {
            String u = player.getUsername();
            int score = scores.getOrDefault(u, 0);
            int correct = correctCount.getOrDefault(u, 0);
            int incorrect = incorrectCount.getOrDefault(u, 0);
            builder.append(u)
                    .append(" -> Score: ").append(score)
                    .append(", Correct: ").append(correct)
                    .append(", Incorrect/Timeout: ").append(incorrect)
                    .append("\n");
            try {
                scoreManager.addRecord(new ScoreRecord(u, mode, category, difficulty, score, correct, incorrect, LocalDateTime.now().toString()));
            } catch (IOException e) {
                player.sendMessage("WARN Failed to save score history: " + e.getMessage());
            }
            player.setCurrentSession(null);
        }
        broadcast(builder.toString());
    }

    public void removePlayer(ClientHandler handler) {
        boolean removed = players.remove(handler);
        if (removed) {
            String name = handler.getDisplayName();
            broadcast("Player " + name + " left the game.");
            handler.setCurrentSession(null);
        }
    }

    private void broadcast(String message) {
        for (ClientHandler player : players) {
            player.sendMessage(message);
        }
    }
}
