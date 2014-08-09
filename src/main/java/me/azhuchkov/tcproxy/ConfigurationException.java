package me.azhuchkov.tcproxy;

/**
 * @author Andrey Zhuchkov
 *         Date: 09.08.14
 */
public class ConfigurationException extends Exception {
    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
