package me.azhuchkov.tcproxy.config;

import me.azhuchkov.tcproxy.PortMapping;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCP Mapper configuration. It contains mappings between local ports and remote endpoints.
 *
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 */
public final class Configuration {
    /**
     * TCP port mappings.
     */
    private final Collection<PortMapping> mappings;

    /**
     * For internal usage only. Use factory-methods instead.
     *
     * @param mappings Collection of port mappings.
     */
    private Configuration(Collection<PortMapping> mappings) {
        this.mappings = mappings;
    }

    /**
     * Returns configuration mappings.
     *
     * @return Unmodifiable view of mappings.
     */
    public Collection<PortMapping> mappings() {
        return Collections.unmodifiableCollection(mappings);
    }

    /**
     * Parses properties file located at the given URL address.
     * Uses the same rules as {@link #parse(java.io.InputStream)}.
     *
     * @param url Configuration file address.
     * @return Configuration object according to properties file.
     * @throws IOException            In case of reading I/O failure.
     * @throws ConfigurationException If configuration contains errors.
     * @see java.util.Properties
     */
    public static Configuration parse(URL url) throws IOException, ConfigurationException {
        try (InputStream in = url.openStream()) {
            return parse(in);
        }
    }

    /**
     * Parses properties configuration from the given stream. Searches for options that describes
     * local ports to bind and then finds the rest of configuration for them. Other lines would
     * be ignored.
     *
     * @param input Configuration input stream. The stream contains data in properties format.
     * @return Configuration object according to properties file.
     * @throws IOException            In case of reading I/O failure.
     * @throws ConfigurationException If configuration contains errors.
     * @see java.util.Properties
     */
    public static Configuration parse(InputStream input) throws IOException, ConfigurationException {
        Properties properties = new Properties();

        properties.load(input);

        final Pattern localPortPattern = Pattern.compile("(?<title>.*)\\.localPort");

        Collection<PortMapping> result = new TreeSet<>(new Comparator<PortMapping>() {
            @Override
            public int compare(PortMapping o1, PortMapping o2) {
                Integer port1 = o1.localAddress().getPort();
                Integer port2 = o2.localAddress().getPort();

                return port1.compareTo(port2);
            }
        });

        for (String name : properties.stringPropertyNames()) {
            Matcher matcher = localPortPattern.matcher(name);

            if (!matcher.matches())
                continue;

            int localPort = parsePort(properties.getProperty(name));

            String title = matcher.group("title");

            String remoteHost = properties.getProperty(title + ".remoteHost");

            if (remoteHost == null)
                throw new ConfigurationException("Remote host must be set for mapping: " + title);

            String remotePort0 = properties.getProperty(title + ".remotePort");

            if (remotePort0 == null)
                throw new ConfigurationException("Remote port must be set for mapping: " + title);

            int remotePort = parsePort(remotePort0);

            PortMapping mapping = new PortMapping(title, new InetSocketAddress(localPort),
                    new InetSocketAddress(remoteHost, remotePort));

            if (!result.add(mapping))
                throw new ConfigurationException("Duplicated listening port in configuration: " +
                        mapping.localAddress().getPort());
        }

        return new Configuration(result);
    }

    /**
     * Parses string and verifies that given number is valid port number.
     *
     * @param port Port number as a string.
     * @return Parsed port number.
     * @throws ConfigurationException If value is invalid (Not a number or outside of range [0..65535]).
     */
    private static int parsePort(String port) throws ConfigurationException {
        Integer remotePort;

        try {
            remotePort = Integer.valueOf(port);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid port value: " + port, e);
        }

        if (remotePort < 0 || remotePort > 65535)
            throw new ConfigurationException("Invalid port value: " + remotePort);

        return remotePort;
    }
}
