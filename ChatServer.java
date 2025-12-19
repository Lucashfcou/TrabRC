import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ChatServer {
    
    private Selector channelSelector;
    private ServerSocketChannel serverChannel;
    
    private static final Map<String, ClientState> nicknameToClient = new ConcurrentHashMap<>();
    private static final Map<String, ChatRoom> activeRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java ChatServer <porta_tcp>");
            System.exit(1);
        }

        int port = 0;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Erro: A porta deve ser um número inteiro.");
            System.exit(1);
        }

        new ChatServer().initializeServer(port);
    }

    public void initializeServer(int port) {
        try {
            channelSelector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.register(channelSelector, SelectionKey.OP_ACCEPT);

            while (true) {
                channelSelector.select();
                Set<SelectionKey> selectedKeys = channelSelector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (key.isAcceptable()) {
                        handleNewConnection();
                    } else if (key.isReadable()) {
                        handleClientRead(key);
                    }
                }
            }
        } catch (IOException e) {
        }
    }

    private void handleNewConnection() throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        client.register(channelSelector, SelectionKey.OP_READ);
        
        ClientState clientState = new ClientState(client);
        client.keyFor(channelSelector).attach(clientState);
    }

    private void handleClientRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();
        ByteBuffer buffer = state.getInputBuffer();
        
        int bytesRead = -1;
        try {
            bytesRead = channel.read(buffer);
        } catch (IOException e) {
        }

        if (bytesRead == -1) {
            clientDisconnected(state);
            channel.close();
            return;
        }
        
        if (bytesRead > 0) {
            parseClientInput(state);
        }
    }

    private void parseClientInput(ClientState state) {
        ByteBuffer buffer = state.getInputBuffer();
        StringBuilder msgBuffer = state.getPartialMessage();
        
        buffer.flip();
        
        if (buffer.hasRemaining()) {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String receivedData = new String(data, StandardCharsets.UTF_8);
            
            msgBuffer.append(receivedData);
            extractCompleteLines(state);
        }
        
        buffer.clear();
    }

    private void extractCompleteLines(ClientState state) {
        StringBuilder msgBuffer = state.getPartialMessage();
        String accumulated = msgBuffer.toString();
        
        int lineStart = 0;
        boolean foundLines = false;
        
        for (int i = 0; i < accumulated.length(); i++) {
            char c = accumulated.charAt(i);
            if (c == '\n' || c == '\r') {
                String completeLine = accumulated.substring(lineStart, i).trim();
                
                if (c == '\r' && i + 1 < accumulated.length() && accumulated.charAt(i + 1) == '\n') {
                    i++;
                }
                lineStart = i + 1;
                foundLines = true;
                
                if (!completeLine.isEmpty()) {
                    processClientCommand(state, completeLine);
                }
            }
        }
        
        if (foundLines) {
            if (lineStart < accumulated.length()) {
                state.setPartialMessage(new StringBuilder(accumulated.substring(lineStart)));
            } else {
                state.resetPartialMessage();
            }
        }
    }

    private void processClientCommand(ClientState state, String message) {
        if (message.startsWith("/")) {
            executeCommand(state, message);
        } else {
            sendTextMessage(state, message);
        }
    }

    private void executeCommand(ClientState state, String message) {
        if (message.startsWith("//")) {
            sendTextMessage(state, message);
            return;
        }

        String[] parts = message.split("\\s+", 2);
        String command = parts[0];
        String argument = parts.length > 1 ? parts[1] : null;

        if (!isKnownCommand(command)) {
            sendTextMessage(state, message);
            return;
        }

        switch (command) {
            case "/nick":
                commandNick(state, argument);
                break;
            case "/join":
                commandJoin(state, argument);
                break;
            case "/leave":
                commandLeave(state);
                break;
            case "/bye":
                commandBye(state);
                break;
            case "/priv":
                if (argument != null) {
                    String[] privParts = argument.split("\\s+", 2);
                    if (privParts.length == 2) {
                        commandPrivate(state, privParts[0], privParts[1]);
                    } else {
                        sendToClient(state, "ERROR");
                    }
                } else {
                    sendToClient(state, "ERROR");
                }
                break;
            default:
                sendToClient(state, "ERROR");
        }
    }

    private boolean isKnownCommand(String command) {
        return command.equals("/nick") || command.equals("/join") || 
               command.equals("/leave") || command.equals("/bye") || 
               command.equals("/priv");
    }

    private void commandNick(ClientState state, String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            sendToClient(state, "ERROR");
            return;
        }
        nickname = nickname.trim();

        if (nicknameToClient.containsKey(nickname)) {
            sendToClient(state, "ERROR");
            return;
        }

        String oldNickname = state.getNickname();
        
        if (state.isInitial()) {
            state.setNickname(nickname);
            state.setState(ClientState.ConnectionState.READY);
            nicknameToClient.put(nickname, state);
            sendToClient(state, "OK");
        }
        else if (state.isReady()) {
            nicknameToClient.remove(oldNickname);
            state.setNickname(nickname);
            nicknameToClient.put(nickname, state);
            sendToClient(state, "OK");
        }
        else if (state.isInChatRoom()) {
            String roomName = state.getRoom();
            nicknameToClient.remove(oldNickname);
            state.setNickname(nickname);
            nicknameToClient.put(nickname, state);
            
            sendToClient(state, "OK");
            notifyRoom(roomName, "NEWNICK " + oldNickname + " " + nickname, nickname);
        }
    }

    private void commandJoin(ClientState state, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendToClient(state, "ERROR");
            return;
        }
        roomName = roomName.trim();

        if (!state.isReady() && !state.isInChatRoom()) {
            sendToClient(state, "ERROR");
            return;
        }

        String nickname = state.getNickname();
        String previousRoom = state.getRoom();

        if (state.isInChatRoom()) {
            exitFromRoom(state, previousRoom);
        }

        ChatRoom room = activeRooms.computeIfAbsent(roomName, ChatRoom::new);
        room.addParticipant(nickname);
        state.setRoom(roomName);
        state.setState(ClientState.ConnectionState.IN_ROOM);

        sendToClient(state, "OK");
        notifyRoom(roomName, "JOINED " + nickname, nickname);
    }

    private void commandLeave(ClientState state) {
        if (!state.isInChatRoom()) {
            sendToClient(state, "ERROR");
            return;
        }
        String roomName = state.getRoom();
        exitFromRoom(state, roomName);
        sendToClient(state, "OK");
    }

    private void commandBye(ClientState state) {
        String nickname = state.getNickname();
        if (state.isInChatRoom()) {
            String roomName = state.getRoom();
            notifyRoom(roomName, "LEFT " + nickname, nickname);
        }
        sendToClient(state, "BYE");
        
        try {
            if (nickname != null) {
                nicknameToClient.remove(nickname);
            }
            state.getConnection().close();
        } catch (IOException e) {
        }
    }

    private void commandPrivate(ClientState state, String targetNick, String message) {
        ClientState targetClient = nicknameToClient.get(targetNick);
        
        if (targetClient == null) {
            sendToClient(state, "ERROR");
            return;
        }

        String senderNick = state.getNickname();
        sendToClient(targetClient, "PRIVATE " + senderNick + " " + message);
        sendToClient(state, "OK");
    }

    private void sendTextMessage(ClientState state, String message) {
        if (!state.isInChatRoom()) {
            sendToClient(state, "ERROR");
            return;
        }

        if (message.startsWith("//")) {
            message = message.substring(1);
        }

        String nickname = state.getNickname();
        String roomName = state.getRoom();

        notifyRoom(roomName, "MESSAGE " + nickname + " " + message, null);
    }

    private void exitFromRoom(ClientState state, String roomName) {
        String nickname = state.getNickname();
        ChatRoom room = activeRooms.get(roomName);
        if (room != null) {
            room.removeParticipant(nickname);
            notifyRoom(roomName, "LEFT " + nickname, nickname);
            if (room.getParticipantCount() == 0) {
                activeRooms.remove(roomName);
            }
        }
        state.setRoom(null);
        state.setState(ClientState.ConnectionState.READY);
    }

    private void sendToClient(ClientState state, String message) {
        try {
            String messageWithNewline = message + "\n";
            ByteBuffer buffer = ByteBuffer.wrap(messageWithNewline.getBytes(StandardCharsets.UTF_8));
            state.getConnection().write(buffer);
        } catch (IOException e) {
            clientDisconnected(state);
        }
    }

    private void notifyRoom(String roomName, String message, String excludeNickname) {
        ChatRoom room = activeRooms.get(roomName);
        if (room != null) {
            room.forEachParticipant(nickname -> {
                if (!nickname.equals(excludeNickname)) {
                    ClientState client = nicknameToClient.get(nickname);
                    if (client != null) {
                        sendToClient(client, message);
                    }
                }
            });
        }
    }

    private void clientDisconnected(ClientState state) {
        String nickname = state.getNickname();
        if (nickname != null) {
            nicknameToClient.remove(nickname);
            if (state.isInChatRoom()) {
                String roomName = state.getRoom();
                ChatRoom room = activeRooms.get(roomName);
                if (room != null) {
                    room.removeParticipant(nickname);
                    notifyRoom(roomName, "LEFT " + nickname, nickname);
                    if (room.getParticipantCount() == 0) {
                        activeRooms.remove(roomName);
                    }
                }
            }
        }
    }

    public static Map<String, ClientState> getActiveClients() { 
        return nicknameToClient; 
    }
    
    public static Map<String, ChatRoom> getChatRooms() { 
        return activeRooms; 
    }
}
