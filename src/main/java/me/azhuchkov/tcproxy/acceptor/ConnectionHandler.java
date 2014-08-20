package me.azhuchkov.tcproxy.acceptor;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * @author Andrey Zhuchkov
 *         Date: 18.08.14
 */
public interface ConnectionHandler {
    void handle(ServerSocketChannel originateChannel, SocketChannel acceptedChannel);
}
