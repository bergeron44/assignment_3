package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.LinkedTransferQueue;

import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    String filesPath = System.getProperty("user.dir") + "/" + "Files";
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean shouldTerminate = false;
    private LinkedTransferQueue<byte[]> data = new LinkedTransferQueue<>();
    private String fileName = "";
    private String connectionName;
    private String state = "INIT";
    private boolean login = false;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.state = "WAITING";
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
                sendError(connectionId, 4, "Illegal TFTP operation");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // Handler implementations

    //login
    private void handleLogrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String username = TftpUtils.extractString(message, 2);
        if (((ConnectionsImpl) connections).isExist(username)) {
            sendError(connectionId, 7, "User already logged in");
        } else {
            ((ConnectionsImpl) connections).login(username, connectionId);
            login = true;
            connectionName = username;
            sendAck(connectionId, 0, connections);
        }
    }

    private void handleDelrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
    }

    private void handleRrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
    }

    private void handleWrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (fileExists(filename)) {
            sendError(connectionId, 5, "File already exists");
        } else {
            fileName = filename;
            state = "DATA";
            sendAck(connectionId, (short) 0, connections);

        }
    }

    private void handleDirq(int connectionId, Connections<byte[]> connections) {
    }

    private void handleData(byte[] message, int connectionId, Connections<byte[]> connections) {
    }

    private void handleAck(byte[] message, int connectionId, Connections<byte[]> connections) {
        int blockNumber = TftpUtils.extractShort(message, 2);
        // other cases... TODO
    }

    private void handleBcast(byte[] message, int connectionId, Connections<byte[]> connections) {
        // This handler is usually server-initiated, so implementation might vary based
        // on your server logic
    }

    private void handleError(byte[] message, int connectionId, Connections<byte[]> connections) {
        int errorCode = TftpUtils.extractShort(message, 2);
        String errorMessage = TftpUtils.extractString(message, 4);
        System.err.println("Error received from client " + connectionId + ": " + errorCode + " - " + errorMessage);
    }

    //logout
    private void handleDisc(int connectionId, Connections<byte[]> connections) {
        if (!login) {
            sendError(connectionId, 0, "User isn't logged in");
            return;
        }
        ((ConnectionsImpl) connections).logout(connectionName);
        sendAck(connectionId, 0, connections);
        connections.disconnect(connectionId);
        shouldTerminate = true;
    }

    // Utility methods

    private void sendAck(int connectionId, int blockNumber, Connections<byte[]> connections) {
        byte[] ackPacket = TftpUtils.createAckPacket((short) blockNumber);
        connections.send(connectionId, ackPacket);
    }

    private void sendError(int connectionId, int errorCode, String errorMessage) {
        byte[] errorPacket = TftpUtils.createErrorPacket(errorCode, errorMessage);
        connections.send(connectionId, errorPacket);
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

    public static short extractShort(byte[] message, int startIndex) {
        int num = ((message[startIndex] & 0xff) << 8) | (message[startIndex + 1] & 0xff);
        return (short) num;
    }

    public static byte[] createAckPacket(short blockNumber) {
        return new byte[] { (byte) ((short) 4 >> 8), (byte) ((short) 4), (byte) (blockNumber >> 8),
                (byte) (blockNumber) };
    }
}
