package me.azhuchkov.tcproxy;

import java.net.InetSocketAddress;

/**
 * Mapping between local bind address and remote socket.
 *
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 */
public final class PortMapping {
    /**
     * Mapping title.
     */
    private final String title;

    /**
     * Local address of the mapping.
     */
    private final InetSocketAddress localAddress;

    /**
     * Remote address of the mapping.
     */
    private final InetSocketAddress remoteAddress;

    /**
     * Creates new port mapping.
     *
     * @param title         Mapping title.
     * @param localAddress  Local address to bind.
     * @param remoteAddress Remote address to forward incoming traffic.
     * @throws java.lang.NullPointerException if one of the arguments is {@code null}.
     */
    public PortMapping(String title, InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
        if (title == null || localAddress == null || remoteAddress == null)
            throw new NullPointerException();

        this.title = title;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    /**
     * @return Mapping title.
     */
    public String title() {
        return title;
    }

    /**
     * @return Local mapping address.
     */
    public InetSocketAddress localAddress() {
        return localAddress;
    }

    /**
     * @return Remote address of the mapping.
     */
    public InetSocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public String toString() {
        return "PortMapping{" +
                "title='" + title + '\'' +
                ", localAddress=" + localAddress +
                ", remoteAddress=" + remoteAddress +
                '}';
    }
}
