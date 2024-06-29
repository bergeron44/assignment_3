package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private int connectionId;
    private Connections<byte[]> connections;
    private boolean shouldTerminate = false;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        // Initialize any other necessary resources here
        System.out.println("Connection started with ID: " + connectionId);
    }

    @Override
    public void process(byte[] message) {
        // Decode the message (e.g., using TftpEncoderDecoder)
        // Handle the message based on its type (RRQ, WRQ, DATA, ACK, ERROR, DIRQ,
        // LOGRQ, DELRQ, BCAST, DISC)
        // Send appropriate response using connections.send(connectionId,
        // responseMessage)
        byte[] responsePacket=null;
            short opcode = (short) ((buffer[0] << 8) | (buffer[1] & 0xFF));
            switch (opcode) {
                case 1: // RRQ
                responsePacket=handleRRQ(message);
                    break;
                case 2: // WRQ
                responsePacket=handleWRQ(message);
                    break;
                case 7: // LOGRQ
                responsePacket=handleLOGRQ(message);
                    break;
                case 8: // DELRQ
                responsePacket=handleDELRQ(message);
                    break;
                case 3: // DATA
                responsePacket=handleDATA(message);
                    break;
                case 4: // ACK
                responsePacket=handleACK(message);
                    break;
                case 5: // ERROR
                responsePacket=handleERROR(message);
                    break;
                case 6: // DIRQ
                responsePacket=handleDIRQ(message);
                    break;
                case 10: // DISC
                responsePacket=handleDISC(message);
                    break;
                case 9: // BCAST
                responsePacket=handleBCAST(message);
                    break;
            }
    }
    private byte[] handleWRQ(byte[] message){

        return true;
    }
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private boolean isDisconnectMessage(byte[] message) {
        // Check if the message is a disconnect message
        // Typically, this would involve checking the opcode of the message
        // Example check for disconnect opcode (10)
        return message.length >= 2 && message[0] == 0 && message[1] == 10;
    }

    private void handleDisconnect() {
        // Set shouldTerminate to true
        shouldTerminate = true;
        // Optionally send an ACK to the client
        byte[] ackMessage = createAckMessage();
        connections.send(connectionId, ackMessage);
    }

    private byte[] createAckMessage() {
        // Create an ACK message
        // Typically, this involves creating a byte array with the appropriate opcode
        // (4) and block number (0)
        return new byte[] { 0, 4, 0, 0 };
    }
}
