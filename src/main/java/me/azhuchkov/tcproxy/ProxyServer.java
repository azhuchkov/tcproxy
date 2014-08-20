package me.azhuchkov.tcproxy;

import me.azhuchkov.tcproxy.acceptor.Acceptor;
import me.azhuchkov.tcproxy.acceptor.BlockingAcceptor;
import me.azhuchkov.tcproxy.acceptor.ConnectionHandler;
import me.azhuchkov.tcproxy.acceptor.NonBlockingAcceptor;
import me.azhuchkov.tcproxy.channel.NetworkChannelFactory;
import me.azhuchkov.tcproxy.channel.ServerSocketChannelFactory;
import me.azhuchkov.tcproxy.channel.SocketChannelFactory;
import me.azhuchkov.tcproxy.config.Configuration;
import me.azhuchkov.tcproxy.config.ConfigurationException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Non-blocking TCP proxy server.
 * Consist of single acceptor for handling incoming connections and several workers
 * that do most of the job. Acceptor may be blocking or non-blocking,
 * workers are always in non-blocking mode. By default workers count is equal to available
 * CPU cores amount.
 *
 * @author Andrey Zhuchkov
 *         Date: 08.08.14
 */
public class ProxyServer {
    /**
     * Server logger.
     */
    private static final Logger LOGGER = Logger.getLogger(ProxyServer.class.getName());

    /**
     * Default backlog value.
     */
    public static final int DEFAULT_BACKLOG = -1;

    /**
     * Default size of transfer buffer.
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Default workers count.
     */
    public static final int DEFAULT_WORKERS_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Server socket channel factory.
     */
    private final NetworkChannelFactory<ServerSocketChannel> serverSocketFactory;

    /**
     * Socket channel factory.
     */
    private final NetworkChannelFactory<SocketChannel> socketFactory;

    /**
     * Maximum number of pending incoming connections.
     */
    private final int backlog;

    /**
     * Transfer buffer size.
     */
    private final int bufferSize;

    /**
     * Incoming connections acceptor.
     */
    private final Acceptor acceptor;

    /**
     * Workers that serves connection events.
     */
    private final Worker[] workers;

    /**
     * TCP port mappings by its channel.
     */
    private volatile Map<ServerSocketChannel, PortMapping> mappings;

    /**
     * Creates new server with default backlog value, default socket options, buffer size 8192,
     * non-blocking acceptor and amount of workers that equal to available CPU cores.
     */
    public ProxyServer() {
        this(ServerSocketChannelFactory.DEFAULT, SocketChannelFactory.DEFAULT, DEFAULT_BACKLOG,
                DEFAULT_BUFFER_SIZE, DEFAULT_WORKERS_COUNT, false);
    }

    /**
     * Creates new instance of proxy server.
     *
     * @param serverSocketFactory  Factory for creating server socket channels.
     * @param socketChannelFactory Factory for creating connections to remote servers.
     * @param backlog              Maximum number of pending incoming connections on each listen port.
     *                             If value is 0 or less, OS default value will be used.
     * @param bufferSize           Transfer buffer size.
     * @param workers              Count of workers.
     * @param blockingAcceptor     Whether blocking I/O acceptor should be used.
     */
    public ProxyServer(NetworkChannelFactory<ServerSocketChannel> serverSocketFactory,
                       NetworkChannelFactory<SocketChannel> socketChannelFactory,
                       int backlog,
                       int bufferSize,
                       int workers,
                       boolean blockingAcceptor) {
        if (bufferSize <= 0)
            throw new IllegalArgumentException("invalid buffer size");

        if (workers <= 0)
            throw new IllegalArgumentException("invalid workers count");

        this.serverSocketFactory = serverSocketFactory;
        this.backlog = backlog;
        this.bufferSize = bufferSize;
        this.socketFactory = socketChannelFactory;

        this.workers = new Worker[workers];

        for (int i = 0; i < this.workers.length; i++) {
            this.workers[i] = new Worker("Proxy TCP Dispatcher-" + i);
        }

        ConnectionHandler handler = new ConnectionHandler() {
            @Override
            public void handle(ServerSocketChannel originateChannel, SocketChannel acceptedChannel) {
                onAccept(originateChannel, acceptedChannel);
            }
        };

        // 'this' leakage is safe since acceptor is private
        this.acceptor = blockingAcceptor ?
                new BlockingAcceptor("Proxy TCP Acceptor-", handler) :
                new NonBlockingAcceptor("Proxy TCP Acceptor", handler);
    }

    /**
     * Starts server.
     *
     * @param portMappings Collection of mappings.
     * @throws IOException           If failed.
     * @throws IllegalStateException If server already started.
     */
    public void start(Collection<PortMapping> portMappings) throws IOException {
        if (mappings != null)
            throw new IllegalStateException("already started");

        synchronized (this) {
            if (mappings != null)
                throw new IllegalStateException("already started");

            mappings = new HashMap<>(portMappings.size());
        }

        for (PortMapping mapping : portMappings) {
            if (mapping.remoteAddress().isUnresolved()) {
                LOGGER.warning("Skipped mapping " + mapping + " since it has unresolved address");
                continue;
            }

            ServerSocketChannel channel = serverSocketFactory.newChannel();

            try {
                channel.bind(mapping.localAddress(), backlog);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to bind: " + mapping.localAddress(), e);

                continue;
            }

            LOGGER.info("Start listening on " + mapping.localAddress() + " mapped to " + mapping.remoteAddress());

            mappings.put(channel, mapping);
        }

        for (Worker worker : workers)
            worker.start();

        try {
            for (Worker worker : workers)
                worker.initLatch.await();
        } catch (InterruptedException e) {
            for (Worker worker : workers)
                worker.interrupt();

            return;
        }

        acceptor.start(mappings.keySet());
    }

    /**
     * Shutdowns the server.
     *
     * @throws InterruptedException  If shutdown process is interrupted.
     * @throws IllegalStateException If server is not started.
     */
    public void shutdown() throws InterruptedException {
        if (mappings == null)
            throw new IllegalStateException("not started");

        Collection<ServerSocketChannel> channels;

        synchronized (this) {
            if (mappings == null)
                throw new IllegalStateException("not started");

            channels = mappings.keySet();

            mappings = null;
        }

        try {
            acceptor.interrupt();
        } finally {
            for (ServerSocketChannel channel : channels)
                close(channel);

            for (Worker worker : workers)
                worker.interrupt();

            for (Worker worker : workers)
                worker.join();
        }
    }

    /**
     * Handles incoming connections.
     *
     * @param originateChannel Channel that accepted new connection.
     * @param channel          Accepted connection channel.
     */
    private void onAccept(ServerSocketChannel originateChannel, SocketChannel channel) {
        SocketChannel mappedChannel = null;

        try {
            socketFactory.apply(channel);
            channel.configureBlocking(false);

            mappedChannel = socketFactory.newChannel();
            mappedChannel.configureBlocking(false);

            mappedChannel.connect(mappings.get(originateChannel).remoteAddress());
        } catch (IOException e) {
            LOGGER.warning("Failed to handle incoming connection. Closing it... (" + e + ")");

            close(channel);

            if (mappedChannel != null)
                close(mappedChannel);

            return;
        }

        Session originateSession = new Session(channel);
        Session mappedSession = new Session(mappedChannel);

        originateSession.link(mappedSession);

        Worker worker = workers[ThreadLocalRandom.current().nextInt(workers.length)];

        worker.register(channel, originateSession, mappedChannel, mappedSession);
    }

    /**
     * Handles outgoing connection establishment.
     *
     * @param key Selection key.
     * @throws IOException If I/O error occurs.
     */
    private void onConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();

        if (channel.finishConnect())
            key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT | SelectionKey.OP_READ);
    }

    /**
     * Handles received data. This method also invoked on disconnects.
     *
     * @param key Selection key.
     * @throws IOException If I/O error occurs.
     */
    private void onRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Session linked = ((Session) key.attachment()).linked;

        if (linked.pending != null)
            throw new RuntimeException("pending data must be flushed");

        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);

        int read = channel.read(buffer);

        buffer.flip();

        if (read == 0)
            return;

        if (read < 0) {
            close(channel);
            close(linked.channel);

            return;
        }

        if (linked.channel.isConnected())
            linked.channel.write(buffer);

        if (buffer.hasRemaining()) {
            linked.pending = buffer;

            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

            SelectionKey linkedKey = linked.channel.keyFor(key.selector());

            linkedKey.interestOps(linkedKey.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    /**
     * Handles channel write readiness.
     *
     * @param key Selection key.
     * @throws IOException If I/O error occurs.
     */
    private void onWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Session session = ((Session) key.attachment());

        if (session.pending == null)
            throw new RuntimeException("expected pending data");

        channel.write(session.pending);

        if (session.pending.hasRemaining())
            return;

        session.pending = null;

        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

        SelectionKey linkedKey = session.linked.channel.keyFor(key.selector());

        linkedKey.interestOps(linkedKey.interestOps() | SelectionKey.OP_READ);
    }

    /**
     * Worker.
     */
    private class Worker extends Thread {
        private final Queue<Registration> pending = new ConcurrentLinkedQueue<>();

        private final CountDownLatch initLatch = new CountDownLatch(1);

        private final AtomicBoolean awakened = new AtomicBoolean(false);

        private volatile Selector selector;

        Worker(String name) {
            super(name);
        }

        private class Registration {
            final SocketChannel channel1;
            final Session session1;

            final SocketChannel channel2;
            final Session session2;

            private Registration(SocketChannel channel1, Session session1,
                                 SocketChannel channel2, Session session2) {
                this.channel1 = channel1;
                this.session1 = session1;
                this.channel2 = channel2;
                this.session2 = session2;
            }
        }

        void register(SocketChannel channel1, Session session1, SocketChannel channel2, Session session2) {
            pending.add(new Registration(channel1, session1, channel2, session2));

            // it seems that wakeup() performs quite slowly
            if (awakened.compareAndSet(false, true))
                selector.wakeup();
        }

        @Override
        public void run() {
            try {
                selector = Selector.open();

                initLatch.countDown();

                while (!isInterrupted()) {
                    awakened.set(false);

                    selector.select();

                    if (isInterrupted())
                        break;

                    for (Iterator<SelectionKey> iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                        final SelectionKey key = iter.next();

                        iter.remove();

                        try {
                            if (key.isValid() && key.isConnectable()) {
                                onConnect(key);
                            }

                            if (key.isValid() && key.isReadable()) {
                                onRead(key);
                            }

                            if (key.isValid() && key.isWritable()) {
                                onWrite(key);
                            }
                        } catch (IOException e) {
                            LOGGER.warning("Failed to handle I/O event: " + e);

                            Session session = (Session) key.attachment();

                            close(session.channel);
                            close(session.linked.channel);
                        }
                    }

                    Registration registration;

                    while ((registration = pending.poll()) != null) {
                        SocketChannel channel1 = registration.channel1;

                        channel1.register(
                                selector,
                                channel1.isConnected() ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT,
                                registration.session1
                        );

                        SocketChannel channel2 = registration.channel2;

                        channel2.register(
                                selector,
                                channel2.isConnected() ? SelectionKey.OP_READ : SelectionKey.OP_CONNECT,
                                registration.session2
                        );
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unexpected I/O error occurs", e);
            } finally {
                if (selector != null) {
                    for (SelectionKey key : selector.keys())
                        close(key.channel());

                    try {
                        selector.close();
                    } catch (IOException e) {
                        LOGGER.warning("Failed to close selector: " + e);
                    }
                }
            }
        }
    }

    /**
     * Channel session object. Correctness provided by passing it to worker through
     * concurrent queue and further handling in single thread.
     */
    private static class Session {
        private final SocketChannel channel;
        private ByteBuffer pending;

        private Session linked;

        private Session(SocketChannel channel) {
            this.channel = channel;
        }

        public void link(Session session) {
            linked = session;
            session.linked = this;
        }
    }

    /**
     * Closes channel suppressing I/O error if any. Error would be reported in log.
     *
     * @param channel Channel to close.
     */
    private static void close(Channel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            LOGGER.warning("Failed to close channel: " + e);
        }
    }

    /**
     * Entry point.
     *
     * @param args Command line arguments.
     */
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

        NetworkChannelFactory.Builder<ServerSocketChannelFactory> serverFactoryBuilder =
                ServerSocketChannelFactory.create();

        String reuseAddr = System.getProperty("tcproxy.accept.reuseAddress");

        if (reuseAddr != null)
            serverFactoryBuilder.option(StandardSocketOptions.SO_REUSEADDR, "true".equals(reuseAddr));

        NetworkChannelFactory.Builder<SocketChannelFactory> socketFactoryBuilder =
                SocketChannelFactory.create();

        String sendBufSize = System.getProperty("tcproxy.conn.sendBuf");

        if (sendBufSize != null)
            socketFactoryBuilder.option(StandardSocketOptions.SO_SNDBUF, Integer.valueOf(sendBufSize));

        String rcvBufSize = System.getProperty("tcproxy.conn.receiveBuf");

        if (rcvBufSize != null)
            socketFactoryBuilder.option(StandardSocketOptions.SO_RCVBUF, Integer.valueOf(rcvBufSize));

        socketFactoryBuilder
                .option(StandardSocketOptions.TCP_NODELAY, Boolean.getBoolean("tcproxy.conn.noDelay"))
                .option(StandardSocketOptions.SO_KEEPALIVE, Boolean.getBoolean("tcproxy.conn.keepAlive"));

        final ProxyServer server = new ProxyServer(
                serverFactoryBuilder.build(),
                socketFactoryBuilder.build(),
                Integer.getInteger("tcproxy.accept.backlog", DEFAULT_BACKLOG),
                Integer.getInteger("tcproxy.conn.transferBuf", DEFAULT_BUFFER_SIZE),
                Integer.getInteger("tcproxy.workers", DEFAULT_WORKERS_COUNT),
                Boolean.getBoolean("tcproxy.accept.blocking")
        );

        logger.info("Starting TCP proxy server...");

        try {
            server.start(config.mappings());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to start server", e);

            System.exit(2);
        }

        logger.info("The server has been started successfully");

        Runtime.getRuntime().addShutdownHook(new Thread("Proxy Shutdown Hook") {
            @Override
            public void run() {
                logger.info("Shutting down the server...");

                try {
                    server.shutdown();
                } catch (InterruptedException e) {
                    LOGGER.severe("Shutdown process has been interrupted");
                }
            }
        });
    }
}
