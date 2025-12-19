import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ServidorChat {
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private static final Map<String, EstadoCliente> activeClientsByNick = new ConcurrentHashMap<>();
    private static final Map<String, SalaChat> chatRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java ServidorChat <porta_tcp>");
            System.exit(1);
        }

        int port = 0;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Erro: A porta deve ser um número inteiro.");
            System.exit(1);
        }

        new ServidorChat().startServer(port);
    }

    public void startServer(int port) {
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            System.out.println("Servidor de Chat multiplex em execução na porta " + port);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (key.isAcceptable()) {
                        acceptConnection();
                    } else if (key.isReadable()) {
                        readFromClient(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        
        EstadoCliente clientState = new EstadoCliente(clientChannel);
        clientChannel.keyFor(selector).attach(clientState);
        System.out.println("Novo cliente conectado: " + clientChannel.getRemoteAddress());
    }

    private void readFromClient(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        EstadoCliente clientState = (EstadoCliente) key.attachment();
        ByteBuffer buffer = clientState.getReadBuffer();
        
        int bytesRead = channel.read(buffer);
        if (bytesRead == -1) {
            handleClientDisconnect(clientState);
            channel.close();
            return;
        }
        
        if (bytesRead > 0) {
            processClientData(clientState);
        }
    }

    private void processClientData(EstadoCliente clientState) {
        ByteBuffer buffer = clientState.getReadBuffer();
        StringBuilder lineBuffer = clientState.getLineBuffer();
        
        buffer.flip();
        
        if (buffer.hasRemaining()) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            String str = new String(bytes, StandardCharsets.UTF_8);
            
            lineBuffer.append(str);
            extractLines(clientState);
        }
        
        buffer.clear();
    }

    private void extractLines(EstadoCliente clientState) {
        StringBuilder lineBuffer = clientState.getLineBuffer();
        String text = lineBuffer.toString();
        
        int start = 0;
        boolean foundLine = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                String line = text.substring(start, i).trim();
                
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
                foundLine = true;
                
                if (!line.isEmpty()) {
                    handleCommand(clientState, line);
                }
            }
        }
        
        if (foundLine) {
            if (start < text.length()) {
                clientState.setLineBuffer(new StringBuilder(text.substring(start)));
            } else {
                clientState.clearLineBuffer();
            }
        }
    }

    private void handleCommand(EstadoCliente clientState, String line) {
        if (line.startsWith("/")) {
            executeCommand(clientState, line);
        } else {
            sendMessage(clientState, line);
        }
    }

    private void executeCommand(EstadoCliente clientState, String line) {
        if (line.startsWith("//")) {
            sendMessage(clientState, line);
            return;
        }

        String[] tokens = line.split("\\s+", 2);
        String cmd = tokens[0];
        String arg = tokens.length > 1 ? tokens[1] : null;

        if (!isValidCommand(cmd)) {
            sendMessage(clientState, line);
            return;
        }

        switch (cmd) {
            case "/nick":
                cmdNick(clientState, arg);
                break;
            case "/join":
                cmdJoin(clientState, arg);
                break;
            case "/leave":
                cmdLeave(clientState);
                break;
            case "/bye":
                cmdBye(clientState);
                break;
            case "/priv":
                if (arg != null) {
                    String[] parts = arg.split("\\s+", 2);
                    if (parts.length == 2) {
                        cmdPrivate(clientState, parts[0], parts[1]);
                    } else {
                        sendToClient(clientState, "ERROR");
                    }
                } else {
                    sendToClient(clientState, "ERROR");
                }
                break;
            default:
                sendToClient(clientState, "ERROR");
        }
    }

    private boolean isValidCommand(String cmd) {
        return cmd.equals("/nick") || cmd.equals("/join") || 
               cmd.equals("/leave") || cmd.equals("/bye") || 
               cmd.equals("/priv");
    }

    private void cmdNick(EstadoCliente clientState, String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            sendToClient(clientState, "ERROR");
            return;
        }
        nick = nick.trim();

        if (activeClientsByNick.containsKey(nick)) {
            sendToClient(clientState, "ERROR");
            return;
        }

        String oldNick = clientState.getNickname();
        
        if (clientState.isInitialState()) {
            clientState.setNickname(nick);
            clientState.transitionToReady();
            activeClientsByNick.put(nick, clientState);
            sendToClient(clientState, "OK");
        }
        else if (clientState.isReadyState()) {
            activeClientsByNick.remove(oldNick);
            clientState.setNickname(nick);
            activeClientsByNick.put(nick, clientState);
            sendToClient(clientState, "OK");
        }
        else if (clientState.isInRoomState()) {
            String roomName = clientState.getCurrentRoom();
            activeClientsByNick.remove(oldNick);
            clientState.setNickname(nick);
            activeClientsByNick.put(nick, clientState);
            
            sendToClient(clientState, "OK");
            broadcastToRoom(roomName, "NEWNICK " + oldNick + " " + nick, nick);
        }
    }

    private void cmdJoin(EstadoCliente clientState, String roomName) {
        if (roomName == null || roomName.trim().isEmpty()) {
            sendToClient(clientState, "ERROR");
            return;
        }
        roomName = roomName.trim();

        if (!clientState.isReadyState() && !clientState.isInRoomState()) {
            sendToClient(clientState, "ERROR");
            return;
        }

        String nick = clientState.getNickname();
        String prevRoom = clientState.getCurrentRoom();

        if (clientState.isInRoomState()) {
            leaveRoom(clientState, prevRoom);
        }

        SalaChat room = chatRooms.computeIfAbsent(roomName, SalaChat::new);
        room.addMember(nick);
        clientState.setCurrentRoom(roomName);
        clientState.transitionToInRoom();

        sendToClient(clientState, "OK");
        broadcastToRoom(roomName, "JOINED " + nick, nick);
    }

    private void cmdLeave(EstadoCliente clientState) {
        if (!clientState.isInRoomState()) {
            sendToClient(clientState, "ERROR");
            return;
        }
        String roomName = clientState.getCurrentRoom();
        leaveRoom(clientState, roomName);
        sendToClient(clientState, "OK");
    }

    private void cmdBye(EstadoCliente clientState) {
        String nick = clientState.getNickname();
        if (clientState.isInRoomState()) {
            String roomName = clientState.getCurrentRoom();
            broadcastToRoom(roomName, "LEFT " + nick, nick);
        }
        sendToClient(clientState, "BYE");
        
        try {
            if (nick != null) {
                activeClientsByNick.remove(nick);
            }
            clientState.getChannel().close();
        } catch (IOException e) {
        }
    }

    private void cmdPrivate(EstadoCliente clientState, String targetNick, String message) {
        EstadoCliente targetClient = activeClientsByNick.get(targetNick);
        
        if (targetClient == null) {
            sendToClient(clientState, "ERROR");
            return;
        }

        String senderNick = clientState.getNickname();
        sendToClient(targetClient, "PRIVATE " + senderNick + " " + message);
        sendToClient(clientState, "OK");
    }

    private void sendMessage(EstadoCliente clientState, String message) {
        if (!clientState.isInRoomState()) {
            sendToClient(clientState, "ERROR");
            return;
        }

        if (message.startsWith("//")) {
            message = message.substring(1);
        }

        String nick = clientState.getNickname();
        String roomName = clientState.getCurrentRoom();

        broadcastToRoom(roomName, "MESSAGE " + nick + " " + message, null);
    }

    private void leaveRoom(EstadoCliente clientState, String roomName) {
        String nick = clientState.getNickname();
        SalaChat room = chatRooms.get(roomName);
        if (room != null) {
            room.removeMember(nick);
            broadcastToRoom(roomName, "LEFT " + nick, nick);
            if (room.getMemberCount() == 0) {
                chatRooms.remove(roomName);
            }
        }
        clientState.setCurrentRoom(null);
        clientState.transitionToReady();
    }

    private void sendToClient(EstadoCliente clientState, String message) {
        try {
            String msgWithNewline = message + "\n";
            ByteBuffer buffer = ByteBuffer.wrap(msgWithNewline.getBytes(StandardCharsets.UTF_8));
            clientState.getChannel().write(buffer);
        } catch (IOException e) {
            handleClientDisconnect(clientState);
        }
    }

    private void broadcastToRoom(String roomName, String message, String excludeNick) {
        SalaChat room = chatRooms.get(roomName);
        if (room != null) {
            room.forEachMember(nick -> {
                if (!nick.equals(excludeNick)) {
                    EstadoCliente client = activeClientsByNick.get(nick);
                    if (client != null) {
                        sendToClient(client, message);
                    }
                }
            });
        }
    }

    private void handleClientDisconnect(EstadoCliente clientState) {
        String nick = clientState.getNickname();
        if (nick != null) {
            activeClientsByNick.remove(nick);
            if (clientState.isInRoomState()) {
                String roomName = clientState.getCurrentRoom();
                SalaChat room = chatRooms.get(roomName);
                if (room != null) {
                    room.removeMember(nick);
                    broadcastToRoom(roomName, "LEFT " + nick, nick);
                    if (room.getMemberCount() == 0) {
                        chatRooms.remove(roomName);
                    }
                }
            }
        }
    }

    public static Map<String, EstadoCliente> getActiveClients() { 
        return activeClientsByNick; 
    }
    
    public static Map<String, SalaChat> getChatRooms() { 
        return chatRooms; 
    }
}
