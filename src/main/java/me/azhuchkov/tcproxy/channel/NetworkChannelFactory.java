package me.azhuchkov.tcproxy.channel;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.NetworkChannel;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Abstract network socket channel factory.
 *
 * @author Andrey Zhuchkov
 *         Date: 08.08.14
 */
public abstract class NetworkChannelFactory<T extends NetworkChannel> {
    /**
     * Factory logger.
     */
    private final Logger logger = Logger.getLogger(getClass().getName());

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
     * Creates new channel with given options. Invalid and unsupported options will be ignored
     * and warning message would be logged.
     *
     * @return Network channel.
     * @throws IOException If an I/O error occurs.
     */
    public T newChannel() throws IOException {
        T channel = newChannel0();

        try {
            apply(channel);
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException e1) {
                logger.warning("Failed to close channel: " + e);
            }
            throw e;
        }

        return channel;
    }

    /**
     * Applies factory options to given channel. Invalid and unsupported options will be ignored
     * and warning message would be logged.
     *
     * @param channel Network channel to apply options to.
     * @throws IOException If I/O error occurs.
     */
    public void apply(T channel) throws IOException {
        for (Map.Entry<SocketOption<Object>, Object> entry : options.entrySet()) {
            SocketOption<Object> option = entry.getKey();
            Object value = entry.getValue();

            try {
                channel.setOption(option, value);
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                logger.warning(e instanceof IllegalArgumentException ?
                        "Invalid value " + value + " for option " + option + ": " + e.getMessage() :
                        "Unsupported option " + option + ": " + e.getMessage());
            }
        }
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
