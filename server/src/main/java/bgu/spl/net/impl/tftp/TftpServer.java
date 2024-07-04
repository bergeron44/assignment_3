package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

import java.util.function.Supplier;

public class TftpServer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java TftpServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);

        // Create Suppliers for protocol and encoder/decoder
        Supplier<MessagingProtocol<byte[]>> protocolFactory = TftpProtocol::new;
        Supplier<MessageEncoderDecoder<byte[]>> encoderDecoderFactory = TftpEncoderDecoder::new;

        // Create the server with thread-per-client model
        Server<byte[]> server = Server.threadPerClient(
                port,
                protocolFactory,
                encoderDecoderFactory,
                new ConnectionsImpl<>()
        );

        // Start serving
        server.serve();
    }
}
