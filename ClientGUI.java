import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class ClientGUI {
    private JFrame frame;
    private JTextField commandField;
    private JTextArea resultArea;
    private JButton sendButton, connectButton, sendFileToServerButton;
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private boolean isConnected = false;

    public static void main(String[] args) {
        EventQueue.invokeLater(ClientGUI::new);
    }

    // Constructeur : appelle initialize() pour créer l'interface
    public ClientGUI() {
        initialize();
    }

    // Initialise l'interface avec une zone de texte, un champ et des boutons
    private void initialize() {
        frame = new JFrame("Client - Contrôle à distance");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        resultArea = new JTextArea();
        resultArea.setEditable(false);
        frame.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        JPanel panel = new JPanel(new FlowLayout());
        frame.add(panel, BorderLayout.SOUTH);

        commandField = new JTextField(20);
        panel.add(commandField);

        sendButton = new JButton("Envoyer Commande");
        sendButton.addActionListener(this::sendCommand);
        sendButton.setEnabled(false);
        panel.add(sendButton);

        connectButton = new JButton("Connexion");
        connectButton.addActionListener(this::toggleConnection);
        panel.add(connectButton);

        sendFileToServerButton = new JButton("Envoyer fichier au serveur");
        sendFileToServerButton.addActionListener(e -> sendFileToServerDialog());
        panel.add(sendFileToServerButton);

        frame.setVisible(true);
    }

    // Ouvre un sélecteur de fichier pour envoyer un fichier au serveur
    private void sendFileToServerDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner un fichier à envoyer au serveur");
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            sendFileToServer(file);
        }
    }

    // Gère la connexion/déconnexion selon l'état actuel
    private void toggleConnection(ActionEvent e) {
        if (isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    // Établit une connexion SSL avec le serveur et authentifie
    private void connect() {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try {
                keyStore.load(new FileInputStream("keystore.jks"), "passer".toCharArray());
            } catch (CertificateException | IOException e) {
                resultArea.append("❌ Erreur de chargement du keystore : " + e.getMessage() + "\n");
                return;
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new java.security.SecureRandom());

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            socket = sslSocketFactory.createSocket("127.0.0.1", 5000);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            if (!authenticate()) {
                resultArea.append("❌ Connexion refusée !\n");
                disconnect();
                return;
            }

            isConnected = true;
            sendButton.setEnabled(true);
            connectButton.setText("Déconnexion");
            resultArea.append("✅ Connecté au serveur avec SSL/TLS !\n");

            new Thread(this::listenForServerMessages).start();
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            resultArea.append("❌ Erreur de connexion SSL/TLS : " + e.getMessage() + "\n");
        }
    }

    // Authentifie l'utilisateur avec login/mot de passe
    private boolean authenticate() throws IOException {
        String loginPrompt = reader.readLine();
        if (loginPrompt == null || !loginPrompt.equals("LOGIN"))
            return false;
        String username = JOptionPane.showInputDialog(frame, "Nom d'utilisateur : ");
        if (username == null)
            return false;
        writer.println(username);

        String passwordPrompt = reader.readLine();
        if (passwordPrompt == null || !passwordPrompt.equals("PASSWORD"))
            return false;
        String password = JOptionPane.showInputDialog(frame, "Mot de passe : ");
        if (password == null)
            return false;
        writer.println(password);

        String authResponse = reader.readLine();
        return authResponse != null && authResponse.equals("AUTH_SUCCESS");
    }

    // Ferme la connexion au serveur
    private void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            isConnected = false;
            sendButton.setEnabled(false);
            connectButton.setText("Connexion");
            resultArea.append("🔌 Déconnecté du serveur.\n");
        } catch (IOException ex) {
            resultArea.append("❌ Erreur lors de la déconnexion : " + ex.getMessage() + "\n");
        } finally {
            socket = null;
            writer = null;
            reader = null;
        }
    }

    // Envoie une commande au serveur
    private void sendCommand(ActionEvent e) {
        if (!isConnected) {
            resultArea.append("⚠️ Vous devez être connecté !\n");
            return;
        }
        String command = commandField.getText().trim();
        if (!command.isEmpty()) {
            writer.println(command);
            commandField.setText("");
            new Thread(() -> receiveResponse(command)).start();
        }
    }

    // Reçoit la réponse du serveur pour une commande
    private void receiveResponse(String command) {
        try {
            String response;
            StringBuilder result = new StringBuilder();
            while ((response = reader.readLine()) != null) {
                if (response.equals("__END__"))
                    break;
                result.append(response).append("\n");
            }
            SwingUtilities.invokeLater(() -> {
                resultArea.append("\n> " + command + "\n" + result.toString());
                resultArea.append("--------------------------------------------------\n");
            });
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> resultArea.append("❌ Erreur de lecture : " + ex.getMessage() + "\n"));
        }
    }

    // c est pour Écouter les messages du serveur en continu
    private void listenForServerMessages() {
        try {
            while (isConnected) {
                String message = reader.readLine();
                if (message == null)
                    break;
                if (message.startsWith("FILE:")) {
                    String fileName = message.substring(5);
                    receiveAndForwardFile(fileName);
                } else if (!message.equals("__END__")) {
                    SwingUtilities.invokeLater(() -> resultArea.append(message + "\n"));
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> resultArea.append("❌ Erreur réception : " + e.getMessage() + "\n"));
            }
        }
    }

    // Reçoit un fichier, le sauvegarde et le renvoie au serveur
    private void receiveAndForwardFile(String fileName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        InputStream is = socket.getInputStream();
        while ((bytesRead = is.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            if (reader.ready() && "__END__".equals(reader.readLine()))
                break;
        }
        byte[] fileData = baos.toByteArray();

        try (FileOutputStream fos = new FileOutputStream("client_received_" + fileName)) {
            fos.write(fileData);
        }
        SwingUtilities.invokeLater(() -> resultArea.append("✅ Fichier reçu : " + fileName + "\n"));

        writer.println("FILE:" + fileName);
        OutputStream os = socket.getOutputStream();
        os.write(fileData);
        os.flush();
        writer.println("__END__");
        os.flush();
        SwingUtilities.invokeLater(() -> resultArea.append("✅ Fichier renvoyé au serveur : " + fileName + "\n"));
    }

    // Envoie un fichier au serveur
    private void sendFileToServer(File file) {
        try {
            writer.println("FILE:" + file.getName());
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            OutputStream os = socket.getOutputStream();
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            fis.close();
            os.flush();
            writer.println("__END__");
            os.flush();
            resultArea.append("✅ Fichier envoyé au serveur : " + file.getName() + "\n");
        } catch (IOException e) {
            resultArea.append("❌ Erreur envoi fichier au serveur : " + e.getMessage() + "\n");
        }
    }
}