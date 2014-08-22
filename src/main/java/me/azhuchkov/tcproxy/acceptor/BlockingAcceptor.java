package me.azhuchkov.tcproxy.acceptor;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Acceptor that listens for incoming connection using blocking I/O.
 * Each socket handled by separate thread.
 *
 * @author Andrey Zhuchkov
 *         Date: 11.08.14
 */
public class BlockingAcceptor implements Acceptor {
    /** Logger. */
    private final static Logger LOGGER = Logger.getLogger(BlockingAcceptor.class.getName());

    /** Handler to pass new connection to. */
    private final ConnectionHandler handler;

    /** Prefix for acceptor threads. */
    private final String prefix;

    /** Array of acceptor threads. */
    private volatile Acceptor[] acceptors;

    /**
     * Creates new acceptor.
     *
     * @param prefix  Prefix for acceptor threads.
     * @param handler Handler to pass new connections to.
     */
    public BlockingAcceptor(String prefix, ConnectionHandler handler) {
        this.handler = handler;
        this.prefix = prefix;
    }

    /** {@inheritDoc} */
    @Override
    public void start(Collection<ServerSocketChannel> channels) {
        if (acceptors != null)
            throw new IllegalStateException("already started");

        Acceptor[] acceptors0;

        synchronized (this) {
            if (acceptors != null)
                throw new IllegalStateException("already started");

            int i = 0;

            acceptors0 = new Acceptor[channels.size()];

            for (ServerSocketChannel channel : channels) {
                acceptors0[i] = new Acceptor(prefix + i, channel);
                i++;
            }

            acceptors = acceptors0;
        }

        for (Acceptor acceptor : acceptors0)
            acceptor.start();
    }

    /** {@inheritDoc} */
    @Override
    public void interrupt() {
        final Acceptor[] acceptors0 = acceptors;

        if (acceptors0 == null)
            throw new IllegalStateException("not started");

        for (Acceptor acceptor : acceptors0)
            acceptor.interrupt();
    }

    /** {@inheritDoc} */
    @Override
    public void join() throws InterruptedException {
        final Acceptor[] acceptors0 = acceptors;

        if (acceptors0 == null)
            throw new IllegalStateException("not started");

        for (Acceptor acceptor : acceptors)
            acceptor.join();
    }

    /**
     * Acceptor thread.
     */
    private class Acceptor extends Thread {
        /** Channel to listen connections. */
        private final ServerSocketChannel channel;

        /**
         * @param name    Thread name.
         * @param channel Channel to listen connections.
         */
        public Acceptor(String name, ServerSocketChannel channel) {
            super(name);

            this.channel = channel;
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    SocketChannel accepted = channel.accept();

                    try {
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
            } catch (ClosedByInterruptException e) {
                // just exit
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Incoming connections acceptor failure.", e);
            }
        }
    }
}
