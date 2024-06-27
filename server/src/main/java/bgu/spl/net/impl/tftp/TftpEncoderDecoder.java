package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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
                    if (nextByte == 0) {
                        return finalizePacket();
                    }
                    break;
                case 3: // DATA
                    if (len >= 4 + ((buffer[2] << 8) | (buffer[3] & 0xFF))) {
                        return finalizePacket();
                    }
                    break;
                case 4: // ACK
                    if (len == 4) {
                        return finalizePacket();
                    }
                    break;
                case 5: // ERROR
                    if (nextByte == 0) {
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
                    if (nextByte == 0) {
                        return finalizePacket();
                    }
                    break;
            }
        }

        return null; // Packet is not yet complete
    }

    private byte[] finalizePacket() {
        byte[] packet = Arrays.copyOf(buffer, len);
        len = 0; // Reset for next packet
        return packet;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message; // Assuming message is already in the correct byte format
    }
}
