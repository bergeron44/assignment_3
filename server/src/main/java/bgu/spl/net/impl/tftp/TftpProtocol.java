package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.ConnectionsImpl;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

  String basePath = System.getProperty("user.dir") + "/" + "Flies";
  String clientName = "None";
  private int connectionId;
  private ConnectionsImpl<byte[]> connections;
  boolean shouldTerminate = false;
  boolean isLogged;
  byte[] errorCodes = { 0, 5 };
  String latestFileName = "";
  ByteBuffer dataPacket;
  short fileReadCounter = 1;
  short writeIndex = 1;
  ConcurrentLinkedQueue<byte[]> fileReadQueue;
  FileOutputStream outputStream;

  @Override
  public void start(
      int connectionIdVal,
      ConnectionsImpl<byte[]> connectionsVal) {
    this.connectionId = connectionIdVal;
    this.connections = connectionsVal;
    isLogged = false;

    fileReadQueue = new ConcurrentLinkedQueue<>();
  }

  @Override
  public void process(byte[] message) {
    short opCode = (short) (((short) message[0] & 0xff) << 8 | (short) (message[1] & 0xff));
    switch (opCode) {
      case 1: // RRQ client wants to read a file
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("RRQ request received");
        String rrqFileName = new String(message, 2, message.length - 2);
        connections.lock.readLock().lock();
        String filePath = basePath + File.separator + rrqFileName;
        File rrqFile = new File(filePath);
        if (!rrqFile.exists()) {
          connections.lock.readLock().unlock();
          sendError((short) 1, "File not found");
        } else {
          try {
            FileInputStream fis = new FileInputStream(filePath);
            FileChannel channel = fis.getChannel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(512);

            int bytesRead;
            while ((bytesRead = channel.read(byteBuffer)) != -1) {
              byteBuffer.rewind();

              byte[] chunk = new byte[bytesRead];
              byteBuffer.get(chunk);

              byte[] blockNum = new byte[] {
                  (byte) (fileReadCounter >> 8),
                  (byte) (fileReadCounter & 0xff),
              };
              byte[] packetSize = new byte[] {
                  (byte) (bytesRead >> 8),
                  (byte) (bytesRead & 0xff),
              };
              byte[] start = {
                  0, 3, packetSize[0], packetSize[1], blockNum[0], blockNum[1],
              };

              fileReadCounter++;
              fileReadQueue.add(concatenateArrays(start, chunk));
              byteBuffer.clear();
            }
            fileReadCounter = 1;
            fis.close();
          } catch (IOException e) {
            e.printStackTrace();
            sendError((short) 0, "Problem reading the file");
            return;
          }
          if (fileReadQueue.isEmpty()) {
            byte[] start = { 0, 3, 0, 0, 0, 1 };
            fileReadQueue.add(start);
          }
          connections.send(connectionId, fileReadQueue.remove());
        }
        break;
      case 2: // WRQ client wants to write a file
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("WRQ request received");
        String wrqFileName = new String(message, 2, message.length - 2);
        connections.lock.writeLock().lock();
        File wrqFile = new File(basePath, wrqFileName);
        if (wrqFile.exists()) {
          connections.lock.writeLock().unlock();
          sendError((short) 5, "File already exists");
        } else {
          try {
            latestFileName = wrqFileName;
            boolean created = wrqFile.createNewFile();

            if (!created) {
              connections.lock.writeLock().unlock();
              sendError((short) 0, "Problems creating the file");
              return;
            }
          } catch (IOException e) {
            connections.lock.writeLock().unlock();
            sendError((short) 0, "Problems creating the file");
            return;
          }

          byte[] ack = { 0, 4, 0, 0 };
          try {
            outputStream = new FileOutputStream(wrqFile);
          } catch (IOException e) {
            e.printStackTrace();
          }

          connections.send(connectionId, ack);
        }
        break;
      case 3: // DATA packet
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        short dataBlockNum = (short) (((short) message[4] & 0xff) << 8 | (short) (message[5] & 0xff));
        short blockLength = (short) (((short) message[2] & 0xff) << 8 | (short) (message[3] & 0xff));
        if (dataBlockNum != writeIndex) {
          File wrqFileData = new File(basePath, latestFileName);
          wrqFileData.delete();
          connections.lock.writeLock().unlock();
          sendError((short) 0, "Got the wrong block");
        } else {
          if (blockLength > 0) {
            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            try {
              outputStream.write(data);
            } catch (IOException e) {
              File wrqFileData = new File(basePath, latestFileName);
              wrqFileData.delete();
              connections.lock.writeLock().unlock();
              sendError((short) 0, "Problem writing to the file");
            }
          }

          byte[] ack = { 0, 4, message[4], message[5] };
          writeIndex++;
          connections.send(connectionId, ack);
          if (blockLength < 512) {
            System.out.println("File " + latestFileName + " was written successfully");
            writeIndex = 1;
            byte[] bCASTStart = { 0, 9, 1 };
            String fileNameWithNullByte = latestFileName + "\0";
            connections.bCast(
                connectionId,
                concatenateArrays(bCASTStart, fileNameWithNullByte.getBytes()));
            connections.lock.writeLock().unlock();
          }
        }
        break;
      case 4: // ACK packet
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("Ack packet received");
        short ackBlockNum = (short) (((short) message[2] & 0x00ff) << 8 | (short) (message[3] & 0x00ff));
        if (fileReadQueue.isEmpty()) {
          fileReadCounter = 1;
          connections.lock.readLock().unlock();
          return;
        }
        if (ackBlockNum != fileReadCounter) {
          fileReadQueue.clear();
          fileReadCounter = 1;
          sendError((short) 0, "Got the wrong block");
          connections.lock.readLock().unlock();
          return;
        }
        connections.send(connectionId, fileReadQueue.remove());
        fileReadCounter++;
        break;
      case 5: // ERROR packet
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("Error packet received");
        String errorMsg = new String(message, 4, message.length - 4);
        short errorCode = (short) (((short) message[2] & 0xff) << 8 | (short) (message[3] & 0xff));
        System.err.println("Error " + errorCode + ": " + errorMsg);
        fileReadQueue.clear();
        fileReadCounter = 1;
        break;
      case 6: // DIRQ request client wants to get the list of files
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("DIRQ request received");
        connections.lock.readLock().lock();
        List<String> fileNamesList = getFileNames();
        StringBuilder sb = new StringBuilder();
        for (String fileName : fileNamesList) {
          sb.append(fileName).append("\0");
        }

        byte[] byteArray = sb.toString().getBytes();

        List<byte[]> splitIntoChunks = splitByteArray(byteArray);

        for (int i = 0; i < splitIntoChunks.size(); i++) {
          short packetSizeShort = (short) splitIntoChunks.get(i).length;
          byte[] packetSizeBytes = new byte[] {
              (byte) (packetSizeShort >> 8),
              (byte) (packetSizeShort & 0xff),
          };
          byte[] indexByte = new byte[] {
              (byte) ((short) (i + 1) >> 8),
              (byte) ((i + 1) & 0xff),
          };
          byte[] start = {
              0, 3, packetSizeBytes[0], packetSizeBytes[1], indexByte[0], indexByte[1],
          };
          byte[] msg = concatenateArrays(start, splitIntoChunks.get(i));
          fileReadQueue.add(msg);
        }
        connections.send(connectionId, fileReadQueue.remove());
        break;
      case 7: // LOGRQ client wants to logIn
        if (isLogged) {
          sendError((short) 7, "User is already logged in");
          return;
        } else {
          System.out.println("LOGRQ request received");
          String userName = new String(message, 2, message.length - 2);
          if (connections.checkIfLoggedin(userName) != null) {
            sendError((short) 0, "The username you gave is already logged in");
            return;
          } else {
            isLogged = true;
            connections.logIn(userName, connectionId);
            clientName = userName;
            byte[] ack = { 0, 4, 0, 0 };
            connections.send(connectionId, ack);
          }
        }
        break;
      case 8: // DELRQ client wants to delete a file
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("DELRQ request received");
        connections.lock.writeLock().lock();
        String delrqFileName = new String(message, 2, message.length - 2);
        File delrqFile = new File(basePath, delrqFileName);
        if (!delrqFile.exists()) {
          connections.lock.writeLock().unlock();
          sendError((short) 1, "File not found");
          return;
        } else {
          if (!delrqFile.delete()) {
            connections.lock.writeLock().unlock();
            sendError((short) 0, "Error deleting the file");
            return;
          }
          byte[] bCastStart = { 0, 9, 0 };
          String delrqFileNameWithNullByte = delrqFileName + "\0";
          byte[] ack = { 0, 4, 0, 0 };
          connections.send(connectionId, ack);
          connections.bCast(
              connectionId,
              concatenateArrays(bCastStart, delrqFileNameWithNullByte.getBytes()));
          connections.lock.writeLock().unlock();
        }
        break;
      case 10: // DISC request client wants to logOut
        if (!isLogged) {
          sendError((short) 6, "User isn't logged in");
          return;
        }
        System.out.println("DISC request received");
        isLogged = false;
        connections.logOut(clientName);
        clientName = "None";
        byte[] ack = { 0, 4, 0, 0 };
        System.out.println("ACK send before terminate");
        connections.send(connectionId, ack);
        connections.disconnect(connectionId);
        shouldTerminate = true;
        break;
      default:
        // Handle unknown opCode
        break;
    }
  }

  @Override
  public boolean shouldTerminate() {
    return shouldTerminate;
  }

  public static byte[] concatenateArrays(byte[] array1, byte[] array2) {
    // Calculate the size of the concatenated array
    int totalLength = array1.length + array2.length;

    // Create a new byte array to hold the concatenated dataPacket
    byte[] result = new byte[totalLength];

    // Copy the contents of the first array into the result array
    System.arraycopy(array1, 0, result, 0, array1.length);

    // Copy the contents of the second array into the result array
    System.arraycopy(array2, 0, result, array1.length, array2.length);

    return result;
  }

  // functions that sends Errors to users
  public void sendError(short opCode, String message) {
    byte[] opCodeByteArray = new byte[] {
        (byte) (opCode >> 8),
        (byte) (opCode & 0xff),
    };
    byte[] errorStart = concatenateArrays(errorCodes, opCodeByteArray);
    byte[] errorMsg = new String(message + new String(new byte[] { 0 }))
        .getBytes();
    connections.send(connectionId, concatenateArrays(errorStart, errorMsg));
  }

  public List<String> getFileNames() {
    List<String> fileNamesList = new ArrayList<>();
    File folder = new File(basePath);

    // Check if the folder exists and is a directory
    if (folder.exists() && folder.isDirectory()) {
      // Get all files in the folder
      File[] files = folder.listFiles();
      if (files != null) {
        for (File file : files) {
          // Add file names to the list
          fileNamesList.add(file.getName());
        }
      }
    } else {
      System.out.println("Folder does not exist or is not a directory.");
    }
    if (fileNamesList.size() == 0) {
      fileNamesList.add("No files in the server");
    }
    return fileNamesList;
  }

  public static List<byte[]> splitByteArray(byte[] byteArray) {
    int chunkSize = 512;
    int numChunks = (byteArray.length + chunkSize - 1) / chunkSize;
    List<byte[]> chunks = new ArrayList<>();

    for (int i = 0; i < numChunks - 1; i++) {
      int startIndex = i * chunkSize;
      int endIndex = startIndex + chunkSize;
      byte[] chunk = new byte[chunkSize];
      System.arraycopy(byteArray, startIndex, chunk, 0, chunkSize);
      chunks.add(chunk);
    }

    // Last chunk
    int lastChunkStart = (numChunks - 1) * chunkSize;
    int lastChunkSize = byteArray.length - lastChunkStart;
    byte[] lastChunk = new byte[lastChunkSize];
    System.arraycopy(byteArray, lastChunkStart, lastChunk, 0, lastChunkSize);
    chunks.add(lastChunk);

    return chunks;
  }
}
