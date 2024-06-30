package bgu.spl.net.srv;

import java.util.HashMap;
import java.util.Map;

public class ByteConnections implements Connections<byte[]> {

    private Map<Integer, ConnectionHandler<byte[]>> connectionMap;

    public ByteConnections() {
        this.connectionMap = new HashMap<>();
    }

    
    public void connect(int connectionId, ConnectionHandler<byte[]> handler) {
        connectionMap.put(connectionId, handler);
        System.out.println("Connection established for connectionId: " + connectionId);
    }

  
    public boolean send(int connectionId, byte[] msg) {
        ConnectionHandler<byte[]> handler = connectionMap.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionMap.remove(connectionId);
        System.out.println("Connection closed for connectionId: " + connectionId);
    }
}
