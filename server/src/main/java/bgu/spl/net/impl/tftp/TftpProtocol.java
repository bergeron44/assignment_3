package bgu.spl.net.impl.tftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedTransferQueue;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private String filesPath = System.getProperty("user.dir") + "/" + "Files";
    private int connectionId;
    private ConnectionsImpl<byte[]> connections;
    private boolean shouldTerminate = false;
    private LinkedTransferQueue<byte[]> data = new LinkedTransferQueue<>();
    private String fileName = "";
    private int expectedPackets;
    private String connectionName;
    private boolean login = false;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = (ConnectionsImpl) connections;
    }

    @Override
    public void process(byte[] message) {
        int opcode = TftpUtils.extractShort(message, 0);
        if (opcode != 7 && !login) {
            sendError(6, "User isn't logged in");
            return;
        }
        switch (opcode) {
            case 1:
                handleRrq(message);
                break;
            case 2:
                handleWrq(message);
                break;
            case 3:
                handleData(message);
                break;
            case 4:
                handleAck(message);
                break;
            case 5:
                handleError(message);
                break;
            case 6:
                handleDirq();
                break;
            case 7:
                handleLogrq(message);
                break;
            case 8:
                handleDelrq(message);
                break;
            case 10:
                handleDisc();
                break;
            default:
                sendError(4, "Illegal TFTP operation");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // Handler implementations

    // login
    private void handleLogrq(byte[] message) {
        String username = TftpUtils.extractString(message, 2);
        if (connections.isExist(username) || login) {
            sendError(7, "User already logged in");
        } else {
            connections.login(username, connectionId);
            login = true;
            connectionName = username;
            sendAck(0);
        }
    }

    private void handleDelrq(byte[] message) {
        String filename = TftpUtils.extractString(message, 2);
        String filePath = filesPath + File.separator + filename;
        connections.lock.writeLock().lock();
        File file = new File(filePath, filename);
        if (!file.exists()) {// file doesnot exists
            sendError(1, "file not found");
            connections.lock.writeLock().unlock();
            return;
        }
        boolean sucsesfuly = file.delete();
        connections.lock.writeLock().unlock();
        if (!sucsesfuly)
        sendError(2, "delte didnt secceded");
        else
            sendBcast(filename, (short) 0);
    }

    private void handleRrq(byte[] message) {
        String filename = TftpUtils.extractString(message, 2);
        String filePath = filesPath + File.separator + filename;
        connections.lock.readLock().lock();
        File file = new File(filePath, filename);
        if (!file.exists()) {// file doesnot exists
            connections.lock.readLock().unlock();
            sendError(1, "file not found");
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
            connections.lock.readLock().unlock();
            // All the packet are ready for send

        } catch (IOException e) {
            e.printStackTrace();
            connections.lock.readLock().unlock();
            // Error reading file
            sendError(2, "Problem reading the file");
            return;
        }
        if (data.isEmpty()) {
            byte[] start = { 0, 3, 0, 0, 0, 1 };
            data.add(start);
        }
        sendAck(data.size());
    }

    private void handleWrq(byte[] message) {
        String filename = TftpUtils.extractString(message, 2);
        String filePath = filesPath + File.separator + filename;
        connections.lock.writeLock().lock();
        File file = new File(filePath, filename);
        if (file.exists()) {// file  exists
            connections.lock.writeLock().unlock();
            sendError(5, "file found");
            return;
        }
        try{
            if(file.createNewFile())
            {
                fileName = filename;
                sendAck((short) 0);
            }  
            else
            {
                sendError(2, "access vaioltion");
            }    
        }
       catch(IOException E){
        sendError(2, "access vaioltion");

       }
       finally{
        connections.lock.writeLock().unlock();
       }
        
    }

    private void handleDirq() {
        String directoryPath = filesPath;
        List<String> fileNames = getFileNamesFromDirectory(directoryPath);

        // Create DIRQ packet data
        byte[] data = createDirqPacket(fileNames);

        // Send the data back through connections
        connections.send(connectionId, data);
    }

    private List<String> getFileNamesFromDirectory(String directoryPath) {
        List<String> fileNames = new ArrayList<>();
        connections.lock.readLock().lock();
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    fileNames.add(file.getName());
                }
            }
            connections.lock.readLock().unlock();
        } else {
            connections.lock.readLock().unlock();
            System.err.println("Directory not found or is empty: " + directoryPath);
        }

        return fileNames;
    }

    private byte[] createDirqPacket(List<String> fileNames) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            for (String fileName : fileNames) {
                outputStream.write(fileName.getBytes("UTF-8"));
                outputStream.write(0); // zero byte after each file name
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outputStream.toByteArray();
    }

    private void handleData(byte[] message) {
        try {

            ByteBuffer buffer = ByteBuffer.wrap(message);
            short opcode = buffer.getShort(); // Extracting opcode (first 2 bytes)
            short packetSize = buffer.getShort(); // Extracting packet size (next 2 bytes)
            short blockNumber = buffer.getShort(); // Extracting block number (next 2 bytes)
            byte[] allData = new byte[packetSize];
            buffer.get(allData); // Extracting data section
            System.out.println("Received DATA packet for block number: " + blockNumber);
            data.add(allData);
            if ((packetSize < 512) && (blockNumber != expectedPackets)) {
                System.out.println("not all expected packets received");
                sendError(0, "not all packets recived");
            } else if (blockNumber == expectedPackets) {
                boolean filecreated = uplode();
                if (filecreated) {
                    System.out.println("the file create sucsesfuly");
                    sendBcast(fileName, (short) 1);
                } else
                    System.out.println("fail to create file");
            }
        } catch (BufferUnderflowException e) {
            System.err.println("Failed to parse DATA message: " + e.getMessage());
        }
    }

    public void handleAck(byte[] message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            short opcode = buffer.getShort();
            short blockNumber = buffer.getShort();

            if (opcode == 4) {
                System.out.println("Received ACK for block number: " + blockNumber);

                if (blockNumber == 0) {
                    System.out.println("ACK for control packet (block number 0)");
                    if (!data.isEmpty())
                        sendData();
                } else {
                    // ACK for DATA packet
                    System.out.println("ACK for DATA packet, preparing next block");
                    expectedPackets = blockNumber;
                }
            } else {
                System.err.println("Unexpected opcode received: " + opcode);
            }

        } catch (BufferUnderflowException e) {
            System.err.println("Failed to parse ACK message: " + e.getMessage());
        }
    }

    private void sendBcast(String filename, short deleteOrAdd) {
        // This handler is usually server-initiated, so implementation might vary based
        // on your server logic
        byte[] bCastStart = { 0, 9, (byte) (deleteOrAdd >> 8),
                (byte) (deleteOrAdd & 0xff), };
        String fileNameWithNullByte = filename + "\0";
        connections.sendAll(
                connectionId,
                TftpUtils.concatenateArrays(bCastStart, fileNameWithNullByte.getBytes()));
    }

    private void handleError(byte[] message) {
        int errorCode = TftpUtils.extractShort(message, 2);
        String errorMessage = TftpUtils.extractString(message, 4);
        System.err.println("Error received from client " + connectionId + ": " + errorCode + " - " + errorMessage);
        data.clear();
    }

    // logout
    private void handleDisc() {
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

    private void sendData() {
        if (data.isEmpty()) {
            sendError(0, "There is no Data to send");
        } else {
            connections.send(connectionId, data.poll());
        }
    }

    public boolean uplode() {
        String filePath = filesPath + File.separator + fileName;
        connections.lock.writeLock().lock();
        File file = new File(filePath, fileName);
        if (!file.exists()) {// file does not exists
            connections.lock.writeLock().unlock();
            return false;
        }
        LinkedTransferQueue<byte[]> backup = new LinkedTransferQueue<>();
        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            while (!data.isEmpty()) {
                byte[] packet = data.poll();
                backup.put(packet);
                fos.write(packet);
            }
            connections.lock.writeLock().unlock();
        } catch (IOException e) {
            file=new File(filePath, fileName);
            file.delete(); 
            while (!data.isEmpty()) {
                backup.put(data.poll());
            }
            while (!backup.isEmpty()) {
                data.put(data.poll());
            }
            connections.lock.writeLock().unlock();
            return false;
        }
        return true;
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
