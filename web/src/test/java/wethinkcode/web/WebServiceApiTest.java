package wethinkcode.web;

import io.javalin.Javalin;
import kong.unirest.HttpResponse;
import kong.unirest.HttpStatus;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wethinkcode.loadshed.common.transfer.StageDO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebServiceApiTest {
    private static final int WEB_PORT = 7766;
    private Javalin stageStub;
    private Javalin placesStub;
    private Javalin scheduleStub;
    private WebService webService;

    @BeforeAll
    public static void configureUnirestMapper() {
        final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        Unirest.config().setObjectMapper(new kong.unirest.jackson.JacksonObjectMapper(mapper));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (scheduleStub != null) scheduleStub.stop();
        if (placesStub != null) placesStub.stop();
        if (stageStub != null) stageStub.stop();
        if (webService != null) {
            // stop the private server via reflection
            try {
                java.lang.reflect.Field f = WebService.class.getDeclaredField("server");
                f.setAccessible(true);
                Object srv = f.get(webService);
                if (srv instanceof Javalin) ((Javalin) srv).stop();
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    @Test
    public void getStage_returnsStageFromStageService_whenStageServiceHealthy() throws Exception {
        stageStub = Javalin.create().get("/stage", ctx -> ctx.json(new StageDO(3))).start(7001);

        webService = new WebService();
        webService.initialise();
        // start web server on test port via reflection
        java.lang.reflect.Field f = WebService.class.getDeclaredField("server");
        f.setAccessible(true);
        Javalin web = (Javalin) f.get(webService);
        web.start(WEB_PORT);

        HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + WEB_PORT + "/api/stage").asJson();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals(3, response.getBody().getObject().getInt("stage"));
    }

    @Test
    public void getStage_returnsNotFound_whenStageServiceErrors() throws Exception {
        stageStub = Javalin.create().get("/stage", ctx -> ctx.status(500).result("oops")).start(7001);

        webService = new WebService();
        webService.initialise();
        java.lang.reflect.Field f = WebService.class.getDeclaredField("server");
        f.setAccessible(true);
        Javalin web = (Javalin) f.get(webService);
        web.start(WEB_PORT);

        HttpResponse<String> response = Unirest.get("http://localhost:" + WEB_PORT + "/api/stage").asString();
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        String body = response.getBody();
        assertTrue(body != null && body.contains("Stage not found"));
    }

    @Test
    public void getProvinces_forwardsProvincesJsonFromPlacesService() throws Exception {
        placesStub = Javalin.create().get("/provinces", ctx -> ctx.json(new String[]{"A", "B"})).start(7000);

        webService = new WebService();
        webService.initialise();
        java.lang.reflect.Field f = WebService.class.getDeclaredField("server");
        f.setAccessible(true);
        Javalin web = (Javalin) f.get(webService);
        web.start(WEB_PORT);

        HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + WEB_PORT + "/api/provinces").asJson();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("application/json", response.getHeaders().getFirst("Content-Type"));
    }

    @Test
    public void getSchedule_usesStageZero_whenStageServiceUnavailable_thenForwardsSchedule() throws Exception {
        // stage service is not started (unavailable) -> web should use stage 0
        scheduleStub = Javalin.create().get("/Western%20Cape/Knysna/0", ctx -> ctx.json("{\"ok\":true,\"stage\":0}")).start(7002);

        webService = new WebService();
        webService.initialise();
        java.lang.reflect.Field f = WebService.class.getDeclaredField("server");
        f.setAccessible(true);
        Javalin web = (Javalin) f.get(webService);
        web.start(WEB_PORT);

        HttpResponse<JsonNode> response = Unirest.get("http://localhost:" + WEB_PORT + "/api/schedule/Western%20Cape/Knysna").asJson();
        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.getBody().toString().contains("\"stage\":0"));
    }

    @Test
    public void getSchedule_returnsServerError_whenScheduleServiceFails() throws Exception {
        stageStub = Javalin.create().get("/stage", ctx -> ctx.json(new StageDO(2))).start(7001);
        // schedule service returns error
        scheduleStub = Javalin.create().get("/Western%20Cape/Knysna/2", ctx -> ctx.status(500).result("boom")).start(7002);

        webService = new WebService();
        webService.initialise();
        java.lang.reflect.Field f2 = WebService.class.getDeclaredField("server");
        f2.setAccessible(true);
        Javalin web2 = (Javalin) f2.get(webService);
        web2.start(WEB_PORT);

        HttpResponse<String> response = Unirest.get("http://localhost:" + WEB_PORT + "/api/schedule/Western%20Cape/Knysna").asString();

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.getBody() != null && response.getBody().contains("boom"));
    }
}
