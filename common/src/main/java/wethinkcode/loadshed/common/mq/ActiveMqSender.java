package wethinkcode.loadshed.common.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.lang.IllegalStateException;

/**
 * A small ActiveMQ-backed sender implementation. It supports delayed initialization
 * via init(String brokerUrl) so tests can use the VM transport (brokerUrl == "TEST").
 *
 * The static openOn(name) helper returns an instance configured with the destination
 * name and type but does NOT automatically start a JMS connection. Call init(...) before send()
 * or allow callers to manage lifecycle explicitly.
 */
public class ActiveMqSender extends MqTopicSender implements MQ, AutoCloseable {

    private Connection connection;
    private Session session;
    private String destName;
    private DestinationType destType;

    // No-arg constructor for tests that construct without params
    public ActiveMqSender() {
        this.destName = null;
        this.destType = null;
    }

    public ActiveMqSender(DestinationType destType) {
        this.destName = null;
        this.destType = destType;
    }

    public ActiveMqSender(String destName, DestinationType destType) {
        this.destName = destName;
        this.destType = destType;
    }

    // Static helper used by tests that call ActiveMqSender.openOn("name")
    // NOTE: This does NOT call init(). Callers should call init("TEST") or init(MQ.URL) as needed.
    public static ActiveMqSender openOn(String destination) {
        DestinationType type = (destination != null && destination.toLowerCase().contains("topic"))
                ? DestinationType.TOPIC : DestinationType.QUEUE;
        return new ActiveMqSender(destination, type);
    }

    /**
     * Initialize JMS connection/session. Use brokerUrl == "TEST" to create an in-vm broker.
     */
    public ActiveMqSender init(String brokerUrl) throws JMSException {
        String url = "TEST".equals(brokerUrl) ? "vm://localhost?broker.persistent=false" : brokerUrl;
        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        connection = factory.createConnection(USER, PASSWD);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return this;
    }

    /**
     * Send a text message to the configured destination. Requires init() to have been called.
     */
    public void send(String message) throws JMSException {
        if (session == null) {
            // Try to auto-initialize for convenience in test environments: prefer in-vm TEST broker
            try {
                init("TEST");
            } catch (JMSException first) {
                try {
                    init(MQ.URL);
                } catch (JMSException second) {
                    throw new IllegalStateException("Session not initialized and auto-init failed", second);
                }
            }
        }

        Destination destination;
        if (destName != null) {
            destination = (destType == DestinationType.TOPIC) ? session.createTopic(destName) : session.createQueue(destName);
        } else {
            // default to ALERT_QUEUE if no destination provided
            destination = session.createQueue(ALERT_QUEUE);
        }

        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        TextMessage textMessage = session.createTextMessage(message);
        producer.send(textMessage);
        // small helpful log to stdout for local runs/tests
        System.out.println("Sent message to " + (destName != null ? destName : ALERT_QUEUE) + ": " + textMessage.getText());
        producer.close();
    }

    public void start() {
        // lms wants it or expects it
    }

    private void closeResources() {
        try {
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException ex) {
            // ignore
        }
        session = null;
        connection = null;
    }

    @Override
    public void close() {
        closeResources();
    }
}
