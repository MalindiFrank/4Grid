package za.co.fourgrid.alerts;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import java.nio.charset.StandardCharsets;

public class NtfyAlertSender implements AlertSender {
    private final String topic;

    public NtfyAlertSender(String topic) {
        this.topic = topic;
        // Optional global timeouts
        Unirest.config().connectTimeout(5000).socketTimeout(5000);
    }

    // Public no-arg constructor required by ServiceLoader; default to topic "alert"
    public NtfyAlertSender() {
        this("alert");
    }

    @Override
    public void send(String message) {
        if (topic == null || topic.isEmpty()) return;

        try {
            HttpResponse<?> response = Unirest.post("https://ntfy.sh/" + topic)
                    .header("User-Agent", "AlertService/ntfy-client")
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .header("Priority", "high")
                    .body(message.getBytes(StandardCharsets.UTF_8))
                    .asEmpty();

            int code = response.getStatus();
            if (code < 200 || code >= 300) {
                System.err.println("[ALERT] Failed to deliver to ntfy (status=" + code + ")");
            }

        } catch (Exception e) {
            System.err.println("[ALERT] Exception sending to ntfy: " + e.getMessage());
        }
    }
}
