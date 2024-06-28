package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

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
        // Handle the message based on its type (RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC)
        // Send appropriate response using connections.send(connectionId, responseMessage)
        
        // Example for handling a disconnect (DISC) message:
        if (isDisconnectMessage(message)) {
            handleDisconnect();
        } else {
            // Handle other message types
        }
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
        // Typically, this involves creating a byte array with the appropriate opcode (4) and block number (0)
        return new byte[] {0, 4, 0, 0};
    }
}
