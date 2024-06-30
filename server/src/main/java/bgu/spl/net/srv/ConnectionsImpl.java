package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap;
    ConcurrentHashMap<String, Integer> connectionsNameMap;

    public ConnectionsImpl() {
        this.connectionMap = new ConcurrentHashMap<>();
        this.connectionsNameMap = new ConcurrentHashMap<>();
    }

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
      if (connectionMap.get(connectionId) != null) return;
      connectionMap.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
      if (connectionMap.get(connectionId) == null) return false;
      connectionMap.get(connectionId).send(msg);
      return true;
    }

    @Override
    public void disconnect(int connectionId) {
        connectionMap.remove(connectionId);
        System.out.println("Connection closed for connectionId: " + connectionId);
    }
}
