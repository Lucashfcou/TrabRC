import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

public class ClientState {
    
    public enum ConnectionState {
        INITIAL,
        READY,
        IN_ROOM
    }

    private ConnectionState state;
    private String clientNickname;
    private String activeRoom;
    private final SocketChannel connection;
    private final ByteBuffer inputBuffer;
    private StringBuilder partialMessage;

    public ClientState(SocketChannel connection) {
        this.connection = connection;
        this.state = ConnectionState.INITIAL;
        this.clientNickname = null;
        this.activeRoom = null;
        this.inputBuffer = ByteBuffer.allocate(2048);
        this.partialMessage = new StringBuilder();
    }

    public ConnectionState getState() {
        return state;
    }

    public String getNickname() {
        return clientNickname;
    }

    public String getRoom() {
        return activeRoom;
    }

    public SocketChannel getConnection() {
        return connection;
    }

    public ByteBuffer getInputBuffer() {
        return inputBuffer;
    }

    public StringBuilder getPartialMessage() {
        return partialMessage;
    }

    public void setState(ConnectionState newState) {
        this.state = newState;
    }

    public void setNickname(String nickname) {
        this.clientNickname = nickname;
    }

    public void setRoom(String room) {
        this.activeRoom = room;
    }

    public void resetPartialMessage() {
        this.partialMessage = new StringBuilder();
    }

    public void setPartialMessage(StringBuilder message) {
        this.partialMessage = message;
    }

    public boolean hasNickname() {
        return clientNickname != null && !clientNickname.isEmpty();
    }

    public boolean isInRoom() {
        return activeRoom != null && !activeRoom.isEmpty();
    }

    public boolean isInitial() {
        return state == ConnectionState.INITIAL;
    }

    public boolean isReady() {
        return state == ConnectionState.READY;
    }

    public boolean isInChatRoom() {
        return state == ConnectionState.IN_ROOM;
    }

    public boolean moveToReady(String nickname) {
        if (state != ConnectionState.INITIAL) {
            return false;
        }
        this.clientNickname = nickname;
        this.state = ConnectionState.READY;
        return true;
    }

    public boolean joinRoom(String room) {
        if (state != ConnectionState.READY && state != ConnectionState.IN_ROOM) {
            return false;
        }
        this.activeRoom = room;
        this.state = ConnectionState.IN_ROOM;
        return true;
    }

    public boolean exitRoom() {
        if (state != ConnectionState.IN_ROOM) {
            return false;
        }
        this.activeRoom = null;
        this.state = ConnectionState.READY;
        return true;
    }

    public void cleanup() {
        this.clientNickname = null;
        this.activeRoom = null;
        this.state = ConnectionState.INITIAL;
        clearAllBuffers();
    }

    public void clearAllBuffers() {
        this.inputBuffer.clear();
        this.partialMessage = new StringBuilder();
    }

    public boolean canSendMessage() {
        return isInChatRoom();
    }

    public boolean canJoinRoom() {
        return isReady() || isInChatRoom();
    }

    public boolean canSetNickname() {
        return true;
    }

    public String getDebugInfo() {
        return String.format("ClientState{nick=%s, state=%s, room=%s}",
            clientNickname != null ? clientNickname : "none",
            state,
            activeRoom != null ? activeRoom : "none");
    }

    @Override
    public String toString() {
        return String.format("Client[%s|%s|%s]",
            clientNickname != null ? clientNickname : "?",
            state,
            activeRoom != null ? activeRoom : "-");
    }
}
