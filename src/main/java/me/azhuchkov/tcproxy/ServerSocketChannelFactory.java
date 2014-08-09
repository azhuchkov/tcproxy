package me.azhuchkov.tcproxy;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.ServerSocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Server socket channel factory.
 *
 * @author Andrey Zhuchkov
 *         Date: 08.08.14
 */
public class ServerSocketChannelFactory {
    /**
     * Instance of factory with default options.
     */
    public static final ServerSocketChannelFactory DEFAULT = new ServerSocketChannelFactory();

    /**
     * Socket channel options.
     */
    private final Map<SocketOption<Object>, Object> options = new HashMap<>();

    /**
     * Use {@link #create()} factory method instead.
     */
    private ServerSocketChannelFactory() {
        // No-op.
    }

    /**
     * Creates new factory builder.
     *
     * @return New builder.
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Creates new server socket channel with default options.
     *
     * @return Server socket channel.
     * @throws IOException If failed.
     */
    public ServerSocketChannel newChannel() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();

        for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
            channel.setOption(entry.getKey(), entry.getValue());
        }

        return channel;
    }

    @Override
    public String toString() {
        return "ServerSocketChannelFactory{" +
                "options=" + options +
                '}';
    }

    /**
     * Factory builder.
     */
    public static class Builder {
        private final ServerSocketChannelFactory factory;

        private Builder() {
            factory = new ServerSocketChannelFactory();
        }

        /**
         * Sets option for socket factory.
         *
         * @param name  Option name.
         * @param value Option value.
         * @param <T>   Option value type.
         * @return {@code this} builder.
         */
        @SuppressWarnings("unchecked")
        public <T> Builder option(SocketOption<T> name, T value) {
            factory.options.put((SocketOption<Object>) name, value);
            return this;
        }

        /**
         * Finishes factory building.
         *
         * @return Socket factory instance.
         */
        public ServerSocketChannelFactory build() {
            return factory;
        }
    }
}
