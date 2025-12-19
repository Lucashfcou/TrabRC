import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;

public class EstadoCliente {
    
    public enum EstadoConexao {
        INICIAL,
        PRONTO,
        EM_SALA
    }

    private EstadoConexao state;
    private String clientNickname;
    private String activeRoom;
    private final SocketChannel connection;
    private final ByteBuffer inputBuffer;
    private StringBuilder partialMessage;

    public EstadoCliente(SocketChannel connection) {
        this.connection = connection;
        this.state = EstadoConexao.INICIAL;
        this.clientNickname = null;
        this.activeRoom = null;
        this.inputBuffer = ByteBuffer.allocate(2048);
        this.partialMessage = new StringBuilder();
    }

    public EstadoConexao getState() {
        return state;
    }

    public String getNickname() {
        return clientNickname;
    }

    public String getCurrentRoom() {
        return activeRoom;
    }

    public SocketChannel getChannel() {
        return connection;
    }

    public ByteBuffer getReadBuffer() {
        return inputBuffer;
    }

    public StringBuilder getLineBuffer() {
        return partialMessage;
    }

    public void setState(EstadoConexao newState) {
        this.state = newState;
    }

    public void setNickname(String nickname) {
        this.clientNickname = nickname;
    }

    public void setCurrentRoom(String room) {
        this.activeRoom = room;
    }

    public void clearLineBuffer() {
        this.partialMessage = new StringBuilder();
    }

    public void setLineBuffer(StringBuilder message) {
        this.partialMessage = message;
    }

    public boolean hasNickname() {
        return clientNickname != null && !clientNickname.isEmpty();
    }

    public boolean isInRoom() {
        return activeRoom != null && !activeRoom.isEmpty();
    }

    public boolean isInitialState() {
        return state == EstadoConexao.INICIAL;
    }

    public boolean isReadyState() {
        return state == EstadoConexao.PRONTO;
    }

    public boolean isInRoomState() {
        return state == EstadoConexao.EM_SALA;
    }

    public boolean transitionToReady() {
        if (state != EstadoConexao.INICIAL && state != EstadoConexao.EM_SALA) {
            return false;
        }
        this.state = EstadoConexao.PRONTO;
        return true;
    }

    public boolean transitionToInRoom() {
        if (state != EstadoConexao.PRONTO && state != EstadoConexao.EM_SALA) {
            return false;
        }
        this.state = EstadoConexao.EM_SALA;
        return true;
    }

    public boolean exitRoom() {
        if (state != EstadoConexao.EM_SALA) {
            return false;
        }
        this.activeRoom = null;
        this.state = EstadoConexao.PRONTO;
        return true;
    }

    public void cleanup() {
        this.clientNickname = null;
        this.activeRoom = null;
        this.state = EstadoConexao.INICIAL;
        clearAllBuffers();
    }

    public void clearAllBuffers() {
        this.inputBuffer.clear();
        this.partialMessage = new StringBuilder();
    }

    public boolean canSendMessage() {
        return isInRoomState();
    }

    public boolean canJoinRoom() {
        return isReadyState() || isInRoomState();
    }

    public boolean canSetNickname() {
        return true;
    }

    public String getDebugInfo() {
        return String.format("EstadoCliente{nick=%s, state=%s, room=%s}",
            clientNickname != null ? clientNickname : "none",
            state,
            activeRoom != null ? activeRoom : "none");
    }

    @Override
    public String toString() {
        return String.format("Cliente[%s|%s|%s]",
            clientNickname != null ? clientNickname : "?",
            state,
            activeRoom != null ? activeRoom : "-");
    }
}
