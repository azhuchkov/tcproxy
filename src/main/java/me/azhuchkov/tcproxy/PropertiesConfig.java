package me.azhuchkov.tcproxy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 */
public class PropertiesConfig {
    public static final Pattern LOCAL_BIND_PATTERN = Pattern.compile("(?<title>.*)\\.localPort");

    private final Collection<PortMapping> mappings;

    private PropertiesConfig(Collection<PortMapping> mappings) {
        this.mappings = mappings;
    }

    public Collection<PortMapping> mappings() {
        return Collections.unmodifiableCollection(mappings);
    }

    public static PropertiesConfig parse(URL url) throws IOException, ConfigurationException {
        try (InputStream in = url.openStream()) {
            return parse(in);
        }
    }

    public static PropertiesConfig parse(InputStream input) throws IOException, ConfigurationException {
        Properties properties = new Properties();

        properties.load(input);

        Collection<PortMapping> result = new ArrayList<>();

        for (String name : properties.stringPropertyNames()) {
            Matcher matcher = LOCAL_BIND_PATTERN.matcher(name);

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

            result.add(
                    new PortMapping(
                            title,
                            new InetSocketAddress(localPort),
                            new InetSocketAddress(remoteHost, remotePort)
                    )
            );
        }

        return new PropertiesConfig(result);
    }

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
