package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

public class TftpServer {
    Server
      .threadPerClient(
        Integer.valueOf(args[0]), //port
        () -> new TftpProtocol(), //protocol factory
        () -> new TftpEncoderDecoder(), //message encoder decoder factory
        new ConnectionsImpl<byte[]>()
      )
      .serve();
    
}
