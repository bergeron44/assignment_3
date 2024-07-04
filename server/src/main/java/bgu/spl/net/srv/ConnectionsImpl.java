package bgu.spl.net.srv;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConnectionsImpl<T> implements Connections<T> {

  ConcurrentHashMap<Integer, ConnectionHandler<T>> connectionMap;
  ConcurrentHashMap<String, Integer> connectionsNameMap;

  public ReentrantReadWriteLock lock;

  public ConnectionsImpl() {
    this.connectionMap = new ConcurrentHashMap<>();
    this.connectionsNameMap = new ConcurrentHashMap<>();
    lock = new ReentrantReadWriteLock();
  }

  @Override
  public void connect(int connectionId, ConnectionHandler<T> handler) {
    if (connectionMap.get(connectionId) != null)
      return;
    connectionMap.put(connectionId, handler);
  }

  public void login(String userName, int connectionId) {
    if (connectionMap.get(connectionId) != null)
      return;
    synchronized (connectionsNameMap) {
      connectionsNameMap.put(userName, connectionId);
    }
  }

  public void logout(String userName) {
    if (connectionsNameMap.get(userName) != null)
      return;
    synchronized (connectionsNameMap) {
      connectionsNameMap.remove(userName);
    }
  }

  //
  @Override
  public boolean send(int connectionId, T msg) {
    if (connectionMap.get(connectionId) == null)
      return false;
    connectionMap.get(connectionId).send(msg);
    return true;
  }

  public boolean isExist(String userName) {
    synchronized (connectionsNameMap) {
      if (connectionsNameMap.get(userName) == null)
        return false;

      return true;
    }
  }

  @Override
  public void disconnect(int connectionId) {
    synchronized (connectionsNameMap) {
      connectionMap.remove(connectionId);
    }
    System.out.println("Connection closed for connectionId: " + connectionId);
  }

  public boolean sendAll(int connectionId, T msg) {
    synchronized (connectionsNameMap) {
      Iterator<Integer> it = connectionsNameMap.values().iterator();
      while (it.hasNext()) {
        int conId = it.next();
        if (conId != connectionId) {
          connectionMap.get(conId).send(msg);
        }
      }
    }
    return true;
  }
}
