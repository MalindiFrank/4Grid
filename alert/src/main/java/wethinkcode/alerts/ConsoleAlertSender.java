package wethinkcode.alerts;

public class ConsoleAlertSender implements AlertSender {
    @Override
    public void send(String message) {

        System.out.println("[ALERT] " + message);
    }
}

