package me.azhuchkov.tcproxy.acceptor;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * Connection handler that acceptors use to pass new connections.
 *
 * @author Andrey Zhuchkov
 *         Date: 18.08.14
 */
public interface ConnectionHandler {
    /**
     * Handles new connection.
     *
     * @param originateChannel Channel that new connection accepted from.
     * @param acceptedChannel  Accepted connection channel.
     */
    void handle(ServerSocketChannel originateChannel, SocketChannel acceptedChannel);
}
