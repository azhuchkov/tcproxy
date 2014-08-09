package me.azhuchkov.tcproxy.channel;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.util.Map;

/**
 * Abstract network socket channel factory.
 *
 * @author Andrey Zhuchkov
 *         Date: 08.08.14
 */
public abstract class NetworkChannelFactory<T extends NetworkChannel> {
    /**
     * Socket channel options.
     */
    protected final Map<SocketOption<Object>, Object> options;

    /**
     * Constructor that allows subclasses to pass predefined channel options
     * using specific {@link java.util.Map} implementation.
     *
     * @param options Map with predefined channel options (may be empty).
     */
    protected NetworkChannelFactory(Map<SocketOption<Object>, Object> options) {
        this.options = options;
    }

    /**
     * Instantiates appropriate socket channel instance.
     *
     * @return New channel instance.
     * @throws IOException If failed.
     */
    protected abstract T newChannel0() throws IOException;

    /**
     * Creates new channel with given options.
     *
     * @return Network channel.
     * @throws IOException If failed.
     */
    public T newChannel() throws IOException {
        T channel = newChannel0();

        for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
            channel.setOption(entry.getKey(), entry.getValue());
        }

        return channel;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '{' +
                "options=" + options +
                '}';
    }

    /**
     * Factory builder. Should be used in subclasses to provided convenient way to pass socket options.
     */
    public static class Builder<T extends NetworkChannelFactory> {
        private final T factory;

        protected Builder(T factory) {
            this.factory = factory;
        }

        /**
         * Sets option for socket factory.
         *
         * @param name  Option name.
         * @param value Option value.
         * @param <V>   Option value type.
         * @return {@code this} builder.
         */
        @SuppressWarnings("unchecked")
        public <V> Builder<T> option(SocketOption<V> name, V value) {
            factory.options.put(name, value);
            return this;
        }

        /**
         * Finishes factory building.
         *
         * @return Socket factory instance.
         */
        public T build() {
            return factory;
        }
    }
}
