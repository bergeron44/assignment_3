package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

public class TftpServer {

    public static void main(String[] args) {
        Server.threadPerClient(
            80,
            () -> new TftpProtocol(),
            () -> new TftpEncoderDecoder(),
            new ConnectionsImpl<>()
        ).serve();
    }
}