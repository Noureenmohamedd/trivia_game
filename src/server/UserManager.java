package server;

import common.Constants;
import model.User;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public void load() throws IOException {
        File file = new File(Constants.USERS_FILE);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split(",", 3);
                if (parts.length == 3) {
                    User user = new User(parts[0].trim(), parts[1].trim(), parts[2].trim());
                    users.put(user.getUsername(), user);
                }
            }
        }
    }

    public synchronized boolean register(String name, String username, String password) throws IOException {
        if (users.containsKey(username)) {
            return false;
        }
        User user = new User(name, username, password);
        users.put(username, user);
        persist();
        return true;
    }

    public int login(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            return 404;
        }
        if (!user.getPassword().equals(password)) {
            return 401;
        }
        return 200;
    }

    public User getUser(String username) {
        return users.get(username);
    }

    private void persist() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Constants.USERS_FILE))) {
            for (User user : users.values()) {
                writer.write(user.toString());
                writer.newLine();
            }
        }
    }
}
