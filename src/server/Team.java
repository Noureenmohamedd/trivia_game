package server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Team {
    private final String name;
    private final ClientHandler creator;
    private final List<ClientHandler> members = new CopyOnWriteArrayList<>();

    public Team(String name, ClientHandler creator) {
        this.name = name;
        this.creator = creator;
        this.members.add(creator);
    }

    public String getName() {
        return name;
    }

    public ClientHandler getCreator() {
        return creator;
    }

    public List<ClientHandler> getMembers() {
        return members;
    }

    public void addMember(ClientHandler handler) {
        if (!members.contains(handler)) {
            members.add(handler);
        }
    }

    public void removeMember(ClientHandler handler) {
        members.remove(handler);
    }

    public int size() {
        return members.size();
    }
}
