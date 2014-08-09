package me.azhuchkov.tcproxy.channel;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.SocketChannel;
import java.util.HashMap;

/**
 * Socket channel factory.
 *
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 * @see java.nio.channels.SocketChannel
 */
public final class SocketChannelFactory extends NetworkChannelFactory<SocketChannel> {
    /**
     * Instance of factory with default socket options.
     */
    public static final SocketChannelFactory DEFAULT = new SocketChannelFactory();

    /**
     * Use {@link #create()} factory method instead.
     */
    private SocketChannelFactory() {
        super(new HashMap<SocketOption<Object>, Object>());
    }

    /**
     * Creates new factory builder.
     *
     * @return Factory builder instance.
     */
    public static Builder<SocketChannelFactory> create() {
        return new Builder<>(new SocketChannelFactory());
    }

    @Override
    protected SocketChannel newChannel0() throws IOException {
        return SocketChannel.open();
    }
}
