package model;

import java.util.List;

public class Question {
    private final String id;
    private final String category;
    private final String difficulty;
    private final String text;
    private final List<String> choices;
    private final char correctAnswer;

    public Question(String id, String category, String difficulty, String text, List<String> choices, char correctAnswer) {
        this.id = id;
        this.category = category;
        this.difficulty = difficulty;
        this.text = text;
        this.choices = choices;
        this.correctAnswer = Character.toUpperCase(correctAnswer);
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getText() {
        return text;
    }

    public List<String> getChoices() {
        return choices;
    }

    public char getCorrectAnswer() {
        return correctAnswer;
    }
}
