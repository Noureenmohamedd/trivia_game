package server;

import common.Constants;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import model.ScoreRecord;

public class ScoreManager {
    private final List<ScoreRecord> records = Collections.synchronizedList(new ArrayList<>());

    public void load() throws IOException {
        File file = new File(Constants.SCORES_FILE);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split("\\|");
                if (p.length == 8) {
                    records.add(new ScoreRecord(p[0], p[1], p[2], p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), p[7]));
                }
            }
        }
    }

    public synchronized void addRecord(ScoreRecord record) throws IOException {
        records.add(record);
        persist();
    }

public List<ScoreRecord> getHistory(String username, int max) {

    List<ScoreRecord> userRecords = records.stream()
            .filter(r -> r.getUsername().equalsIgnoreCase(username))
            .collect(Collectors.toList());

    if (userRecords.isEmpty()) {
        return Collections.emptyList();
    }

    ScoreRecord last = userRecords.get(userRecords.size() - 1);

    return List.of(last);
}

    private void persist() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.SCORES_FILE))) {
            for (ScoreRecord record : records) {
                writer.write(record.toString());
                writer.newLine();
            }
        }
    }
}
