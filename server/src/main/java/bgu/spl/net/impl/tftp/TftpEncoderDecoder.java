package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private static final int MAX_PACKET_SIZE = 516; // TFTP packet size (4 bytes header + 512 bytes data)
    private byte[] buffer = new byte[MAX_PACKET_SIZE];
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (len >= MAX_PACKET_SIZE) {
            throw new RuntimeException("Packet size exceeds the maximum allowed size");
        }

        buffer[len++] = nextByte;

        if (len >= 2) {
            short opcode = (short) ((buffer[0] << 8) | (buffer[1] & 0xFF));
            switch (opcode) {
                case 1: // RRQ
                case 2: // WRQ
                case 7: // LOGRQ
                case 8: // DELRQ
                    if (nextByte == (byte) 0) { // End of filename or mode
                        return finalizePacket();
                    }
                    break;
                case 3: // DATA
                    if (len >= 4 + ((buffer[2] << 8) | (buffer[3] & 0xFF))) { // Length of data block
                        return finalizePacket();
                    }
                    break;
                case 4: // ACK
                    if (len == 4) {
                        return finalizePacket();
                    }
                    break;
                case 5: // ERROR
                    if (nextByte == (byte) 0) { // End of error message
                        return finalizePacket();
                    }
                    break;
                case 6: // DIRQ
                case 10: // DISC
                    if (len == 2) {
                        return finalizePacket();
                    }
                    break;
                case 9: // BCAST
                    if (nextByte == (byte) 0) {
                        return finalizePacket();
                    }
                    break;
            }
        }

        return null;
    }

    private byte[] finalizePacket() {
        byte[] packet = Arrays.copyOf(buffer, len);
        len = 0; // Reset for next packet
        return packet;
    }

    @Override
    public byte[] encode(byte[] message) {
        short opcode = (short) ((message[0] << 8) | (message[1] & 0xFF));

        switch (opcode) {
            case 1: // RRQ
            case 2: // WRQ
            case 7: // LOGRQ
            case 8: // DELRQ
                return encodeRequest(message);
            case 3: // DATA
                return encodeData(message);
            case 4: // ACK
                return encodeAck(message);
            case 5: // ERROR
                return encodeError(message);
            case 6: // DIRQ
                return encodeSimpleOpcode(message);
            case 9: // BCAST
                return encodeBcast(message);
            case 10: // DISC
                return encodeSimpleOpcode(message);
            default:
                throw new IllegalArgumentException("Unknown opcode: " + opcode);
        }
    }

    private byte[] encodeRequest(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(message.length + 1);
        buffer.put(message, 0, message.length); // Copy opcode, filename, and mode
        buffer.put((byte) 0); // Null terminator
        return buffer.array();
    }

    private byte[] encodeData(byte[] message) {
        int dataLength = message.length - 4;
        ByteBuffer buffer = ByteBuffer.allocate(4 + dataLength);
        buffer.put(message, 0, 4); // Copy opcode and block number
        buffer.put(message, 4, dataLength); // Copy data
        return buffer.array();
    }

    private byte[] encodeAck(byte[] message) {
        return Arrays.copyOf(message, 4); // ACK is always 4 bytes
    }

    private byte[] encodeError(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(message.length + 1);
        buffer.put(message); // Copy entire message
        buffer.put((byte) 0); // Null terminator for error message
        return buffer.array();
    }

    private byte[] encodeSimpleOpcode(byte[] message) {
        return Arrays.copyOf(message, 2); // Only opcode, 2 bytes
    }

    private byte[] encodeBcast(byte[] message) {
        ByteBuffer buffer = ByteBuffer.allocate(message.length + 1);
        buffer.put(message); // Copy entire message
        buffer.put((byte) 0); // Null terminator for broadcast message
        return buffer.array();
    }
}
