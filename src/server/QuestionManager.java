package server;

import common.Constants;
import model.Question;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class QuestionManager {
    private final List<Question> questions = new ArrayList<>();
    private final Set<String> categories = new LinkedHashSet<>();
    private final Set<String> difficulties = new LinkedHashSet<>();

    public void load() throws IOException {
        File file = new File(Constants.QUESTIONS_FILE);
        if (!file.exists()) {
            throw new IOException("Missing questions file: " + Constants.QUESTIONS_FILE);
        }

        questions.clear();
        categories.clear();
        difficulties.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                // Format:
                // QuestionText|Category|Difficulty|A|B|C|D|CorrectLetter
                String[] parts = line.split("\\|", -1);
                if (parts.length != 8) {
                    continue;
                }
                String text = parts[0].trim();
                String category = parts[1].trim();
                String difficulty = parts[2].trim();
                String a = parts[3].trim();
                String b = parts[4].trim();
                String c = parts[5].trim();
                String d = parts[6].trim();
                String correctRaw = parts[7].trim();

                if (text.isEmpty() || category.isEmpty() || difficulty.isEmpty()
                        || a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()
                        || correctRaw.isEmpty()) {
                    continue;
                }

                char correct = Character.toUpperCase(correctRaw.charAt(0));
                if (correct < 'A' || correct > 'D') {
                    continue;
                }

                List<String> choices = List.of(a, b, c, d);
                String id = "Q" + (questions.size() + 1);
                questions.add(new Question(id, category, difficulty, text, choices, correct));
                categories.add(category);
                difficulties.add(difficulty);
            }
        }
    }

    public List<Question> getQuestions(String category, String difficulty, int count) {
        List<Question> filtered = questions.stream()
                .filter(q -> q.getCategory().equalsIgnoreCase(category))
                .filter(q -> q.getDifficulty().equalsIgnoreCase(difficulty))
                .collect(Collectors.toList());

        Collections.shuffle(filtered);
        if (filtered.size() < count) {
            return new ArrayList<>(filtered);
        }
        return new ArrayList<>(filtered.subList(0, count));
    }

    public List<String> getCategories() {
        return categories.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
    }

    public List<String> getDifficulties() {
        return difficulties.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean isValidCategory(String category) {
        if (category == null) return false;
        return categories.stream().anyMatch(c -> c.equalsIgnoreCase(category.trim()));
    }

    public boolean isValidDifficulty(String difficulty) {
        if (difficulty == null) return false;
        return difficulties.stream().anyMatch(d -> d.equalsIgnoreCase(difficulty.trim()));
    }
}
