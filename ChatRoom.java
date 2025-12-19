import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.function.Consumer;

public class ChatRoom {
    
    private final String name;
    private final Set<String> participants;

    public ChatRoom(String name) {
        this.name = name;
        this.participants = Collections.synchronizedSet(new HashSet<>());
    }

    public String getName() {
        return name;
    }

    public void addParticipant(String nickname) {
        participants.add(nickname);
    }

    public void removeParticipant(String nickname) {
        participants.remove(nickname);
    }

    public boolean hasParticipant(String nickname) {
        return participants.contains(nickname);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public Set<String> getParticipants() {
        synchronized (participants) {
            return Collections.unmodifiableSet(new HashSet<>(participants));
        }
    }

    public void forEachParticipant(Consumer<String> action) {
        synchronized (participants) {
            for (String participant : participants) {
                action.accept(participant);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("ChatRoom{name='%s', participants=%d}", name, participants.size());
    }
}
