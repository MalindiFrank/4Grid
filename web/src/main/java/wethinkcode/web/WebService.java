// Java
package wethinkcode.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.sse.SseClient;
import io.javalin.http.staticfiles.Location;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;

import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.mq.MqTopicReceiver;
import wethinkcode.loadshed.common.transfer.StageDO;
import wethinkcode.places.model.Town;
import wethinkcode.loadshed.common.mq.ActiveMqSender;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebService implements MessageListener {
    public static final int DEFAULT_PORT = 7010;

    private static final String PLACES_URL = "http://localhost:7000";
    private static final String SCHEDULE_URL = "http://localhost:7002";
    private static final String STAGE_URL = "http://localhost:7001/stage";

    // System-level logger for the web subsystem
    private static final Logger LOG = Logger.getLogger("loadshed.web");

    private Javalin server;
    private UnirestInstance client;
    // synchronisation fix
    private volatile int loadSheddingStage = 0;
    private volatile boolean stageAvailable = false;

    private MqTopicReceiver topicReceiver;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();

    // Health-check scheduler and availability flags to avoid alert storms
    private ScheduledExecutorService healthCheckScheduler;
    private final AtomicBoolean placesAvailable = new AtomicBoolean(true);
    private final AtomicBoolean scheduleAvailable = new AtomicBoolean(true);


    public static void main(String[] args) {
        new WebService().initialise().start();
    }

    WebService initialise() {
        // Configure the objectMapper used for JMS deserialization
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        configureHttpClient();
        // Attempt to fetch initial stage from StageService
        try {
            HttpResponse<StageDO> resp = client.get(STAGE_URL).asObject(StageDO.class);
            if (resp.getStatus() == 200 && resp.getBody() != null) {
                this.loadSheddingStage = resp.getBody().getStage();
                this.stageAvailable = true;
            } else {
                this.stageAvailable = false;
            }
        } catch (Exception e) {
            this.stageAvailable = false;
            // Log the failure to contact the Stage service (info for operators)
            LOG.log(Level.INFO, "Unable to fetch initial stage from StageService", e);
        }

        server = configureHttpServer();
        try {
            topicReceiver = new MqTopicReceiver().init(MQ.URL, this);
            LOG.info("Connected to message broker at " + MQ.URL + " and registered as MessageListener");
        } catch (JMSException e) {
            try {
                LOG.warning("Failed to connect to MQ.URL, falling back to VM broker.");
                topicReceiver = new MqTopicReceiver().init("TEST", this);
            } catch (JMSException ex) {
                throw new RuntimeException(ex);
            }
        }

        // Start periodic health checks for Place-Name and Schedule services so alerts
        // are emitted if either service becomes unreachable while the ensemble is running.
        startHealthChecks();

        return this;
    }

    private void start() {
        server.start(DEFAULT_PORT);
    }

    public void stop() {
        server.stop();
        if (topicReceiver != null) {
            topicReceiver.close();
        }
        // stop health checks
        if (healthCheckScheduler != null) {
            healthCheckScheduler.shutdownNow();
            try {
                healthCheckScheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void configureHttpClient() {
        ObjectMapper jackson = new ObjectMapper();
        jackson.registerModule(new JavaTimeModule());
        jackson.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Unirest.config().setObjectMapper(new kong.unirest.ObjectMapper() {
            @Override
            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jackson.readValue(value, valueType);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String writeValue(Object value) {
                try {
                    return jackson.writeValueAsString(value);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        client = Unirest.spawnInstance();
    }

    private Javalin configureHttpServer() {
        return Javalin.create(config -> {
                    // new API: use config.staticFiles.add(...)
                    config.staticFiles.add(staticFiles -> {
                        staticFiles.hostedPath = "/";
                        staticFiles.directory = "/templates";
                        staticFiles.location = Location.CLASSPATH; // or Location.EXTERNAL depending where your files are
                    });
                })
                .get("/api/stage", this::getStage)
                .get("/api/provinces", this::getProvinces)
                .get("/api/towns/{province}", this::getTowns)
                .get("/api/schedule/{province}/{town}", this::getSchedule)
                .sse("/api/stage-updates", sseClient -> {
                    sseClients.add(sseClient);
                    sseClient.onClose(() -> sseClients.remove(sseClient));
                });
    }

    private void getStage(Context ctx) {
        if (!stageAvailable) {
            ctx.status(404).json("Stage not found");
            return;
        }
        ctx.json(new StageDO(loadSheddingStage));
    }

    private void getProvinces(Context ctx) {
        try {
            HttpResponse<JsonNode> r = client.get(PLACES_URL + "/provinces").asJson();
            if (r.getStatus() == 200 && r.getBody() != null)
                ctx.result(r.getBody().toString()).contentType("application/json");
            else ctx.status(404).json("Provinces not found");
        } catch (Exception e) {
            // Log and send an alert to the alert queue so operators are notified
            LOG.log(Level.WARNING, "Error fetching provinces from Place-Name service", e);
            try (ActiveMqSender sender = ActiveMqSender.openOn(MQ.ALERT_QUEUE)) {
                try {
                    sender.init(MQ.URL);
                    String msg = String.format("WebService: Unable to contact Place-Name service at %s: %s", PLACES_URL, e.getMessage());
                    sender.send(msg);
                } catch (JMSException jmse) {
                    LOG.log(Level.SEVERE, "Failed to initialize ActiveMqSender for alerting", jmse);
                }
            } catch (Exception ignore) {
                // If alerting fails, log at a severe level but keep handling the original error
                LOG.log(Level.SEVERE, "Failed to send alert to alert queue", ignore);
            }
            ctx.status(500).json("Error fetching provinces");
        }
    }

    private void getTowns(Context ctx) {
        String province = ctx.pathParam("province");
        try {
            HttpResponse<Town[]> r = client.get(PLACES_URL + "/towns/" + province).asObject(Town[].class);
            if (r.getStatus() == 200 && r.getBody() != null)
                ctx.json(r.getBody());
            else ctx.status(404).json("Towns not found");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error fetching towns from Place-Name service", e);
            try (ActiveMqSender sender = ActiveMqSender.openOn(MQ.ALERT_QUEUE)) {
                try {
                    sender.init(MQ.URL);
                    String msg = String.format("WebService: Unable to contact Place-Name service at %s for province %s: %s", PLACES_URL, province, e.getMessage());
                    sender.send(msg);
                } catch (JMSException jmse) {
                    LOG.log(Level.SEVERE, "Failed to initialize ActiveMqSender for alerting", jmse);
                }
            } catch (Exception ignore) {
                LOG.log(Level.SEVERE, "Failed to send alert to alert queue", ignore);
            }
            ctx.status(500).json("Error fetching towns");
        }
    }

    private void getSchedule(Context ctx) {
        String province = ctx.pathParam("province");
        String town = ctx.pathParam("town");

        try {
            String r = client.get(SCHEDULE_URL + "/" + province.replace("-", "") + "/" + town + "/" + loadSheddingStage).asString().getBody();
            ctx.result(r).contentType("application/json");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error fetching schedule from Schedule service", e);
            try (ActiveMqSender sender = ActiveMqSender.openOn(MQ.ALERT_QUEUE)) {
                try {
                    sender.init(MQ.URL);
                    String msg = String.format("WebService: Unable to contact Schedule service at %s for %s/%s (stage=%d): %s", SCHEDULE_URL, province, town, loadSheddingStage, e.getMessage());
                    sender.send(msg);
                } catch (JMSException jmse) {
                    LOG.log(Level.SEVERE, "Failed to initialize ActiveMqSender for alerting", jmse);
                }
            } catch (Exception ignore) {
                LOG.log(Level.SEVERE, "Failed to send alert to alert queue", ignore);
            }
            ctx.status(500).json("Error fetching schedule");
        }
    }

    @Override
    public void onMessage(Message message) {
        LOG.log(Level.FINE, "onMessage invoked, message: {0}", message);
        if (message instanceof TextMessage) {
            try {
                String json = ((TextMessage) message).getText();
                StageDO stageDO = objectMapper.readValue(json, StageDO.class);
                this.loadSheddingStage = stageDO.getStage();
                this.stageAvailable = true; // mark available now that we have a valid stage
                LOG.log(Level.INFO, "Updated stage to {0} via JMS", this.loadSheddingStage);
                System.out.println("Updated stage to " + this.loadSheddingStage);

                // Notify all SSE clients
                for (SseClient client : sseClients) {
                    client.sendEvent("stage-update", stageDO.asJson());
                }
            } catch (JMSException | JsonProcessingException e) {
                LOG.log(Level.SEVERE, "Error processing incoming JMS message", e);
            }
        } else {
            LOG.warning("Received non-text JMS message, ignoring");
        }
    }

    // Start a scheduled background job that periodically probes the Place-Name and Schedule services.
    // When a service transitions from reachable to unreachable an alert is sent to the alert queue
    private void startHealthChecks() {
        healthCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "web-health-check");
            t.setDaemon(true);
            return t;
        });

        // initial states: try to probe now to set correct availability flags
        healthCheckScheduler.schedule(() -> {
            probePlacesService();
            probeScheduleService();
        }, 0, TimeUnit.SECONDS);

        // then schedule regular checks
        healthCheckScheduler.scheduleAtFixedRate(() -> {
            try {
                probePlacesService();
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Unhandled error in places health-check", t);
            }
        }, 5, 5, TimeUnit.SECONDS);

        healthCheckScheduler.scheduleAtFixedRate(() -> {
            try {
                probeScheduleService();
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Unhandled error in schedule health-check", t);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void probePlacesService() {
        boolean wasUp = placesAvailable.get();
        try {
            // perform a quick request; any successful response indicates the service is reachable
            client.get(PLACES_URL + "/provinces").asString();
            if (!wasUp) {
                // recovered
                LOG.log(Level.INFO, "Place-Name service at {0} has recovered", PLACES_URL);
            }
            placesAvailable.set(true);
        } catch (Exception e) {
            if (wasUp) {
                LOG.log(Level.WARNING, "Place-Name service appears down", e);
                sendAlert(String.format("WebService: Unable to contact Place-Name service at %s: %s", PLACES_URL, e.getMessage()), e);
            }
            placesAvailable.set(false);
        }
    }

    private void probeScheduleService() {
        boolean wasUp = scheduleAvailable.get();
        try {
            // do a lightweight probe; treat any non-exceptional response as reachable
            client.get(SCHEDULE_URL + "/").asString();
            if (!wasUp) {
                LOG.log(Level.INFO, "Schedule service at {0} has recovered", SCHEDULE_URL);
            }
            scheduleAvailable.set(true);
        } catch (Exception e) {
            if (wasUp) {
                LOG.log(Level.WARNING, "Schedule service appears down", e);
                sendAlert(String.format("WebService: Unable to contact Schedule service at %s: %s", SCHEDULE_URL, e.getMessage()), e);
            }
            scheduleAvailable.set(false);
        }
    }

    private void sendAlert(String message, Throwable t) {
        // Log the event and attempt to publish an alert to the alert queue
        LOG.log(Level.WARNING, message, t);
        try (ActiveMqSender sender = ActiveMqSender.openOn(MQ.ALERT_QUEUE)) {
            try {
                sender.init(MQ.URL);
                sender.send(message);
            } catch (JMSException jmse) {
                LOG.log(Level.SEVERE, "Failed to initialize ActiveMqSender for alerting", jmse);
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to send alert to alert queue", ex);
        }
    }
}
