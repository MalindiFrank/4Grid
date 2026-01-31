package za.co.fourgrid.alerts;

/**
 * Abstraction for alert delivery targets (console, ntfy, email, etc.).
 */
public interface AlertSender {
    /**
     * Send an alert message to the underlying target.
     * Implementations should handle their own failures and not throw.
     */
    void send(String message);
}

