package client;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TriviaClient {
    private enum State {
        AUTH_MENU,
        REGISTER_NAME,
        REGISTER_USERNAME,
        REGISTER_PASSWORD,
        LOGIN_USERNAME,
        LOGIN_PASSWORD,
        MAIN_MENU,
        SINGLE_CATEGORY,
        SINGLE_DIFFICULTY,
        SINGLE_COUNT,
        CREATE_TEAM,
        JOIN_TEAM,
        JOIN_TEAM_NOT_FOUND_MENU,
        START_TEAM_TEAMCOUNT,
        START_TEAM_NAMES,

        START_TEAM_CATEGORY,
        START_TEAM_DIFFICULTY,
        START_TEAM_COUNT,
        START_TEAM_ERROR_MENU,

        IN_GAME
    }

    private enum ClientMode {
        MENU,
        LOGIN_USERNAME,
        LOGIN_PASSWORD,
        REGISTER_NAME,
        REGISTER_USERNAME,
        REGISTER_PASSWORD,
        IN_QUESTION,
        WAITING
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        try (Socket socket = new Socket(host, port);
                java.io.BufferedReader serverReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)) {

            BlockingQueue<String> serverMessages = new LinkedBlockingQueue<>();
            BlockingQueue<String> userInputs = new LinkedBlockingQueue<>();

            Thread listener = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = serverReader.readLine()) != null) {
                        serverMessages.offer(msg);
                    }
                } catch (IOException ignored) {
                } finally {
                    serverMessages.offer("__DISCONNECTED__");
                }
            }, "ServerListener");
            listener.setDaemon(true);
            listener.start();

            Thread inputThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted() && scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        userInputs.offer(line);
                    }
                } catch (IllegalStateException e) {
                    // ignore scanner closed
                } finally {
                    userInputs.offer("__EOF__");
                }
            }, "ConsoleInput");
            inputThread.setDaemon(true);
            inputThread.start();

            State state = State.AUTH_MENU;
            ClientMode mode = ClientMode.MENU;
            boolean running = true;
            boolean needsRender = true;

            String regName = null;
            String regUser = null;
            String regPass = null;

            String loginUser = null;
            String loginPass = null;

            String selectedCategory = null;
            String selectedDifficulty = null;
            Integer selectedCount = null;

            String pendingTeamName = null;
            String missingTeamName = null;

            Integer teamCount = null;
            java.util.List<String> teamNames = new java.util.ArrayList<>();

            while (running) {
                // Always drain server messages first so the client never "freezes" visually.
                String sm;
                while ((sm = serverMessages.poll()) != null) {
                    if ("__DISCONNECTED__".equals(sm)) {
                        System.out.println("Disconnected from server.");
                        running = false;
                        break;
                    }

                    // Handle a few key protocol events for state transitions.
                    if (sm.startsWith("200 LOGIN_SUCCESS|")) {
                        String name = sm.substring("200 LOGIN_SUCCESS|".length());
                        System.out.println("Welcome, " + name);
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }
                    if (sm.equals("200 REGISTER_SUCCESS")) {
                        System.out.println("Registration successful.");
                        System.out.println("Please login.");
                        state = State.LOGIN_USERNAME;
                        mode = ClientMode.LOGIN_USERNAME;
                        needsRender = true;
                        continue;
                    }
                    if (sm.equals("409 USERNAME_ALREADY_EXISTS")) {
                        System.out.println("Username already exists.");
                        state = State.REGISTER_USERNAME;
                        mode = ClientMode.REGISTER_USERNAME;
                        needsRender = true;
                        continue;
                    }
                    if (sm.equals("404 USER_NOT_FOUND")) {
                        System.out.println("Username not found.");
                        state = State.LOGIN_USERNAME;
                        mode = ClientMode.LOGIN_USERNAME;
                        needsRender = true;
                        continue;
                    }

                    if (sm.equals("401 UNAUTHORIZED")) {
                        System.out.println("Wrong password.");
                        state = State.LOGIN_PASSWORD;
                        mode = ClientMode.LOGIN_PASSWORD;
                        needsRender = true;
                        continue;
                    }
                    if (sm.equals("200 MENU_READY")) {
                        // Server confirms the session is ready for menu actions
                        if (state != State.IN_GAME) {
                            state = State.MAIN_MENU;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                        continue;
                    }
                    if (sm.equals("200 LOGOUT_SUCCESS")) {
                        state = State.AUTH_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }

                    if (sm.startsWith("400 TEAM_SIZE_MISMATCH")) {
                        System.out.println(sm);
                        state = State.START_TEAM_ERROR_MENU; // NEW STATE
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }

                    if (sm.startsWith("200 SINGLE_GAME_STARTING") || sm.startsWith("200 TEAM_GAME_STARTING")) {
                        state = State.IN_GAME;
                        mode = ClientMode.IN_QUESTION;
                        needsRender = false;
                        System.out.println("Starting game...");
                        continue;
                    }

                    if (sm.equals("403 ONLY_TEAM_CREATOR_CAN_START")) {
                        System.out.println("Only the team creator can start the game.");
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }
                    if (sm.startsWith("=== GAME OVER ===")) {
                        // Let the scoreboard print as normal afterwards.
                        System.out.println(sm);
                        System.out.println();
                        System.out.println("Returning to main menu...");
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }
                    if (sm.equals("404 TEAM_NOT_FOUND")) {
                        System.out.println("Team not found.");
                        System.out.println("Returning to Main Menu...");

                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;

                        continue;
                    }

                    if (sm.startsWith("404 TEAM_NOT_FOUND|")) {
                        missingTeamName = sm.substring("404 TEAM_NOT_FOUND|".length());
                        System.out.println("Team \"" + missingTeamName + "\" does not exist.");

                        System.out.println("Returning to Main Menu...");

                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;

                        continue;
                    }

                    if (sm.startsWith("200 JOINED_TEAM")) {
                        System.out.println(sm);
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }

                    if (sm.equals("400 TEAM_IS_FULL")) {
                        System.out.println("Team is full.");
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }

                    if (sm.startsWith("Available Teams:")) {
                        System.out.println(sm);

                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;

                        continue;
                    }
                    if (sm.equals("400 ALREADY_IN_TEAM")) {
                        System.out.println("You are already in a team.");
                        state = State.MAIN_MENU;
                        mode = ClientMode.MENU;
                        needsRender = true;
                        continue;
                    }

                    // Default: just print server messages.
                    // Detect question start
                    if (sm.toLowerCase().startsWith("question")) {
                        mode = ClientMode.IN_QUESTION;
                        state = State.IN_GAME;
                    }

                    // Detect end of question
                    if (sm.startsWith("TIME UP") || sm.startsWith("RESULT") || sm.startsWith("SCOREBOARD")) {
                        mode = ClientMode.WAITING;
                    }

                    System.out.println(sm);
                }
                if (!running)
                    break;

                if (needsRender) {
                    render(state, teamNames.size());
                    needsRender = false;
                }

                String input = userInputs.poll(200, TimeUnit.MILLISECONDS);
                if (input == null) {
                    continue;
                }
                input = input.trim();

                if ("__EOF__".equals(input)) {
                    writer.println("-");
                    break;
                }

                // Global quit: "-" works at any screen
                if (input.equals("-")) {
                    writer.println("QUIT");
                    System.out.println("Disconnecting...");
                    running = false;
                    inputThread.interrupt();
                    break;
                }

                // Restrict inputs based on client mode
                if (mode == ClientMode.IN_QUESTION) {
                    String up = input.toUpperCase(Locale.ROOT);
                    if (up.length() == 1 && up.charAt(0) >= 'A' && up.charAt(0) <= 'D') {
                        // Send ANSWER|X format
                        writer.println("ANSWER|" + up);
                    } else {
                        System.out.println("Invalid input. Please enter A, B, C, D or '-'");
                    }
                    continue;
                } else if (mode == ClientMode.WAITING) {
                    // Ignore user keystrokes while waiting for server (prevents menu actions
                    // mid-question)
                    continue;
                }

                switch (state) {
                    case AUTH_MENU -> {
                        switch (input) {
                            case "1" -> {
                                state = State.REGISTER_NAME;
                                mode = ClientMode.REGISTER_NAME;
                                needsRender = true;
                            }
                            case "2" -> {
                                state = State.LOGIN_USERNAME;
                                mode = ClientMode.LOGIN_USERNAME;
                                needsRender = true;
                            }
                            case "3" -> {
                                writer.println("QUIT");
                                running = false;
                            }
                            default -> {
                                System.out.println("Invalid input. Please choose again.");
                                needsRender = true;
                            }
                        }
                    }
                    case REGISTER_NAME -> {
                        if (input.isBlank()) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            regName = input;
                            state = State.REGISTER_USERNAME;
                            mode = ClientMode.REGISTER_USERNAME;
                            needsRender = true;
                        }
                    }
                    case REGISTER_USERNAME -> {
                        if (!isSimpleToken(input)) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            regUser = input;
                            state = State.REGISTER_PASSWORD;
                            mode = ClientMode.REGISTER_PASSWORD;
                            needsRender = true;
                        }
                    }

                    case START_TEAM_ERROR_MENU -> {
                        if (input.equals("1")) {
                            state = State.START_TEAM_TEAMCOUNT; // try again
                        } else if (input.equals("2")) {
                            System.out.println("Returning to Main Menu...");
                            state = State.MAIN_MENU;
                        } else {
                            System.out.println("Invalid input.");
                        }
                        needsRender = true;
                    }
                    case REGISTER_PASSWORD -> {
                        if (input.isBlank()) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            regPass = input;
                            writer.println("REGISTER|" + regName + "|" + regUser + "|" + regPass);
                            mode = ClientMode.WAITING;
                            // Wait for server reply. Rendering is driven by server message.
                        }
                    }
                    case LOGIN_USERNAME -> {
                        if (!isSimpleToken(input)) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            loginUser = input;
                            state = State.LOGIN_PASSWORD;
                            mode = ClientMode.LOGIN_PASSWORD;
                            needsRender = true;
                        }
                    }
                    case LOGIN_PASSWORD -> {
                        if (input.isBlank()) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            loginPass = input;
                            writer.println("LOGIN|" + loginUser + "|" + loginPass);
                            mode = ClientMode.WAITING;
                        }
                    }
                    case MAIN_MENU -> {
                        switch (input) {
                            case "1" -> {
                                state = State.SINGLE_CATEGORY;
                                mode = ClientMode.MENU;
                                needsRender = true;
                            }
                            case "2" -> {
                                state = State.CREATE_TEAM;
                                mode = ClientMode.MENU;
                                needsRender = true;
                            }
                            case "3" -> {
                                state = State.JOIN_TEAM;
                                mode = ClientMode.MENU;
                                needsRender = true;
                            }
                            case "4" -> {
                                writer.println("LIST_TEAMS");
                                mode = ClientMode.WAITING;
                                needsRender = false;
                            }
                            case "5" -> {
                                teamCount = null;
                                teamNames.clear();
                                state = State.START_TEAM_TEAMCOUNT;
                                mode = ClientMode.MENU;
                                needsRender = true;
                            }
                            case "6" -> writer.println("HISTORY");
                            case "7" -> {
                                writer.println("LOGOUT");
                                mode = ClientMode.WAITING;
                            }
                            case "8" -> {
                                writer.println("QUIT");
                                running = false;
                            }
                            default -> {
                                System.out.println("Invalid input. Please choose again.");
                                needsRender = true;
                            }
                        }
                    }
                    case SINGLE_CATEGORY -> {
                        selectedCategory = mapCategoryChoice(input);
                        if (selectedCategory == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            state = State.SINGLE_DIFFICULTY;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }
                    case SINGLE_DIFFICULTY -> {
                        selectedDifficulty = mapDifficultyChoice(input);
                        if (selectedDifficulty == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            state = State.SINGLE_COUNT;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }
                    case SINGLE_COUNT -> {
                        selectedCount = parsePositiveInt(input);
                        if (selectedCount == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            writer.println(
                                    "PLAY_SINGLE|" + selectedCategory + "|" + selectedDifficulty + "|" + selectedCount);
                            mode = ClientMode.WAITING;
                        }
                    }
                    case CREATE_TEAM -> {
                        pendingTeamName = input;
                        if (!isSimpleToken(pendingTeamName)) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            writer.println("CREATE_TEAM|" + pendingTeamName);
                            state = State.MAIN_MENU;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }
                    case JOIN_TEAM -> {
                        pendingTeamName = input;
                        if (!isSimpleToken(pendingTeamName)) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            writer.println("JOIN_TEAM|" + pendingTeamName);
                        }
                    }
                    case JOIN_TEAM_NOT_FOUND_MENU -> {
                        if ("1".equals(input)) {
                            state = State.JOIN_TEAM;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        } else if ("2".equals(input)) {
                            state = State.MAIN_MENU;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        } else {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        }
                    }
                    case START_TEAM_TEAMCOUNT -> {
                        Integer num = parsePositiveInt(input);

                        if ("0".equals(input.trim())) {
                            System.out.println("Returning to Main Menu... ");
                            state = State.MAIN_MENU;
                            needsRender = true;
                            break;
                        }

                        if (num == null) {
                            System.out.println("Invalid input. Please enter a number.");
                            needsRender = true;
                            break;
                        }

                        if (num < 2) {
                            System.out.println("Enter at least 2 teams or 0 to go back.");
                            needsRender = true;
                            break;
                        }

                        teamCount = num;
                        teamNames.clear();
                        state = State.START_TEAM_NAMES;
                        mode = ClientMode.MENU;
                        needsRender = true;
                    }
                    case START_TEAM_NAMES -> {
                        if (!isSimpleToken(input)) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                            continue;
                        }
                        teamNames.add(input);
                        if (teamNames.size() < teamCount) {
                            needsRender = true;
                        } else {
                            state = State.START_TEAM_CATEGORY;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }

                    case START_TEAM_CATEGORY -> {
                        selectedCategory = mapCategoryChoice(input);
                        if (selectedCategory == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            state = State.START_TEAM_DIFFICULTY;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }
                    case START_TEAM_DIFFICULTY -> {
                        selectedDifficulty = mapDifficultyChoice(input);
                        if (selectedDifficulty == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            state = State.START_TEAM_COUNT;
                            mode = ClientMode.MENU;
                            needsRender = true;
                        }
                    }
                    case START_TEAM_COUNT -> {
                        selectedCount = parsePositiveInt(input);
                        if (selectedCount == null) {
                            System.out.println("Invalid input. Please choose again.");
                            needsRender = true;
                        } else {
                            StringBuilder cmd = new StringBuilder();
                            cmd.append("START_TEAM_GAME|").append(teamCount);
                            for (String tn : teamNames) {
                                cmd.append("|").append(tn);
                            }
                            cmd.append("|").append(selectedCategory)
                                    .append("|").append(selectedDifficulty)
                                    .append("|").append(selectedCount);
                            writer.println(cmd.toString());
                            mode = ClientMode.WAITING;
                        }
                    }
                    case IN_GAME -> {
                        // handled above
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Client error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Client interrupted.");
        }
    }

    private static void render(State state, int enteredTeams) {
        switch (state) {
            case AUTH_MENU -> {
                System.out.println("=== Multiplayer Trivia Game ===");
                System.out.println();
                System.out.println("1) Register");
                System.out.println("2) Login");
                System.out.println("3) Quit");
                System.out.println();
                System.out.print("Choose option: ");
            }
            case REGISTER_NAME -> System.out.print("Enter your name: ");
            case REGISTER_USERNAME -> System.out.print("Enter username: ");
            case REGISTER_PASSWORD -> System.out.print("Enter password: ");
            case LOGIN_USERNAME -> System.out.print("Enter username: ");
            case LOGIN_PASSWORD -> System.out.print("Enter password: ");
            case MAIN_MENU -> {
                System.out.println("=== MAIN MENU ===");
                System.out.println();
                System.out.println("1) Single Player");
                System.out.println("2) Create Team");
                System.out.println("3) Join Team");
                System.out.println("4) List Teams");
                System.out.println("5) Start Team Game");
                System.out.println("6) History");
                System.out.println("7) Logout");
                System.out.println("8) Quit");
                System.out.println();
                System.out.print("Choose option: ");
            }
            case SINGLE_CATEGORY, START_TEAM_CATEGORY -> {
                System.out.println("Choose Category:");
                System.out.println();
                System.out.println("1) Geography");
                System.out.println("2) Math");
                System.out.println("3) Science");
                System.out.println();
                System.out.print("Choose option: ");
            }
            case SINGLE_DIFFICULTY, START_TEAM_DIFFICULTY -> {
                System.out.println("Choose Difficulty:");
                System.out.println();
                System.out.println("1) Easy");
                System.out.println("2) Medium");
                System.out.println("3) Hard");
                System.out.println();
                System.out.print("Choose option: ");
            }
            case SINGLE_COUNT, START_TEAM_COUNT -> System.out.print("Enter number of questions: ");
            case CREATE_TEAM -> System.out.print("Enter team name: ");
            case JOIN_TEAM -> System.out.print("Enter team name: ");

            case START_TEAM_TEAMCOUNT -> System.out.print("How many teams will play? ");
            case START_TEAM_NAMES -> {
                int next = enteredTeams + 1;
                System.out.print("Enter Team " + next + " name: ");
            }

            case IN_GAME -> {
                // No menu rendering during gameplay; the server drives question display.
            }
            default -> {
            }
        }
    }

    private static String mapCategoryChoice(String input) {
        return switch (input) {
            case "1" -> "Geography";
            case "2" -> "Math";
            case "3" -> "Science";
            default -> null;
        };
    }

    private static String mapDifficultyChoice(String input) {
        return switch (input) {
            case "1" -> "Easy";
            case "2" -> "Medium";
            case "3" -> "Hard";
            default -> null;
        };
    }

    private static Integer parsePositiveInt(String input) {
        try {
            int v = Integer.parseInt(input.trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSimpleToken(String s) {
        if (s == null)
            return false;
        String t = s.trim();
        if (t.isEmpty())
            return false;
        // Avoid protocol delimiter breaking and whitespace issues.
        return !t.contains("|") && !t.contains(" ");
    }
}
