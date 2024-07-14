package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

public class TftpServer {

  public static void main(String[] args) {
    // you can use any server you like baby...
    if (args.length == 0) {
      System.out.println("No arguments provided. setting port to 7777");
      args=new String[]{"7777"};
  }
    Server
      .threadPerClient(
        Integer.valueOf(args[0]), //port
        () -> new TftpProtocol(), //protocol factory
        () -> new TftpEncoderDecoder(), //message encoder decoder factory
        new ConnectionsImpl<byte[]>()
      )
      .serve();
  }
}
