package model;

public class ScoreRecord {

    private final String username;
    private final String mode;
    private final String category;
    private final String difficulty;
    private final int score;
    private final int correct;
    private final int incorrect;
    private final String timestamp;

    public ScoreRecord(String username, String mode, String category, String difficulty,
                       int score, int correct, int incorrect, String timestamp) {
        this.username = username;
        this.mode = mode;
        this.category = category;
        this.difficulty = difficulty;
        this.score = score;
        this.correct = correct;
        this.incorrect = incorrect;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getMode() {
        return mode;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getScore() {
        return score;
    }

    public int getCorrect() {
        return correct;
    }

    public int getIncorrect() {
        return incorrect;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return username + "|" + mode + "|" + category + "|" + difficulty + "|" +
                score + "|" + correct + "|" + incorrect + "|" + timestamp;
    }
}