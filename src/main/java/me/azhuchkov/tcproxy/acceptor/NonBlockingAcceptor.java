package me.azhuchkov.tcproxy.acceptor;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceptor that listens for incoming connection using non-blocking I/O.
 *
 * @author Andrey Zhuchkov
 *         Date: 11.08.14
 */
public class NonBlockingAcceptor extends Thread implements Acceptor {
    /** Logger. */
    private final static Logger LOGGER = Logger.getLogger(NonBlockingAcceptor.class.getName());

    /** Handler to pass new connection to. */
    private final ConnectionHandler handler;

    /** Channels that acceptor should listen for new connections. */
    private volatile Collection<ServerSocketChannel> channels;

    /**
     * Creates new acceptor.
     *
     * @param name    Acceptor thread name.
     * @param handler Connection handler.
     */
    public NonBlockingAcceptor(String name, ConnectionHandler handler) {
        super(name);

        this.handler = handler;
    }

    /** {@inheritDoc} */
    @Override
    public void start(Collection<ServerSocketChannel> channels) {
        if (this.channels != null)
            throw new IllegalThreadStateException("already started");

        synchronized (this) {
            if (this.channels != null)
                throw new IllegalThreadStateException("already started");

            this.channels = channels;
        }

        start();
    }

    /** {@inheritDoc} */
    @Override
    public void run() {
        if (channels == null)
            throw new IllegalStateException("channels must be provided");

        try (Selector selector = Selector.open()) {
            for (ServerSocketChannel channel : channels) {
                channel.configureBlocking(false);
                channel.register(selector, SelectionKey.OP_ACCEPT);
            }

            while (!isInterrupted()) {
                selector.select();

                if (isInterrupted())
                    break;

                for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                    SelectionKey key = iter.next();

                    iter.remove();

                    final ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                    final SocketChannel accepted = channel.accept();

                    if (accepted != null) try {
                        handler.handle(channel, accepted);
                    } catch (Exception e) {
                        LOGGER.severe("Failed to handle new connection: " + e);

                        try {
                            accepted.close();
                        } catch (IOException e1) {
                            LOGGER.severe("Failed to close connection: " + e1);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Incoming connections acceptor failure.", e);
        }
    }
}
