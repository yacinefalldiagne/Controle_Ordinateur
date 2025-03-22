import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        String serverIP = "127.0.0.1"; // Adresse du serveur
        int port = 5000; // Même port que le serveur

        try (Socket socket = new Socket(serverIP, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Scanner scanner = new Scanner(System.in)) {
            System.out.println("Connecté au serveur.");
            while (true) {
                System.out.print("Commande à exécuter (ou 'exit' pour quitter) : ");
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("exit")) {
                    System.out.println("Déconnexion...");
                    break; // Quitte la boucle si l'utilisateur tape 'exit'
                }
                writer.println(command); // Envoie la commande au serveur
                // Lire toute la réponse jusqu'à __END__
                String response;
                System.out.println("Résultat :");
                while ((response = reader.readLine()) != null) {
                    if (response.equals("__END__")) {
                        break; // Fin de la réponse
                    }
                    System.out.println(response);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur de connexion au serveur : " + e.getMessage());
        }
    }
}
