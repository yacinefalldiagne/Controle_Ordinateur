import java.io.*;
import java.net.*;

public class ServerMain {
    public static void main(String[] args) {
        int port = 5000; // Port du serveur
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur démarré sur le port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

                // on Lance un thread pour gérer la communication avec un client
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Gestion des commandes envoyées par le client
class ClientHandler extends Thread {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    public void run() {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String command;
            while ((command = reader.readLine()) != null) {
                System.out.println("Commande reçue : " + command);

                // ici on Exécute la commande sur le serveur
                String result = executeCommand(command);

                // on Envoye le résultat au client
                writer.println(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command); // Windows
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command); // Linux/macOS
            }

            Process process = processBuilder.start();

            // Lire la sortie standard
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            // c pour Lire la sortie des erreurs
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                output.append("Erreur: ").append(line).append("\n");
            }
            process.waitFor();
        } catch (Exception e) {
            output.append("Erreur lors de l'exécution de la commande : ").append(e.getMessage());
        }
        output.append("__END__\n");
        return output.toString();
    }
}
