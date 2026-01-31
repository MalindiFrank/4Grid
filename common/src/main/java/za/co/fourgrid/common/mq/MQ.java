package za.co.fourgrid.common.mq;

/**
 * I contain a few variables and definitions common to all the MQ utility classes.
 */
public interface MQ
{
    static final String URL = "tcp://localhost:61616";

    static final String USER = "admin";

    static final String PASSWD = "admin";

    static final String STAGE_TOPIC = "stage";

    // Added constants used by CI tests
    static final String TEST_TOPIC = "test-topic";
    static final String TEST_QUEUE = "test-queue";

    // Alert queue name used by the AlertService
    static final String ALERT_QUEUE = "alert";

    /**
     * Types of destinations available on the MQ.
     * Added so code/tests can refer to MQ.DestinationType.TOPIC or QUEUE.
     */
    public static enum DestinationType {
        QUEUE,
        TOPIC
    }
}
