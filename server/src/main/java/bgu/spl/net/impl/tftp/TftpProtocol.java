package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedTransferQueue;

import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private String filesPath = System.getProperty("user.dir") + "/" + "Files";
    private int connectionId;
    private ConnectionsImpl<byte[]> connections;
    private boolean shouldTerminate = false;
    private LinkedTransferQueue<byte[]> data = new LinkedTransferQueue<>();
    private String fileName = "";
    private byte[] lastPacket;
    private String connectionName;
    private String state = "INIT";
    private boolean login = false;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl) connections;
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

    // login
    private void handleLogrq(byte[] message, int connectionId) {
        String username = TftpUtils.extractString(message, 2);
        if (connections.isExist(username)) {
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

    private void handleRrq(byte[] message) {
        String filename = TftpUtils.extractString(message, 2);
        String filePath = filesPath + File.separator + filename;
        File file = new File(filePath);
        if (!file.exists()) {// file doesnot exists
            sendError(connectionId, connectionId, filename);
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            FileChannel channel = fis.getChannel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(510);

            int bytesRead;
            while ((bytesRead = channel.read(byteBuffer)) > 0) {
                byteBuffer.flip();
                byte[] chank = new byte[bytesRead];
                byteBuffer.get(chank);
                byte[] blockNum = new byte[] {
                        ((byte) ((short) data.size() >> 8)),
                        ((byte) ((short) data.size() & 0xff)),
                };
                byte[] packetSize = new byte[] {
                        ((byte) (bytesRead >> 8)),
                        (byte) (bytesRead & 0xff),
                };
                data.put(TftpUtils.concatenateArrays(
                        new byte[] { 0, 3, packetSize[0], packetSize[1], blockNum[0], blockNum[1] },
                        chank));
                byteBuffer.clear();
            }
            fis.close();
            // All the packet are ready for send

        } catch (IOException e) {
            e.printStackTrace();
            // Error reading file
            sendError(connectionId, 0, "Problem reading the file");
            return;
        }
        if (data.isEmpty()) {
            byte[] start = { 0, 3, 0, 0, 0, 1 };
            data.add(start);
        }
        sendAck(data.size());
    }

    private void handleWrq(byte[] message, int connectionId, Connections<byte[]> connections) {
        String filename = TftpUtils.extractString(message, 2);
        if (fileExists(filename)) {
            sendError(connectionId, 5, "File already exists");
        } else {
            fileName = filename;
            state = "DATA";
            sendAck((short) 0);

        }
    }

    private void handleDirq(int connectionId, Connections<byte[]> connections) {
    }

    private void handleData(byte[] message, int connectionId, Connections<byte[]> connections) {
    }

    public void handleAck(byte[] message, int connectionId, Connections<byte[]> connections) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            short opcode = buffer.getShort();
            short blockNumber = buffer.getShort();

            if (opcode == 4) {
                System.out.println("Received ACK for block number: " + blockNumber);

                if (blockNumber == 0) {
                    System.out.println("ACK for control packet (block number 0)");
                } else {
                    // ACK for DATA packet
                    System.out.println("ACK for DATA packet, preparing next block");
                    sendData(true);
                }
            } else {
                System.err.println("Unexpected opcode received: " + opcode);
            }

        } catch (BufferUnderflowException e) {
            System.err.println("Failed to parse ACK message: " + e.getMessage());
        }
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

    // logout
    private void handleDisc() {
        if (!login) {
            sendError(0, "User isn't logged in");
            return;
        }
        connections.logout(connectionName);
        sendAck(0);
        connections.disconnect(connectionId);
        shouldTerminate = true;
    }

    // Utility methods

    private void sendAck(int blockNumber) {
        byte[] ackPacket = TftpUtils.createAckPacket((short) blockNumber);
        connections.send(connectionId, ackPacket);
    }

    private void sendError(int errorCode, String errorMessage) {
        byte[] errorPacket = TftpUtils.createErrorPacket((short) errorCode, errorMessage);
        connections.send(connectionId, errorPacket);
    }

    private void sendData(boolean backup) {
        if (data.isEmpty()) {
            sendError(0, "There is no Data to send");
        } else {
            if (backup)
                connections.send(connectionId, lastPacket);
            else {
                lastPacket = data.poll();
                connections.send(connectionId, lastPacket);
            }
        }
    }
}

// Utility class for TFTP operations
class TftpUtils {

    public static byte[] createErrorPacket(short errorCode, String errorMassage) {
        byte[] opCodeByteArray = new byte[] {
                (byte) (5 >> 8),
                (byte) (5 & 0xff),
        };
        byte[] errorCodeArray = new byte[] {
                (byte) (errorCode >> 8),
                (byte) (errorCode & 0xff),
        };
        byte[] errorStart = TftpUtils.concatenateArrays(errorCodeArray, opCodeByteArray);
        byte[] errorMsg = new String(errorMassage + new String(new byte[] { 0 }))
                .getBytes();
        return concatenateArrays(errorStart, errorMsg);
    }

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

    public static byte[] concatenateArrays(byte[] array1, byte[] array2) {
        // Calculate the size of the concatenated array
        int totalLength = array1.length + array2.length;

        // Create a new byte array to hold the concatenated data
        byte[] result = new byte[totalLength];

        // Copy the contents of the first array into the result array
        System.arraycopy(array1, 0, result, 0, array1.length);

        // Copy the contents of the second array into the result array
        System.arraycopy(array2, 0, result, array1.length, array2.length);

        return result;
    }
}
