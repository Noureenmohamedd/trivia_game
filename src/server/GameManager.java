package server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import model.Question;

public class GameManager {
    private final QuestionManager questionManager;
    private final ScoreManager scoreManager;
    private final ConfigManager configManager;
    private final Map<String, Team> teams = new ConcurrentHashMap<>();

    public GameManager(QuestionManager questionManager, ScoreManager scoreManager, ConfigManager configManager) {
        this.questionManager = questionManager;
        this.scoreManager = scoreManager;
        this.configManager = configManager;
    }

    public synchronized String createTeam(String teamName, ClientHandler creator) {
        if (teams.containsKey(teamName)) {
            return "409 TEAM_NAME_ALREADY_EXISTS";
        }
        Team team = new Team(teamName, creator);
        teams.put(teamName, team);
        creator.setTeamName(teamName);
        return "200 TEAM_CREATED " + teamName;
    }


  public synchronized String joinTeam(String teamName, ClientHandler player) {

    if (player.getTeamName() != null) {
        return "400 ALREADY_IN_TEAM";
    }

    Team team = teams.get(teamName);

    if (team == null) {
        return "404 TEAM_NOT_FOUND";
    }

    int max = configManager.getInt("MAX_PLAYERS_PER_TEAM", 4);

    if (team.size() >= max) {
        return "400 TEAM_IS_FULL";
    }

    team.addMember(player);
    player.setTeamName(teamName);

    return "200 JOINED_TEAM " + teamName;

    }

    public synchronized String startSinglePlayerGame(ClientHandler player, String category, String difficulty, int count) {
        List<Question> questions = questionManager.getQuestions(category, difficulty, count);
        if (questions.isEmpty()) {
            return "404 NO_QUESTIONS_FOUND";
        }
        GameSession session = new GameSession(
                "S-" + UUID.randomUUID().toString().substring(0, 8),
                "single",
                category,
                difficulty,
                questions,
                List.of(player),
                scoreManager,
                configManager.getInt("QUESTION_TIME_SECONDS", 30)
        );
        player.setCurrentSession(session);
        new Thread(session).start();
        return "200 SINGLE_GAME_STARTING";
    }

    public synchronized String startTeamGame(String teamAName, String teamBName, ClientHandler requester,
                                             String category, String difficulty, int count) {
        Team teamA = teams.get(teamAName);
        Team teamB = teams.get(teamBName);
        if (teamA == null || teamB == null ) {
            return "404 TEAM_NOT_FOUND";
        }


        // Only a team creator can start a match        if (teamA.getCreator() != requester && teamB.getCreator() != requester) {
      boolean isCreator = requester == teamA.getCreator() || requester == teamB.getCreator();

            if (!isCreator) {
                 return "403 ONLY_TEAM_CREATOR_CAN_START";
                }


        // Prevent starting a new game if any player is already in a session
        for (ClientHandler p : teamA.getMembers()) {
            if (p.getCurrentSession() != null) {
                return "400 PLAYERS_ALREADY_IN_GAME";
            }
        }
        for (ClientHandler p : teamB.getMembers()) {
            if (p.getCurrentSession() != null) {
                return "400 PLAYERS_ALREADY_IN_GAME";
            }
        }
        if (teamA.size() != teamB.size()) {
            return "400 TEAM_SIZE_MISMATCH";
        }
        int min = configManager.getInt("MIN_PLAYERS_PER_TEAM", 1);
        if (teamA.size() < min || teamB.size() < min) {
            return "400 NOT_ENOUGH_PLAYERS";
        }
        List<Question> questions = questionManager.getQuestions(category, difficulty, count);
        if (questions.isEmpty()) {
            return "404 NO_QUESTIONS_FOUND";
        }
        List<ClientHandler> players = new ArrayList<>();
        players.addAll(teamA.getMembers());
        players.addAll(teamB.getMembers());
        GameSession session = new GameSession(
                "T-" + UUID.randomUUID().toString().substring(0, 8),
                "team",
                category,
                difficulty,
                questions,
                players,
                scoreManager,
                configManager.getInt("QUESTION_TIME_SECONDS", 30)
        );
        for (ClientHandler p : players) {
            p.setCurrentSession(session);
        }
        new Thread(session).start();
        return "200 TEAM_GAME_STARTING";
    }

    public synchronized void handleDisconnect(ClientHandler player) {
        String teamName = player.getTeamName();
        if (teamName != null) {
            Team team = teams.get(teamName);
            if (team != null) {
                team.removeMember(player);
                if (team.getMembers().isEmpty()) {
                    teams.remove(teamName);
                }
            }
        }
        if (player.getCurrentSession() != null) {
            player.getCurrentSession().removePlayer(player);
        }
    }

    public String listTeams() {

    if (teams.isEmpty()) {
        return "Available Teams:\n(no teams yet)";
    }

    StringBuilder sb = new StringBuilder("Available Teams:\n\n");

    for (Team team : teams.values()) {

        sb.append("Team: ").append(team.getName()).append(" (");

        List<ClientHandler> members = team.getMembers();

        for (int i = 0; i < members.size(); i++) {

            sb.append(members.get(i).getUsername());

            if (i < members.size() - 1) {
                sb.append(", ");
            }
        }

        sb.append(")\n");
    }

    return sb.toString();
}
    public synchronized String startMultiTeamGame(List<String> teamNames, ClientHandler requester,
                                                 String category, String difficulty, int count) {
        if (teamNames == null || teamNames.size() < 2) {
            return "400 INVALID_TEAM_COUNT";
        }

        List<Team> selected = new ArrayList<>();
        for (String tn : teamNames) {
            Team t = teams.get(tn);
            if (t == null) {
                return "404 TEAM_NOT_FOUND|" + tn;
            }
            selected.add(t);
        }

        // Keep existing rule: only a team creator can start a match
        // For multi-team, requester must be creator of Team 1 (first entered)
        if (selected.get(0).getCreator() != requester) {
            return "403 ONLY_TEAM_CREATOR_CAN_START";
        }

        // Prevent starting a new game if any player in any selected team is already in a session
        for (Team t : selected) {
            for (ClientHandler p : t.getMembers()) {
                if (p.getCurrentSession() != null) {
                    return "400 PLAYERS_ALREADY_IN_GAME";
                }
            }
        }

        int min = configManager.getInt("MIN_PLAYERS_PER_TEAM", 1);
        int expectedSize = selected.get(0).size();
        boolean sizeMismatch = false;
        StringBuilder sizes = new StringBuilder();
        for (Team t : selected) {
            int size = t.size();
            if (size < min) {
                return "400 NOT_ENOUGH_PLAYERS";
            }
            if (size != expectedSize) {
                sizeMismatch = true;
            }
            sizes.append(t.getName()).append(" : ").append(size).append(" players\n");
        }
        if (sizeMismatch) {
           return "400 TEAM_SIZE_MISMATCH\nTeams must have equal number of players.\n\n"
        + sizes
        + "\n1) Try again\n2) Back to main menu";
        }

        List<Question> questions = questionManager.getQuestions(category, difficulty, count);
        if (questions.isEmpty()) {
            return "404 NO_QUESTIONS_FOUND";
            
        }

        List<ClientHandler> players = new ArrayList<>();
        for (Team t : selected) {
            players.addAll(t.getMembers());
        }

        GameSession session = new GameSession(
                "T-" + UUID.randomUUID().toString().substring(0, 8),
                "team",
                category,
                difficulty,
                questions,
                players,
                scoreManager,
                configManager.getInt("QUESTION_TIME_SECONDS", 30)
        );
        for (ClientHandler p : players) {
            p.setCurrentSession(session);
        }
        new Thread(session).start();
        return "200 TEAM_GAME_STARTING";
    }
}
