package me.azhuchkov.tcproxy.acceptor;

import java.nio.channels.ServerSocketChannel;
import java.util.Collection;

/**
 * @author Andrey Zhuchkov
 *         Date: 18.08.14
 */
public interface Acceptor {
    void start(Collection<ServerSocketChannel> channels);

    void interrupt();

    void join() throws InterruptedException;
}
