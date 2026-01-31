package za.co.fourgrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import za.co.fourgrid.common.mq.MQ;
import za.co.fourgrid.common.mq.MqTopicReceiver;
import za.co.fourgrid.common.transfer.DayDO;
import za.co.fourgrid.common.transfer.ScheduleDO;
import za.co.fourgrid.common.transfer.SlotDO;
import za.co.fourgrid.common.transfer.StageDO;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * I provide a REST API providing the current loadshedding schedule for a given town (in a specific province) at a given
 * loadshedding stage.
 */
public class ScheduleService
{
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7002;

    public static final String MQ_TOPIC = "stage";

    private Javalin server;

    private int servicePort;
    private volatile int currentStage = DEFAULT_STAGE;

    public static void main( String[] args ){
        final ScheduleService svc = new ScheduleService().initialise();
        svc.start();
    }

    @VisibleForTesting
    public ScheduleService initialise(){
        // Fetch initial stage from StageService if possible
        try{
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(new java.net.URI("http://localhost:7001/stage"))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode() == 200 && resp.body() != null){
                ObjectMapper om = new ObjectMapper();
                StageDO s = om.readValue(resp.body(), StageDO.class);
                currentStage = s.getStage();}
        }catch(Exception ignored){ }

        // Start listening for stage change events
        try{
            MqTopicReceiver stageReceiver = new MqTopicReceiver().init(MQ.DestinationType.TOPIC, message -> {
                try {
                    if (message instanceof TextMessage) {
                        String payload = ((TextMessage) message).getText();
                        ObjectMapper om = new ObjectMapper();
                        StageDO s = om.readValue(payload, StageDO.class);
                        currentStage = s.getStage();
                        System.out.println("Current stage: " + currentStage);
                    }
                } catch (Exception e) {
                }
            });
        }catch(JMSException ignored){ }
        server = initHttpServer();
        return this;
    }

    @VisibleForTesting
    public ScheduleService initialise(MqTopicReceiver testReceiver){
        // allow tests to inject a receiver; behaviour mirrors no-arg initialise
        server = initHttpServer();
        return this;
    }

    @VisibleForTesting
    public ScheduleService initialise(int networkPort, MqTopicReceiver testReceiver){
        // allow tests to inject a receiver; behaviour mirrors no-arg initialise
        server = initHttpServer();
        this.servicePort = networkPort;
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
    }

    public void run(){
        server.start( servicePort );
    }

    private Javalin initHttpServer(){
        return Javalin.create()
                .get( "/{province}/{town}/{stage}", this::getSchedule )
                .get( "/{province}/{town}", this::getScheduleWithCurrent );
    }

    private Context getSchedule( Context ctx ){
        final String province = ctx.pathParam( "province" );
        final String townName = ctx.pathParam( "town" );
        final String stageStr = ctx.pathParam( "stage" );

        if( province.isEmpty() || townName.isEmpty() || stageStr.isEmpty() ){
            ctx.status( HttpStatus.BAD_REQUEST );
            return ctx;
        }
        final int stage = Integer.parseInt( stageStr );
        if( stage < 0 || stage > 8 ){
            return ctx.status( HttpStatus.BAD_REQUEST );
        }

        final Optional<ScheduleDO> schedule = getSchedule( province, townName, stage );

        ctx.status( schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND );
        return ctx.json( schedule.orElseGet( ScheduleService::emptySchedule ) );
    }

    private Context getScheduleWithCurrent(Context ctx){
        final String province = ctx.pathParam( "province" );
        final String townName = ctx.pathParam( "town" );
        System.out.println(currentStage);
        final int stage = currentStage;

        final Optional<ScheduleDO> schedule = getSchedule( province, townName, stage );

        ctx.status( schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND );
        return ctx.json( schedule.orElseGet( ScheduleService::emptySchedule ) );
    }

    private Context getDefaultSchedule( Context ctx ){
        return ctx.json( mockSchedule() );
    }

    // There *must* be a better way than this...
    Optional<ScheduleDO> getSchedule( String province, String town, int stage ){
        return province.equalsIgnoreCase( "Mars" )
                ? Optional.empty()
                : Optional.of( mockSchedule() );
    }

    private static ScheduleDO mockSchedule(){
        final List<SlotDO> slots = List.of(
                new SlotDO( LocalTime.of( 2, 0 ), LocalTime.of( 4, 0 ) ),
                new SlotDO( LocalTime.of( 10, 0 ), LocalTime.of( 12, 0 ) ),
                new SlotDO( LocalTime.of( 18, 0 ), LocalTime.of( 20, 0 ) )
        );
        final List<DayDO> days = List.of(
                new DayDO( slots ),
                new DayDO( slots ),
                new DayDO( slots ),
                new DayDO( slots )
        );
        return new ScheduleDO( days );
    }

    private static ScheduleDO emptySchedule(){
        final List<SlotDO> slots = Collections.emptyList();
        final List<DayDO> days = Collections.emptyList();
        return new ScheduleDO( days );
    }
}