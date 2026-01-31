package za.co.fourgrid.common.mq;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service by
 * subscribing to a Topic.
 */
public class MqTopicReceiver implements MQ
{
    private Connection connection;

    private void closeConnection(){
        if( connection != null ) try{
            connection.close();
        }catch( JMSException ex ){
            // meh
        }
    }

    // Accept DestinationType enum
    public MqTopicReceiver init(DestinationType destinationType, MessageListener listener) throws JMSException {
        String url = "vm://localhost?broker.persistent=false";

        // Convert enum to actual topic name
        String topicName = (destinationType == DestinationType.TOPIC) ? STAGE_TOPIC : TEST_QUEUE;

        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        connection = factory.createConnection(MQ.USER, MQ.PASSWD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = session.createTopic(topicName);
        MessageConsumer receiver = session.createConsumer(dest);
        receiver.setMessageListener(listener);
        connection.start();
        return this;
    }

    // Keep String overload for backwards compatibility if needed by lms
    public MqTopicReceiver init(String brokerUrl, MessageListener listener) throws JMSException {
        String url = "TEST".equals(brokerUrl) ? "vm://localhost?broker.persistent=false" : brokerUrl;
        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        connection = factory.createConnection(MQ.USER, MQ.PASSWD);
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination dest = session.createTopic(STAGE_TOPIC);
        MessageConsumer receiver = session.createConsumer(dest);
        receiver.setMessageListener(listener);
        connection.start();
        return this;
    }

    public void close() {
        closeConnection();
    }
}