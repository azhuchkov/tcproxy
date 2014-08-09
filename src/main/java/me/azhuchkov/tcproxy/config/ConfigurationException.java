package me.azhuchkov.tcproxy.config;

/**
 * This exception indicates error in TCP mapper configuration, e.g. properties file, manual configuration, etc.
 *
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 */
public class ConfigurationException extends Exception {
    /**
     * Constructs new exception with given message.
     *
     * @param message Message that describes configuration error.
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with given error description and its cause.
     *
     * @param message Error message.
     * @param cause   Error cause.
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
