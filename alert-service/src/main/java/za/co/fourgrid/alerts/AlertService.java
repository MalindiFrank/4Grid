// java
package za.co.fourgrid.alerts;

import org.apache.activemq.ActiveMQConnectionFactory;
import za.co.fourgrid.common.mq.MQ;

import javax.jms.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;

/**
 * Simple Alert Service that listens to an MQ queue and delegates alert delivery to AlertSender implementations.
 * Implementations are discovered using ServiceLoader, so adding/removing senders does not require changing this class.
 */
public class AlertService implements MQ, AutoCloseable {

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    // discovered senders
    private final List<AlertSender> senders = new ArrayList<>();

    // No-arg constructor
    public AlertService() {
    }

    public static void main(String[] args) {
        AlertService service = null;
        try {
            service = new AlertService();
            service.start();
            System.out.println("Alert Service started. Listening on queue 'alert'. Press Ctrl+C to exit.");

            AlertService finalService = service;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    finalService.close();
                } catch (Exception ignored) {}
            }));

            // Block until shutdown
            service.awaitShutdown();
        } catch (JMSException ex) {
            System.err.println("Failed to start Alert Service: " + ex.getMessage());
            try {
                if (service != null) service.close();
            } catch (Exception ignored) {}
            System.exit(1);
        }
    }

    public AlertService start() throws JMSException {
        // discover AlertSender implementations via ServiceLoader
        ServiceLoader<AlertSender> loader = ServiceLoader.load(AlertSender.class);
        for (AlertSender s : loader) {
            senders.add(s);
        }
        // ensure there is at least a console sender
        if (senders.isEmpty()) {
            senders.add(new ConsoleAlertSender());
        }

        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MQ.URL);
        connection = factory.createConnection(MQ.USER, MQ.PASSWD);
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = session.createQueue(MQ.ALERT_QUEUE);
        consumer = session.createConsumer(dest);

        consumer.setMessageListener(message -> {
            try {
                if (message instanceof TextMessage) {
                    String text = ((TextMessage) message).getText();
                    // delegate to all discovered senders
                    for (AlertSender s : senders) {
                        try {
                            s.send(text);
                        } catch (Throwable t) {
                            // keep other senders running even if one fails
                            System.err.println("[ALERT] Sender failed: " + t.getMessage());
                        }
                    }
                } else {
                    for (AlertSender s : senders) {
                        try { s.send("Received non-text message"); } catch (Throwable ignored) {}
                    }
                }
            } catch (JMSException ex) {
                for (AlertSender s : senders) {
                    try { s.send("Failed to read message: " + ex.getMessage()); } catch (Throwable ignored) {}
                }
            }
        });
        connection.start();
        return this;
    }

    private void awaitShutdown() {
        try {
            stopLatch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        // signal shutdown
        stopLatch.countDown();
        // close consumer, session and connection in order
        if (consumer != null) {
            try { consumer.close(); } catch (JMSException ignore) {}
            consumer = null;
        }
        if (session != null) {
            try { session.close(); } catch (JMSException ignore) {}
            session = null;
        }
        if (connection != null) {
            try { connection.close(); } catch (JMSException ignore) {}
            connection = null;
        }
    }
}
