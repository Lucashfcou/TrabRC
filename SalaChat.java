import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SalaChat {
    private final String roomName;
    private final Set<String> members;

    public SalaChat(String name) {
        this.roomName = name;
        this.members = Collections.synchronizedSet(new HashSet<>());
    }

    public String getRoomName() { 
        return roomName; 
    }

    public void addMember(String nickname) { 
        members.add(nickname); 
    }
    
    public void removeMember(String nickname) { 
        members.remove(nickname); 
    }
    
    public boolean isMember(String nickname) { 
        return members.contains(nickname); 
    }
    
    public int getMemberCount() { 
        return members.size(); 
    }

    public Set<String> getMembersSnapshot() {
        synchronized (members) {
            return Collections.unmodifiableSet(new HashSet<>(members));
        }
    }

    public void forEachMember(java.util.function.Consumer<String> action) {
        synchronized (members) {
            for (String m : members) {
                action.accept(m);
            }
        }
    }
}
