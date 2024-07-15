package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

  private byte[] buffer = new byte[1 << 10]; // start with 1k
  private int length = 0;
  private short operationCode;

  @Override
  public byte[] decodeNextByte(byte nextByte) {
    byte[] returnBytes;
    if (length < 2) { // Reading the opcode:
      buffer[length] = nextByte;
      length++;
      if (length == 2) { // Save the opcode as short:
        operationCode = (short) (((short) buffer[0] & 0xff) << 8 | (short) (buffer[1] & 0xff));
        if (operationCode == 6 || operationCode == 10) {
          returnBytes = Arrays.copyOfRange(buffer, 0, length);
          buffer = new byte[1 << 10];
          length = 0;
          return returnBytes;
        }
      }
    } else { // Finished reading opcode
      if (
        operationCode == 1 || // Read request
        operationCode == 2 || // Write request
        operationCode == 7 || // Login request
        operationCode == 8 // Delete file request
      ) {
        if (nextByte == 0) {
          returnBytes = Arrays.copyOfRange(buffer, 0, length);
          buffer = new byte[1 << 10];
          length = 0;
          return returnBytes;
        }
        buffer[length] = nextByte;
        length++;
      }
      if (operationCode == 4) {
        buffer[length] = nextByte;
        length++;
        if (length == 4) {
          returnBytes = Arrays.copyOfRange(buffer, 0, length);
          buffer = new byte[1 << 10];
          length = 0;
          return returnBytes;
        }
      }
      if (operationCode == 3) { // Data
        buffer[length] = nextByte;
        length++;

        if (length >= 6) {
          short packetSize = (short) (((short) buffer[2]) << 8 | (short) (buffer[3] & 0xff));
          if (length == 6 + packetSize) {
            returnBytes = Arrays.copyOfRange(buffer, 0, length);
            buffer = new byte[1 << 10];
            length = 0;
            return returnBytes;
          }
        }
      }
      if (operationCode == 9) { // BCAST
        if (nextByte == 0 && length != 2) {
          returnBytes = Arrays.copyOfRange(buffer, 0, length);
          buffer = new byte[1 << 10];
          length = 0;
          return returnBytes;
        }
        buffer[length] = nextByte;
        length++;
      }
      if (operationCode == 5) {
        if (length < 4) {
          buffer[length] = nextByte;
          length++;
        } else {
          if (nextByte == 0) {
            returnBytes = Arrays.copyOfRange(buffer, 0, length);
            buffer = new byte[1 << 10];
            length = 0;
            return returnBytes;
          }
          buffer[length] = nextByte;
          length++;
        }
      }
    }

    return null;
  }

  @Override
  public byte[] encode(byte[] message) {
    return message;
  }
}
