import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ClienteChat {

    JFrame frame = new JFrame("Cliente de Chat");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean running = true;

    public void printMessage(final String message) {
        chatArea.append(message);
    }

    public ClienteChat(String server, int port) throws IOException {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });

        try {
            socket = new Socket(server, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            printMessage("=== Conectado ao servidor " + server + ":" + port + " ===\n");
            printMessage("Comandos: /nick, /join, /leave, /bye, /priv\n");
        } catch (IOException e) {
            printMessage("ERRO: Não foi possível conectar a " + server + ":" + port + "\n");
            throw e;
        }
    }

    public void newMessage(String message) throws IOException {
        if (message == null || message.trim().isEmpty()) return;

        String messageToSend = message;
        if (message.startsWith("/")) {
            String[] parts = message.split("\\s+");
            String command = parts[0];
            if (!isValidCommand(command)) {
                messageToSend = "/" + message;
            }
        }

        out.println(messageToSend);

        if (message.equals("/bye")) {
            running = false;
        }
    }

    public void run() throws IOException {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                String formatted = formatServerMessage(line);
                printMessage(formatted + "\n");
                if (line.equals("BYE")) {
                    running = false;
                }
            }
        } catch (IOException e) {
            if (running) printMessage("\n*** Conexão perdida ***\n");
        } finally {
            if (socket != null && !socket.isClosed()) socket.close();
            frame.dispose();
            System.exit(0);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Uso: java ClienteChat <host> <porta>");
            System.exit(1);
        }
        
        ClienteChat client = new ClienteChat(args[0], Integer.parseInt(args[1]));
        client.run();
    }

    private boolean isValidCommand(String cmd) {
        return cmd.equals("/nick") || cmd.equals("/join") || 
               cmd.equals("/leave") || cmd.equals("/bye") || 
               cmd.equals("/priv");
    }

    private String formatServerMessage(String serverMessage) {
        if (serverMessage.startsWith("MESSAGE ")) {
            String[] parts = serverMessage.split(" ", 3);
            if (parts.length >= 3) return parts[1] + ": " + parts[2];
        } else if (serverMessage.startsWith("NEWNICK ")) {
            String[] parts = serverMessage.split(" ");
            if (parts.length >= 3) return "*** " + parts[1] + " mudou de nome para " + parts[2] + " ***";
        } else if (serverMessage.startsWith("JOINED ")) {
            String[] parts = serverMessage.split(" ");
            if (parts.length >= 2) return "*** " + parts[1] + " entrou na sala ***";
        } else if (serverMessage.startsWith("LEFT ")) {
            String[] parts = serverMessage.split(" ");
            if (parts.length >= 2) return "*** " + parts[1] + " saiu da sala ***";
        } else if (serverMessage.startsWith("PRIVATE ")) {
            String[] parts = serverMessage.split(" ", 3);
            if (parts.length >= 3) return "[PRIVADO] " + parts[1] + ": " + parts[2];
        } else if (serverMessage.equals("OK")) {
            return "✓ Sucesso";
        } else if (serverMessage.equals("ERROR")) {
            return "✗ Erro";
        }
        return serverMessage;
    }
}
