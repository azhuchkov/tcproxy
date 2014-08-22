package me.azhuchkov.tcproxy.acceptor;

import java.nio.channels.ServerSocketChannel;
import java.util.Collection;

/**
 * Accepts incoming connections form given server sockets.
 *
 * @author Andrey Zhuchkov
 *         Date: 18.08.14
 */
public interface Acceptor {
    /**
     * Starts the acceptor.
     *
     * @param channels Channels to listen for incoming connection.
     */
    void start(Collection<ServerSocketChannel> channels);

    /**
     * Sends stop signal to acceptor.
     */
    void interrupt();

    /**
     * Waits for acceptor to fully stop.
     *
     * @throws InterruptedException If thread is interrupted during this operation.
     */
    void join() throws InterruptedException;
}
