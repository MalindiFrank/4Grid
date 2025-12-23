package wethinkcode.loadshed.common.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import javax.jms.IllegalStateException;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service.
 */
public class MqTopicSender implements MQ
{
    private Connection connection;
    private Session session;
    private String topicName;

    private void closeResources(){
        try{
            if( session != null ) session.close();
            if( connection != null ) connection.close();
        }catch( JMSException ex ){
            // wut?
        }
        session = null;
        connection = null;
    }

    // Accept DestinationType enum
    public MqTopicSender init(DestinationType destinationType) throws JMSException {
        // Use vm transport for tests, tcp for production
        String url = "vm://localhost?broker.persistent=false";

        // Convert enum to actual topic name
        this.topicName = (destinationType == DestinationType.TOPIC) ? STAGE_TOPIC : TEST_QUEUE;

        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        connection = factory.createConnection(USER, PASSWD);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return this;
    }

    // Keep String overload for backwards compatibility if needed by lms
    public MqTopicSender init(String brokerUrl) throws JMSException {
        String url = "TEST".equals(brokerUrl) ? "vm://localhost?broker.persistent=false" : brokerUrl;
        this.topicName = STAGE_TOPIC; // default

        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        connection = factory.createConnection(USER, PASSWD);
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        return this;
    }

    public void send(String message) throws JMSException {
        if (session == null) {
            throw new IllegalStateException("Session not initialized. Call init() first.");
        }
        Destination destination = session.createTopic(topicName != null ? topicName : STAGE_TOPIC);
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        TextMessage textMessage = session.createTextMessage(message);
        producer.send(textMessage);
        System.out.println("Sent topic message: " + textMessage.getText());
        producer.close();
    }

    public void close() {
        closeResources();
    }
}