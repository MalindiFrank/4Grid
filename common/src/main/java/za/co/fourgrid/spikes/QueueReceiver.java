package za.co.fourgrid.spikes;

import org.apache.activemq.ActiveMQConnectionFactory;
import za.co.fourgrid.common.mq.MQ;

import javax.jms.*;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service by
 * reading messages from a Queue.
 */
public class QueueReceiver implements Runnable
{
    private static long NAP_TIME = 2000; //ms

    public static final String MQ_QUEUE_NAME = "stage";
    public int msgCount = 0;
    public String alert = "";

    public static void main( String[] args ){
        final QueueReceiver app = new QueueReceiver();
        app.run();
    }

    private boolean running = true;

    private Connection connection;

    @Override
    public void run(){

        // FIX: One way to fix this is to move the `closeConnection()` call into a `finally` block
        // in the `run()` method. This ensures that the connection is always closed, even if an
        // exception occurs.

        try{
            setUpMessageListener();
            while( running ){
                System.out.println( "Still doing other things..." );
                snooze();
            }
        }finally{
            closeConnection();
            System.out.println( "Bye..." );
        }
    }

    /**
     * Set up a MQ Session and hook a MessageListener into the MQ machinery. Do this right and,
     * whenever a new Message arrives on the Queue we want to watch, our MessageListener's
     * `onMessage()` method will get called so that we can do something useful with the message.
     */
    private void setUpMessageListener(){
        try{
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory( MQ.URL );
            connection = factory.createConnection( MQ.USER, MQ.PASSWD );

            final Session session = connection.createSession( false, Session.AUTO_ACKNOWLEDGE );
            final Destination queueId = session.createQueue( MQ_QUEUE_NAME );

            final MessageConsumer receiver = session.createConsumer( queueId );
            receiver.setMessageListener((MessageListener) m -> {
                try {
                    if (m instanceof TextMessage) {
                        String body = ((TextMessage) m).getText();

                        if ("SHUTDOWN".equals(body)) {
                            System.out.println("SHUTDOWN command received. Stopping.");
                            running = false;
                        } else {
                            msgCount++;
                            alert = body;
                            System.out.println("Received message: " + body);
                        }
                    } else {
                        System.out.println("Received a non-text message.");
                    }
                } catch (JMSException e) {
                    System.err.println("Error processing message: " + e.getMessage());
                }
            });
            connection.start();

        }catch( JMSException erk ){
            throw new RuntimeException( erk );
        }
    }

    private void snooze(){
        try{
            Thread.sleep( NAP_TIME );
        }catch( InterruptedException eek ){
            // ignore
        }
    }

    private void closeConnection(){
        if( connection != null ) try{
            connection.close();
        }catch( JMSException ex ){
            // ignore
        }
    }

}
