package wethinkcode.loadshed.spikes;

import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;

public class QueueSender implements Runnable
{
    private static long NAP_TIME = 2000; //ms

    public static final String MQ_URL = "tcp://localhost:61616";

    public static final String MQ_USER = "admin";

    public static final String MQ_PASSWD = "admin";

    public static final String MQ_QUEUE_NAME = "stage";

    public static void main( String[] args ){
        final QueueSender app = new QueueSender();
        app.run();
    }

    private String[] cmdLineMsgs;

    private Connection connection;

    private Session session;

    @Override
    public void run(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ_URL );
            connection = factory.createConnection( MQ_USER, MQ_PASSWD );
            connection.start();

            session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            sendAllMessages( cmdLineMsgs == null || cmdLineMsgs.length == 0
                    ? new String[]{ "{ \"stage\":4 }" }
                    : cmdLineMsgs );

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }finally{
            closeResources();
        }
        System.out.println( "Bye..." );
    }

    private void sendAllMessages( String[] messages ) throws JMSException {
        Destination destination = session.createQueue(MQ_QUEUE_NAME);

        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        for (String msgBody : messages) {
            TextMessage message = session.createTextMessage(msgBody);
            producer.send(message);
            System.out.println("Sent message: " + message.getText());
        }

        producer.close();
    }

    private void closeResources(){
        try{
            if( session != null ) session.close();
            if( connection != null ) connection.close();
        }catch( JMSException ex ){
            // ignore
        }
        session = null;
        connection = null;
    }
}