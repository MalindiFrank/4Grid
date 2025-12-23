package wethinkcode.stage;


import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.mq.MqTopicSender;
import wethinkcode.loadshed.common.transfer.StageDO;

import javax.jms.JMSException;


/**
 * I provide a REST API that reports the current loadshedding "stage". I provide two endpoints:
 * <dl>
 * <dt>GET /stage
 * <dd>report the current stage of loadshedding as a JSON serialisation of a {@code StageDO} data/transfer object
 * <dt>POST /stage
 * <dd>set a new loadshedding stage/level by POSTing a JSON-serialised {@code StageDO} instance as the body of the
 * request.
 * </ul>
 */
public class StageService
{
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7001;

    public static final String MQ_TOPIC_NAME = "stage";

    public static void main( String[] args ){
        final StageService svc = new StageService().initialise();
        svc.start();
    }

    private int loadSheddingStage;

    private Javalin server;

    private int servicePort;

    private MqTopicSender topicSender;

    @VisibleForTesting
    public StageService initialise(){
        return initialise( DEFAULT_STAGE );
    }

    @VisibleForTesting
    public StageService initialise( int initialStage ){
        loadSheddingStage = initialStage;
        assert loadSheddingStage >= 0;

        try {
            topicSender = new MqTopicSender().init(MQ.URL);
        } catch (JMSException e) {
            // Fallback for testing where external broker might not be running
            try {
                System.err.println("Could not connect to default MQ. Retrying with VM broker...");
                topicSender = new MqTopicSender().init("vm://localhost?broker.persistent=false");
            } catch (JMSException ex) {
                throw new RuntimeException("Failed to connect to both default and VM MQ brokers", ex);
            }
        }

        server = initHttpServer();
        return this;
    }

    @VisibleForTesting
    public StageService initialise(int initialStage, MqTopicSender testTopicSender) {
        loadSheddingStage = initialStage;
        assert loadSheddingStage >= 0;

        this.topicSender = testTopicSender;

        server = initHttpServer();
        return this;
    }


    public void start(){
        start( DEFAULT_PORT );
    }

    @VisibleForTesting
    public void start( int networkPort ){
        servicePort = networkPort;
        run();
    }

    public void stop(){
        server.stop();
        if (topicSender != null) {
            topicSender.close();
        }
    }

    public void run(){
        server.start( servicePort );
    }

    private Javalin initHttpServer(){
        return Javalin.create()
            .get( "/stage", this::getCurrentStage )
            .post( "/stage", this::setNewStage );
    }

    private Context getCurrentStage( Context ctx ){
        return ctx.json( new StageDO( loadSheddingStage ) );
    }

    private Context setNewStage( Context ctx ){
        final StageDO stageData = ctx.bodyAsClass( StageDO.class );
        final int newStage = stageData.getStage();
        if( newStage >= 0 ){
            loadSheddingStage = newStage;
            broadcastStageChangeEvent();
            ctx.status( HttpStatus.OK );
        }else{
            ctx.status( HttpStatus.BAD_REQUEST );
        }
        return ctx.json( new StageDO( loadSheddingStage ) );
    }

    private void broadcastStageChangeEvent(){
        try {
            String message = new StageDO(loadSheddingStage).asJson();
            topicSender.send(message);
        } catch (JMSException e) {
            System.err.println("Failed to send stage change event: " + e.getMessage());
        }
    }
}
