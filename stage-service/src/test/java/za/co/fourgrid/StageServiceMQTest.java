package za.co.fourgrid;


import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.*;
import za.co.fourgrid.common.mq.MqTopicSender;
import za.co.fourgrid.common.transfer.StageDO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * I test StageService message sending.
 */
@Tag( "expensive" )
public class StageServiceMQTest
{
    public static final int TEST_PORT = 7777;

    private static StageService server;

    private static ActiveMQConnectionFactory factory;

    private static Connection mqConnection;

    @BeforeAll
    public static void startInfrastructure() throws JMSException {
        startMsgQueue();
        startStageSvc();
    }

    @AfterAll
    public static void cleanup() throws JMSException {
        server.stop();
        if (mqConnection != null) {
            mqConnection.close();
        }
    }

    public void connectMqListener( MessageListener listener ) throws JMSException {
        mqConnection = factory.createConnection();
        final Session session = mqConnection.createSession( false, Session.AUTO_ACKNOWLEDGE );
        final Destination dest = session.createTopic( StageService.MQ_TOPIC_NAME );

        final MessageConsumer receiver = session.createConsumer( dest );
        receiver.setMessageListener( listener );

        mqConnection.start();
    }

    @AfterEach
    public void closeMqConnection() throws JMSException {
        if (mqConnection != null) {
            mqConnection.close();
            mqConnection = null;
        }
    }

    @Test
    public void sendMqEventWhenStageChanges() throws Exception {
        final LinkedBlockingQueue<StageDO> resultCatcher = new LinkedBlockingQueue<>();
        final MessageListener mqListener = message -> {
            try {
                if (message instanceof TextMessage) {
                    String json = ((TextMessage) message).getText();
                    StageDO stage = new ObjectMapper().readValue(json, StageDO.class);
                    resultCatcher.offer(stage);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        connectMqListener(mqListener);

        final HttpResponse<StageDO> startStage = Unirest.get( serverUrl() + "/stage" ).asObject( StageDO.class );
        assertEquals( HttpStatus.OK, startStage.getStatus() );

        final StageDO data = startStage.getBody();
        final int newStage = data.getStage() + 1;

        final HttpResponse<JsonNode> changeStage = Unirest.post( serverUrl() + "/stage" )
                .header( "Content-Type", "application/json" )
                .body( new StageDO( newStage ))
                .asJson();
        assertEquals( HttpStatus.OK, changeStage.getStatus() );

        StageDO received = resultCatcher.poll(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(received, "Message not received on MQ");
        assertEquals(newStage, received.getStage());
    }

    private static void startMsgQueue() throws JMSException {
        factory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
    }

    private static void startStageSvc() throws JMSException {
        MqTopicSender sender = new MqTopicSender().init("vm://localhost?broker.persistent=false");
        server = new StageService().initialise(StageService.DEFAULT_STAGE, sender);
        server.start( TEST_PORT );
    }

    private String serverUrl(){
        return "http://localhost:" + TEST_PORT;
    }
}
