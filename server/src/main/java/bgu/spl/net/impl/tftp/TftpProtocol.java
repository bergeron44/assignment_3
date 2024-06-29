package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean shouldTerminate = false;

    private Map<Integer, String> loggedInUsers = new HashMap<>();
    private Map<Integer, String> fileTransfers = new HashMap<>();
    

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {
        int opcode = TftpUtils.extractShort(message, 0);
        switch (opcode) {
            case 1:
                handleRrq(message, connectionId, connections);
                break;
            case 2:
                handleWrq(message, connectionId, connections);
                break;
            case 3:
                handleData(message, connectionId, connections);
                break;
            case 4:
                handleAck(message, connectionId, connections);
                break;
            case 5:
                handleError(message, connectionId, connections);
                break;
            case 6:
                handleDirq(connectionId, connections);
                break;
            case 7:
                handleLogrq(message, connectionId, connections);
                break;
            case 8:
                handleDelrq(message, connectionId, connections);
                break;
            case 9:
                handleBcast(message, connectionId, connections);
                break;
            case 10:
                handleDisc(connectionId, connections);
                break;
            default:
                sendError(connectionId, 4, "Illegal TFTP operation", connections);
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // Handler implementations

    private void handleLogrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String username = TftpUtils.extractString(message, 2);
        if (isUserLoggedIn(username)) {
            sendError(connectionId, 7, "User already logged in", connections);
        } else {
            loginUser(username, connectionId);
            sendAck(connectionId, 0, connections);
        }
    }

    private void handleDelrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (!fileExists(filename)) {
            sendError(connectionId, 1, "File not found", connections);
        } else {
            deleteFile(filename);
            sendAck(connectionId, 0, connections);
            broadcastFileDeletion(filename, connections);
        }
    }

    private void handleRrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (!fileExists(filename)) {
            sendError(connectionId, 1, "File not found", connections);
        } else {
            sendFileToClient(filename, connectionId, connections);
        }
    }

    private void handleWrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (fileExists(filename)) {
            sendError(connectionId, 5, "File already exists", connections);
        } else {
            prepareToReceiveFile(filename, connectionId);
            sendAck(connectionId, 0, connections);
        }
    }

    private void handleDirq(int connectionId, Connections<byte[]> connections) {
        String directoryListing = getDirectoryListing();
        sendDirectoryListing(directoryListing, connectionId, connections);
    }

    private void handleData(byte[] message, int connectionId, Connections<byte[]> connections) {
        int blockNumber = TftpUtils.extractShort(message, 2);
        byte[] data = Arrays.copyOfRange(message, 4, message.length);
        writeDataToFile(connectionId, blockNumber, data);
        sendAck(connectionId, blockNumber, connections);
    }

    private void handleAck(byte[] message, int connectionId, Connections<byte[]> connections) {
        int blockNumber = TftpUtils.extractShort(message, 2);
        proceedWithFileTransfer(connectionId, blockNumber, connections);
    }

    private void handleBcast(byte[] message, int connectionId, Connections<byte[]> connections) {
        // This handler is usually server-initiated, so implementation might vary based on your server logic
        broadcastFileChange(message);
    }

    private void handleError(byte[] message, int connectionId, Connections<byte[]> connections) {
        int errorCode = TftpUtils.extractShort(message, 2);
        String errorMessage = TftpUtils.extractString(message, 4);
        System.err.println("Error received from client " + connectionId + ": " + errorCode + " - " + errorMessage);
    }

    private void handleDisc(int connectionId, Connections<byte[]> connections) {
        disconnectUser(connectionId);
        sendAck(connectionId, 0, connections);
        shouldTerminate = true;
    }

    // Utility methods

    private boolean isUserLoggedIn(String username) {
        return loggedInUsers.containsValue(username);
    }

    private void loginUser(String username, int connectionId) {
        loggedInUsers.put(connectionId, username);
    }

    private void sendAck(int connectionId, int blockNumber, Connections<byte[]> connections) {
        byte[] ackPacket = TftpUtils.createAckPacket(blockNumber);
        connections.send(connectionId, ackPacket);
    }

    private void sendError(int connectionId, int errorCode, String errorMessage, Connections<byte[]> connections) {
        byte[] errorPacket = TftpUtils.createErrorPacket(errorCode, errorMessage);
        connections.send(connectionId, errorPacket);
    }

    private boolean fileExists(String filename) {
        // Implement file existence check
        return false;
    }

    private void deleteFile(String filename) {
        // Implement file deletion
    }

    private void broadcastFileDeletion(String filename, Connections<byte[]> connections) {
        // Implement file deletion broadcast
    }

    private void sendFileToClient(String filename, int connectionId, Connections<byte[]> connections) {
        // Implement file sending
    }

    private void prepareToReceiveFile(String filename, int connectionId) {
        fileTransfers.put(connectionId, filename);
    }

    private String getDirectoryListing() {
        // Implement directory listing retrieval
        return "";
    }

    private void sendDirectoryListing(String directoryListing, int connectionId, Connections<byte[]> connections) {
        // Implement directory listing sending
    }

    private void writeDataToFile(int connectionId, int blockNumber, byte[] data) {
        // Implement data writing to file
    }

    private void proceedWithFileTransfer(int connectionId, int blockNumber, Connections<byte[]> connections) {
        // Implement file transfer continuation
    }

    private void disconnectUser(int connectionId) {
        loggedInUsers.remove(connectionId);
    }

    private void broadcastFileChange(byte[] message) {
        // Implement file change broadcast
    }
}

// Utility class for TFTP operations
class TftpUtils {

    public static String extractString(byte[] message, int startIndex) {
        int endIndex = startIndex;
        while (endIndex < message.length && message[endIndex] != 0) {
            endIndex++;
        }
        return new String(message, startIndex, endIndex - startIndex, StandardCharsets.UTF_8);
    }

    public static int extractShort(byte[] message, int startIndex) {
        return ((message[startIndex] & 0xff) << 8) | (message[startIndex + 1] & 0xff);
    }

    public static byte[] createAckPacket(int blockNumber) {
        byte[] packet = new byte[4];
        packet[0] = 0;
        packet[1] = 4;
        packet[2] = (byte) (blockNumber >> 8);
        packet[3] = (byte) blockNumber;
        return packet;
    }

    public static byte[] createErrorPacket(int errorCode, String errorMessage) {
        byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[4 + errorMessageBytes.length + 1];
        packet[0] = 0;
        packet[1] = 5;
        packet[2] = (byte) (errorCode >> 8);
        packet[3] = (byte) errorCode;
        System.arraycopy(errorMessageBytes, 0, packet, 4, errorMessageBytes.length);
        packet[4 + errorMessageBytes.length] = 0;
        return packet;
    }
}
