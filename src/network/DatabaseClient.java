package network;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * A simple command-line interface (CLI) client to interact with the database server over TCP.
 */
public class DatabaseClient {

    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 9000);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println(in.readLine());

            while (true) {
                System.out.print("> ");
                String command = scanner.nextLine();
                out.println(command);

                if (command.equalsIgnoreCase("EXIT") || command.equalsIgnoreCase("QUIT")) {
                    System.out.println(in.readLine());
                    break;
                }

                System.out.println(in.readLine());
            }
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }
}