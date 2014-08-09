package me.azhuchkov.tcproxy.channel;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;

/**
 * Server socket channel factory.
 *
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 * @see java.nio.channels.ServerSocketChannel
 */
public final class ServerSocketChannelFactory extends NetworkChannelFactory<ServerSocketChannel> {
    /**
     * Instance of factory with default socket options.
     */
    public static final ServerSocketChannelFactory DEFAULT = new ServerSocketChannelFactory();

    /**
     * Use {@link #create()} factory method instead.
     */
    private ServerSocketChannelFactory() {
        super(new HashMap<SocketOption<Object>, Object>());
    }

    /**
     * Creates new factory builder.
     *
     * @return Factory builder instance.
     */
    public static Builder<ServerSocketChannelFactory> create() {
        return new Builder<>(new ServerSocketChannelFactory());
    }

    @Override
    protected ServerSocketChannel newChannel0() throws IOException {
        return ServerSocketChannel.open();
    }
}
