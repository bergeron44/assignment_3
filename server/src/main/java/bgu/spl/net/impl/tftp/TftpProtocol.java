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
    private int connectionId;
    private Connections<byte[]> connections;
    private boolean shouldTerminate = false;
    private LinkedTransferQueue<byte[]> data = new LinkedTransferQueue<>();
    private String fileName = "";
    private String state = "INIT";
    private boolean login=false;
    private static final int BASE_SERVER_CONNECTION_ID = 1;

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
                if (state == "BASEDATA")
                    handleBaseData(message, connectionId, connections);
                else if (state == "DATA")
                    handleData(message, connectionId, connections);
                else
                    sendError(connectionId, opcode, fileName, connections);
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
        if (((ConnectionsImpl) connections).isExist(username)) {
            sendError(connectionId, 7, "User already logged in", connections);
        } else {
            ((ConnectionsImpl) connections).login(username,connectionId);
            login=true;
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
            sendAck(connectionId, (short) 0, connections);
            state = "BaseACK";
            connections.send(BASE_SERVER_CONNECTION_ID, message);
        }
    }

    private void handleWrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (fileExists(filename)) {
            sendError(connectionId, 5, "File already exists", connections);
        } else {
            fileName = filename;
            state = "DATA";
            sendAck(connectionId, (short) 0, connections);
        }
    }

    private void handleDirq(int connectionId, Connections<byte[]> connections) {
        String directoryListing = getDirectoryListing();
        sendDirectoryListing(directoryListing, connectionId, connections);
    }

    private void handleData(byte[] message, int connectionId, Connections<byte[]> connections) {
            short blockNumber = TftpUtils.extractShort(message, 4);
            if (blockNumber != data.size()+1)
                sendError(connectionId, connectionId, fileName, connections);
            else {
                data.put(message);
                sendAck(connectionId, 0, connections);
                if(dataPacketSize==0){
                    state="BASETRANSFER";
                    proceedWithFileTransfer(connectionId, data.size(), connections);
                }
            }
        
    }

    private void handleAck(byte[] message, int connectionId, Connections<byte[]> connections) {
        int blockNumber = TftpUtils.extractShort(message, 2);
        if (state == "DATAACK") {
            state = "DATA";
            proceedWithFileTransfer(BASE_SERVER_CONNECTION_ID, blockNumber, connections);
        } else if (state == "BASEDATAACK") {
            state = "BASEDATA";
            proceedWithFileTransfer(connectionId, blockNumber, connections);
        }
        // other cases... TODO
    }

    private void handleBcast(byte[] message, int connectionId, Connections<byte[]> connections) {
        // This handler is usually server-initiated, so implementation might vary based
        // on your server logic
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

    private void sendAck(int connectionId, int blockNumber, Connections<byte[]> connections) {
        byte[] ackPacket = TftpUtils.createAckPacket((short) blockNumber);
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
        dataPacketSize = blockNumber;
        data.clear();
        sendAck(connectionId, 0, connections);
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

    public static short extractShort(byte[] message, int startIndex) {
        int num = ((message[startIndex] & 0xff) << 8) | (message[startIndex + 1] & 0xff);
        return (short) num;
    }

    public static byte[] createAckPacket(short blockNumber) {
        return new byte[] { (byte) ((short) 4 >> 8), (byte) ((short) 4), (byte) (blockNumber >> 8),
                (byte) (blockNumber) };
    }
}
