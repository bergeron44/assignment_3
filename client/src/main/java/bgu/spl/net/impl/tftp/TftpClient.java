package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TftpClient {
    private static final int SERVER_PORT = 69; // Default TFTP port
    private static final int BUFFER_SIZE = 516; // 512 bytes data + 4 bytes header

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: TftpClient <server> <command> [<filename>]");
            System.exit(1);
        }

        String server = args[0];
        String command = args[1];
        String filename = args.length > 2 ? args[2] : null;

        try (Socket socket = new Socket(server, SERVER_PORT);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            switch (command.toLowerCase()) {
                case "read":
                    if (filename == null) {
                        System.out.println("Filename required for read command");
                        break;
                    }
                    sendRRQ(out, filename);
                    receiveFile(out,in, filename);
                    break;
                case "write":
                    if (filename == null) {
                        System.out.println("Filename required for write command");
                        break;
                    }
                    sendWRQ(out, filename);
                    sendFile(out, in, filename);
                    break;
                case "login":
                    if (filename == null) {
                        System.out.println("Username required for login command");
                        break;
                    }
                    sendLOGRQ(out, filename);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendRRQ(OutputStream out, String filename) throws IOException {
        byte[] request = createRequest(1, filename);
        out.write(request);
        out.flush();
    }

    private static void sendWRQ(OutputStream out, String filename) throws IOException {
        byte[] request = createRequest(2, filename);
        out.write(request);
        out.flush();
    }

    private static void sendLOGRQ(OutputStream out, String username) throws IOException {
        byte[] request = createRequest(7, username);
        out.write(request);
        out.flush();
    }

    private static byte[] createRequest(int opcode, String filename) {
        byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);
        byte[] request = new byte[2 + filenameBytes.length + 1];
        request[0] = 0;
        request[1] = (byte) opcode;
        System.arraycopy(filenameBytes, 0, request, 2, filenameBytes.length);
        request[request.length - 1] = 0;
        return request;
    }

    private static void receiveFile(OutputStream outputStream,InputStream in, String filename) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 4, bytesRead - 4); // Skip the header
                sendAck(outputStream); 
            }
           
        }
    }

    private static void sendFile(OutputStream out, InputStream in, String filename) throws IOException {
        try (FileInputStream fis = new FileInputStream(filename)) {
            byte[] buffer = new byte[BUFFER_SIZE - 4];
            int bytesRead;
            int blockNumber = 1;
            while ((bytesRead = fis.read(buffer)) != -1) {
                sendData(out, buffer, bytesRead, blockNumber++);
                receiveAck(in);
            }
            sendData(out, new byte[0], 0, blockNumber); // Send last packet
        }
    }

    private static void sendData(OutputStream out, byte[] data, int length, int blockNumber) throws IOException {
        byte[] packet = new byte[4 + length];
        packet[0] = 0;
        packet[1] = 3;
        packet[2] = (byte) (blockNumber >> 8);
        packet[3] = (byte) blockNumber;
        System.arraycopy(data, 0, packet, 4, length);
        out.write(packet);
        out.flush();
    }

    private static void sendAck(OutputStream out) throws IOException {
        byte[] ack = new byte[]{0, 4, 0, 0};
        out.write(ack);
        out.flush();
    }

    private static void receiveAck(InputStream in) throws IOException {
        byte[] ack = new byte[4];
        int bytesRead = in.read(ack);
        if (bytesRead != 4 || ack[1] != 4) {
            throw new IOException("Invalid ACK packet");
        }
    }
}
