package server;

import common.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final Map<String, String> values = new HashMap<>();

    public void load() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(Constants.CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                values.put(parts[0].trim(), parts[1].trim());
            }
        }
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(values.getOrDefault(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }
}
