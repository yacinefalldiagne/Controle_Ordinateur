import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.*;

public class ServerGUI {
    private JFrame frame;
    private JTextArea logArea;
    private JButton startButton, stopButton, sendFileButton;
    private DefaultListModel<String> clientListModel;
    private JList<String> clientList;
    private DefaultListModel<String> historyModel;
    private JList<String> historyList;
    private ServerSocket serverSocket;
    private boolean running = false;
    private CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Logger logger = Logger.getLogger(ServerGUI.class.getName());

    // ici nous le Bloc statique pour configurer le logger qui enregistre les
    // événements dans un fichier server_log.txt
    static {
        try {
            FileHandler fileHandler = new FileHandler("server_log.txt", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Erreur de configuration du logger : " + e.getMessage());
        }
    }

    // Point d'entrée principal
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                ServerGUI window = new ServerGUI();
                window.frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Constructeur : initialise l'interface graphique en appelant initialize()
    public ServerGUI() {
        initialize();
    }

    // ici on Initialise l'interface graphique du serveur avec une zone de texte,
    // des boutons et des listes
    private void initialize() {
        frame = new JFrame("Serveur - Commandes à distance");
        frame.setBounds(100, 100, 700, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        frame.getContentPane().add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel panel = new JPanel();
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        startButton = new JButton("Démarrer le serveur");
        stopButton = new JButton("Arrêter le serveur");
        stopButton.setEnabled(false);
        sendFileButton = new JButton("Envoyer fichier au client");
        sendFileButton.setEnabled(false);

        panel.add(startButton);
        panel.add(stopButton);
        panel.add(sendFileButton);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        sendFileButton.addActionListener(e -> sendFileToClient());

        clientListModel = new DefaultListModel<>();
        clientList = new JList<>(clientListModel);
        frame.getContentPane().add(new JScrollPane(clientList), BorderLayout.EAST);

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = historyList.getSelectedIndex();
                    if (index != -1) {
                        String command = historyModel.get(index);
                        for (ClientHandler client : clients) {
                            client.sendCommand(command);
                        }
                    }
                }
            }
        });
        frame.getContentPane().add(new JScrollPane(historyList), BorderLayout.WEST);
    }

    // on Démarre le serveur dans un thread séparé, c ici qu on configure le SSL/TLS
    // pour accepter les connexions des clients
    private void startServer() {
        new Thread(() -> {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(new FileInputStream("keystore.jks"), "passer".toCharArray());

                KeyManagerFactory keyManagerFactory = KeyManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, "passer".toCharArray());

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagerFactory.getKeyManagers(), null, new java.security.SecureRandom());
                SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();

                serverSocket = sslServerSocketFactory.createServerSocket(5000);
                running = true;
                log("✅ Serveur démarré sur le port 5000 avec SSL/TLS");
                logger.info("Serveur démarré sur le port 5000");
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                sendFileButton.setEnabled(true);

                while (running) {
                    SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    clientHandler.start();
                    clientListModel.addElement(clientSocket.getInetAddress().toString());
                    logger.info("Nouveau client connecté: " + clientSocket.getInetAddress());
                }
            } catch (Exception e) {
                log("❌ Erreur serveur SSL/TLS : " + e.getMessage());
                logger.severe("Erreur serveur: " + e.getMessage());
            }
        }).start();
    }

    // on Arrête le serveur, on ferme le socket et déconnecte tous les clients
    private void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.disconnect();
            }
            clients.clear();
            clientListModel.clear();
            log("🛑 Serveur arrêté.");
            logger.info("Serveur arrêté par l'utilisateur");
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            sendFileButton.setEnabled(false);
        } catch (IOException e) {
            log("❌ Erreur lors de l'arrêt : " + e.getMessage());
            logger.severe("Erreur arrêt serveur: " + e.getMessage());
        }
    }

    // Ajoute un message à la zone de log de l'interface graphique de manière
    // thread-safe
    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    // Ouvre un sélecteur de fichier et on envoie un fichier au client sélectionné
    // dans la liste
    private void sendFileToClient() {
        int selectedIndex = clientList.getSelectedIndex();
        if (selectedIndex == -1) {
            log("⚠️ Veuillez sélectionner un client dans la liste.");
            return;
        }
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            ClientHandler client = clients.get(selectedIndex);
            client.sendFile(file);
            log("✅ Fichier envoyé au client " + client.socket.getInetAddress() + ": " + file.getName());
            logger.info("Fichier envoyé à " + client.socket.getInetAddress() + ": " + file.getName());
        }
    }

    // Classe interne pour gérer chaque connexion client dans un thread séparé
    class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter writer;
        private BufferedReader reader;

        // Constructeur : initialise le socket du client
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        // Exécute la logique principale du gestionnaire de client : authentification et
        // traitement des entrées
        public void run() {
            try (
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                this.writer = writer;
                this.reader = reader;

                if (!authenticate(reader, writer)) {
                    log("🔴 Échec d'authentification !");
                    logger.warning("Échec d'authentification pour " + socket.getInetAddress());
                    socket.close();
                    return;
                }
                log("✅ Utilisateur authentifié !");
                logger.info("Utilisateur authentifié: " + socket.getInetAddress());

                String input;
                while ((input = reader.readLine()) != null) {
                    if (input.startsWith("FILE:")) {
                        String fileName = input.substring(5);
                        receiveAndForwardFile(fileName);
                    } else {
                        log("📩 Commande reçue : " + input);
                        logger.info("Commande reçue de " + socket.getInetAddress() + ": " + input);
                        historyModel.addElement(input);
                        String result = executeCommand(input);
                        writer.println(result);
                        writer.println("__END__");
                    }
                }
            } catch (IOException e) {
                log("🔴 Client déconnecté : " + socket.getInetAddress());
                logger.info("Client déconnecté: " + socket.getInetAddress());
                clients.remove(this);
                clientListModel.removeElement(socket.getInetAddress().toString());
            }
        }

        // cette methode sendCommand Envoie une commande au client via le flux de sortie
        public void sendCommand(String command) {
            if (writer != null) {
                writer.println(command);
                writer.flush();
                log("📩 Commande renvoyée : " + command);
                logger.info("Commande renvoyée à " + socket.getInetAddress() + ": " + command);
            }
        }

        // Reçoit un fichier du client, le sauvegarde localement et le renvoie au client
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

            // Sauvegarde locale
            try (FileOutputStream fos = new FileOutputStream("received_" + fileName)) {
                fos.write(fileData);
            }
            log("✅ Fichier reçu du client : " + fileName);
            logger.info("Fichier reçu de " + socket.getInetAddress() + ": " + fileName);

            // Renvoyer le fichier au client
            writer.println("FILE:" + fileName);
            OutputStream os = socket.getOutputStream();
            os.write(fileData);
            os.flush();
            writer.println("__END__");
            os.flush();
            log("✅ Fichier renvoyé au client : " + fileName);
        }

        // Envoie un fichier au client via le flux de sortie
        private void sendFile(File file) {
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
            } catch (IOException e) {
                log("❌ Erreur envoi fichier : " + e.getMessage());
                logger.severe("Erreur envoi fichier à " + socket.getInetAddress() + ": " + e.getMessage());
            }
        }

        // Exécute une commande système et retourne le résultat sous forme de chaîne
        private String executeCommand(String command) {
            StringBuilder output = new StringBuilder();
            try {
                ProcessBuilder pb = System.getProperty("os.name").toLowerCase().contains("win")
                        ? new ProcessBuilder("cmd.exe", "/c", command)
                        : new ProcessBuilder("/bin/sh", "-c", command);
                Process process = pb.start();
                BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                while ((line = stdReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errReader.readLine()) != null) {
                    output.append("❌ Erreur: ").append(line).append("\n");
                }
                process.waitFor();
            } catch (Exception e) {
                output.append("❌ Erreur d'exécution : ").append(e.getMessage());
            }
            return output.toString();
        }

        // Authentifie un client en vérifiant les identifiants dans users.txt
        private boolean authenticate(BufferedReader reader, PrintWriter writer) throws IOException {
            writer.println("LOGIN");
            String username = reader.readLine();
            writer.println("PASSWORD");
            String password = reader.readLine();

            try (BufferedReader fileReader = new BufferedReader(new FileReader("users.txt"))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2 && parts[0].equals(username) && parts[1].equals(password)) {
                        writer.println("AUTH_SUCCESS");
                        return true;
                    }
                }
            }
            writer.println("AUTH_FAILED");
            return false;
        }

        // Déconnecte le client
        public void disconnect() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}