package me.azhuchkov.tcproxy;

import me.azhuchkov.tcproxy.channel.NetworkChannelFactory;
import me.azhuchkov.tcproxy.channel.ServerSocketChannelFactory;
import me.azhuchkov.tcproxy.channel.SocketChannelFactory;
import me.azhuchkov.tcproxy.config.Configuration;
import me.azhuchkov.tcproxy.config.ConfigurationException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
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
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

    /**
     * Server socket channel factory.
     */
    private final NetworkChannelFactory<ServerSocketChannel> serverSocketFactory;

    /**
     * Socket channel factory.
     */
    private final NetworkChannelFactory<SocketChannel> socketFactory;

    /**
     * TCP port mappings.
     */
    private final Collection<PortMapping> mappings;

    /**
     * todo
     */
    private final ConcurrentMap<ServerSocketChannel, PortMapping> channels = new ConcurrentHashMap<>();

    /**
     * Maximum number of pending connections.
     */
    private final int backlog;

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
    private final ConnectionManager connectionManager = new ConnectionManager("Connection Manager");

    /**
     * todo
     *
     * @param mappings
     * @param backlog
     */
    public ProxyServer(Collection<PortMapping> mappings, int backlog) {
        this(ServerSocketChannelFactory.DEFAULT, SocketChannelFactory.DEFAULT, mappings, backlog);
    }

    /**
     * Creates new instance of proxy server.
     *
     * @param serverSocketFactory  Factory for creating server socket channels.
     * @param socketChannelFactory Factory for creating connections to remote servers.
     * @param mappings             Collection of TCP port mappings.
     * @param backlog              Maximum number of pending incoming connections on each listen port.
     *                             If value is 0 or less, OS default value will be used.
     */
    public ProxyServer(NetworkChannelFactory<ServerSocketChannel> serverSocketFactory,
                       NetworkChannelFactory<SocketChannel> socketChannelFactory,
                       Collection<PortMapping> mappings,
                       int backlog) {
        this.serverSocketFactory = serverSocketFactory;
        this.backlog = backlog;
        this.socketFactory = socketChannelFactory;
        this.mappings = mappings;
    }

    /**
     * Starts server: opens channel and do bind.
     *
     * @throws IOException           If failed.
     * @throws IllegalStateException If server already started.
     */
    public void start() throws IOException {
        if (selector != null)
            throw new IllegalStateException("Server already started");

        synchronized (mutex) {
            if (selector != null)
                throw new IllegalStateException("Server already started");

            selector = Selector.open();
        }

        for (PortMapping mapping : mappings) {
            if (mapping.localAddress().isUnresolved() || mapping.remoteAddress().isUnresolved()) {
                LOGGER.warning("Skipped mapping " + mapping + " since it has unresolved address");
                continue;
            }

            ServerSocketChannel channel = serverSocketFactory.newChannel();

            channels.put(channel, mapping);

            channel.configureBlocking(false);

            channel.register(selector, SelectionKey.OP_ACCEPT);

            channel.bind(mapping.localAddress(), backlog);

            LOGGER.info("Start listening on " + mapping.localAddress() + " for " + mapping.remoteAddress());
        }

        connectionManager.start();
    }

    /**
     * todo
     *
     * @throws IOException
     */
    public void shutdown() throws IOException {
        Selector selector0 = selector;

        if (selector0 == null || !selector0.isOpen())
            throw new IllegalStateException("Server is not started");

        synchronized (mutex) {
            if (!selector0.isOpen())
                throw new IllegalStateException("Server is not started");

            selector0.close();
        }

        connectionManager.interrupt();
    }

    /**
     * todo
     *
     * @return
     */
    public Collection<PortMapping> mappings() {
        return Collections.unmodifiableCollection(mappings);
    }

    private void onAccept(SelectionKey key) throws IOException {
        ServerSocketChannel originateChannel = (ServerSocketChannel) key.channel();
        SocketChannel acceptedChannel = originateChannel.accept();
        SocketChannel mappedChannel = socketFactory.newChannel();

        acceptedChannel.configureBlocking(false);
        acceptedChannel.register(selector, SelectionKey.OP_READ, mappedChannel);

        mappedChannel.configureBlocking(false);
        mappedChannel.register(
                selector,
                SelectionKey.OP_CONNECT | SelectionKey.OP_READ,
                acceptedChannel);

        InetSocketAddress remote = channels.get(originateChannel).remoteAddress();

        mappedChannel.connect(remote);

        LOGGER.fine("Incoming connection from: " + acceptedChannel.getRemoteAddress());
    }

    private void onConnect(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        if (!socketChannel.finishConnect()) {
            LOGGER.severe("Failed to connect to remote destination. " +
                    "Closing originating connection.");
            ((SocketChannel) key.attachment()).close();
        } else
            LOGGER.fine("Established connection with remote: " + socketChannel.getRemoteAddress());
    }

    private void onDataAvailable(SelectionKey key) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(32);

        SocketChannel originateChannel = (SocketChannel) key.channel();
        SocketChannel pipedChannel = (SocketChannel) key.attachment();

        if (pipedChannel.isConnectionPending())
            return;

        int read = originateChannel.read(buf);

        if (read == -1) {
            LOGGER.fine("Connection with " + originateChannel.getRemoteAddress() + " has been closed");
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

    public static void main(String[] args) {
        final Logger logger = Logger.getLogger("");

        String configUrl0 = System.getProperty("tcproxy.config.url", "classpath:/proxy.properties");

        URL configUrl = null;

        try {
            configUrl = configUrl0.startsWith("classpath:") ?
                    ProxyServer.class.getResource(configUrl0.substring(10)) :
                    new URL(configUrl0);

            if (configUrl == null)
                throw new MalformedURLException("Couldn't find " + configUrl0);
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, "Failed to resolve configuration URL address", e);

            System.exit(1);
        }

        Configuration config = null;

        try {
            config = Configuration.parse(configUrl);
        } catch (ConfigurationException | IOException e) {
            final String message = e instanceof IOException ?
                    "Failed to read configuration" :
                    "Configuration error";

            logger.log(Level.SEVERE, message, e);

            System.exit(1);
        }

        if (config.mappings().isEmpty()) {
            logger.severe("There is no TCP mappings have been configured. Please add something.");

            System.exit(1);
        }

        ProxyServer server =
                new ProxyServer(config.mappings(), Integer.getInteger("tcproxy.accept.backlog", -1));

        logger.info("Starting TCP proxy server...");

        try {
            server.start();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);

            System.exit(2);
        }

        logger.info("The server has been started successfully");

//        server.shutdown();
    }
}
