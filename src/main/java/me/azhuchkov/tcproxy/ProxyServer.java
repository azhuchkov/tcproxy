package me.azhuchkov.tcproxy;

import me.azhuchkov.tcproxy.channel.NetworkChannelFactory;
import me.azhuchkov.tcproxy.channel.ServerSocketChannelFactory;
import me.azhuchkov.tcproxy.channel.SocketChannelFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Non-blocking TCP proxy server.
 *
 * @author Andrey Zhuchkov
 *         Date: 08.08.14
 */
public class ProxyServer {
    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger("PROXY");

    /**
     * Server socket channel factory.
     */
    private final NetworkChannelFactory<ServerSocketChannel> serverSocketFactory;

    /**
     * Socket channel factory.
     */
    private final NetworkChannelFactory<SocketChannel> socketFactory;

    /**
     * Socket address to bind.
     */
    private final SocketAddress address;

    /**
     * Maximum number of pending connections.
     */
    private final int backlog;

    /**
     * todo
     */
    private volatile ServerSocketChannel channel;

    /**
     * Server lifecycle mutex.
     */
    private final Object mutex = new Object();

    /**
     * todo
     */
    private volatile Selector selector;

    /**
     * Main background thread that manages all the events.
     */
    private final ConnectionManager connectionManager = new ConnectionManager("connection-manager");

    /**
     * todo
     * @param address
     * @param backlog
     */
    public ProxyServer(SocketAddress address, int backlog) {
        this(ServerSocketChannelFactory.DEFAULT, address, backlog, SocketChannelFactory.DEFAULT);
    }

    /**
     * Creates new instance of proxy server.
     *
     * @param serverSocketFactory Factory for creating server socket channels.
     * @param address Bind address.
     * @param backlog Maximum number of pending connections. If value is 0 or less, OS default value will be used.
     */
    public ProxyServer(NetworkChannelFactory<ServerSocketChannel> serverSocketFactory, SocketAddress address, int backlog,
                       NetworkChannelFactory<SocketChannel> socketChannelFactory) {
        this.serverSocketFactory = serverSocketFactory;
        this.address = address;
        this.backlog = backlog;
        this.socketFactory = socketChannelFactory;
    }

    /**
     * Starts server: opens channel and do bind.
     *
     * @throws IOException           If failed.
     * @throws IllegalStateException If server already started.
     */
    public void start() throws IOException {
        if (this.channel != null)
            throw new IllegalStateException("Server already started");

        ServerSocketChannel channel0;

        synchronized (mutex) {
            if (this.channel != null)
                throw new IllegalStateException("Server already started");

            this.channel = channel0 = serverSocketFactory.newChannel();
        }

        channel0.configureBlocking(false);

        selector = Selector.open();

        channel0.register(selector, SelectionKey.OP_ACCEPT);

        channel0.bind(address, backlog);

        LOGGER.info("Start listening on " + address + " with backlog: " + backlog);

        connectionManager.start();
    }

    /**
     * todo
     *
     * @throws IOException
     */
    public void shutdown() throws IOException {
        ServerSocketChannel channel0 = this.channel;

        if (channel0 == null || !channel0.isOpen())
            throw new IllegalStateException("Server is not started");

        synchronized (mutex) {
            if (!channel0.isOpen())
                throw new IllegalStateException("Server is not started");

            channel0.close();
        }

        connectionManager.interrupt();
    }

    private void onAccept(SelectionKey key) throws IOException {
        SocketChannel acceptedChannel = channel.accept();
        SocketChannel mappedChannel = socketFactory.newChannel();

        acceptedChannel.configureBlocking(false);
        acceptedChannel.register(selector, SelectionKey.OP_READ, mappedChannel);

        mappedChannel.configureBlocking(false);
        mappedChannel.register(
                selector,
                SelectionKey.OP_CONNECT | SelectionKey.OP_READ,
                acceptedChannel);

        InetSocketAddress remote = new InetSocketAddress("ya.ru", 80);

        if (remote.isUnresolved()) {
            LOGGER.severe("Closing connection due to failed to resolve remote: " + remote);
            acceptedChannel.close();
            mappedChannel.close();
        } else {
            mappedChannel.connect(remote);
            LOGGER.info("Incoming connection from: " + acceptedChannel.getRemoteAddress());
        }
    }

    private void onConnect(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        if (!socketChannel.finishConnect()) {
            LOGGER.severe("Failed to connect to remote destination. " +
                    "Closing originating connection.");
            ((SocketChannel) key.attachment()).close();
        } else
            LOGGER.info("Established connection with remote: " + socketChannel.getRemoteAddress());
    }

    private void onDataAvailable(SelectionKey key) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(32);

        SocketChannel originateChannel = (SocketChannel) key.channel();
        SocketChannel pipedChannel = (SocketChannel) key.attachment();

        if (pipedChannel.isConnectionPending())
            return;

        int read = originateChannel.read(buf);

        if (read == -1) {
            LOGGER.info("Connection with " + originateChannel.getRemoteAddress() + " has been closed");
            try {
                originateChannel.close();
            } finally {
                pipedChannel.close();
            }
        }

        while (read > 0) {
            buf.flip();

            while (buf.hasRemaining())
                pipedChannel.write(buf);

            buf.clear();

            read = originateChannel.read(buf);
        }
    }

    /**
     * Thread that manages connections: accepting incoming ones, disconnects handling, etc.
     */
    private class ConnectionManager extends Thread {
        private ConnectionManager(String name) {
            super(name);
        }

        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    int selected = selector.select();

                    if (selected == 0)
                        continue;

                    for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                        SelectionKey key = iter.next();

                        if (key.isAcceptable())
                            onAccept(key);
                        else if (key.isConnectable())
                            onConnect(key);
                        else if (key.isReadable())
                            onDataAvailable(key);
                        else
                            throw new RuntimeException("unsupported event selected: " + key);

                        iter.remove();
                    }
                }
            } catch (IOException e) {
                LOGGER.severe("Unexpected error occurs: " + e);
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ProxyServer server = new ProxyServer(new InetSocketAddress(8080), 10);

        server.start();

        System.out.println("Yahooo!!! Server has been started!");

//        server.shutdown();
    }
}
