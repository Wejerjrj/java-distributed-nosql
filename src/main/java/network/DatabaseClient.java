package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * A command-line client for the distributed NoSQL database.
 * Establishes a TCP connection and provides an interactive REPL (Read-Eval-Print Loop)
 * to send commands and receive responses using a custom length-prefixed binary protocol.
 */
public class DatabaseClient {

    /**
     * The main entry point for the database client application.
     * Continuously prompts the user for input, formats the command,
     * transmits it over the socket, and prints the server's response.
     *
     * @param args Command line arguments (unused).
     */
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 9000);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println(in.readUTF());

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                String[] parts;
                if (input.toUpperCase().startsWith("SET ") || input.toUpperCase().startsWith("SET_EX ")) {
                    int limit = input.toUpperCase().startsWith("SET_EX") ? 4 : 3;
                    parts = input.split("\\s+", limit);
                } else {
                    parts = input.split("\\s+");
                }

                out.writeInt(parts.length);
                for (String part : parts) {
                    out.writeUTF(part);
                }
                out.flush();

                if (parts[0].equalsIgnoreCase("EXIT") || parts[0].equalsIgnoreCase("QUIT")) {
                    System.out.println(in.readUTF());
                    break;
                }

                System.out.println(in.readUTF());
            }
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}