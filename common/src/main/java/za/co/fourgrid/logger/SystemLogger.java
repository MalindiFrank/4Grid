package za.co.fourgrid.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight centralized logger helper built on SLF4J.*
 * - Use textual logger names (e.g. "PLACES", "ALERT").
 * - Supports parameterized messages and optional Throwable.
 * - Caches Logger instances for efficiency.
 */
public final class SystemLogger {

    // Canonical logger name constants (optional convenience)
    public static final String PLACES = "places";
    public static final String SCHEDULE = "schedule";
    public static final String STAGE = "stage";
    public static final String WEB = "web";
    public static final String ALERT = "alert";

    // Logger cache (thread-safe)
    private static final Map<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    private SystemLogger() { /* utility class */ }

    // Get or create a logger mapped to a name
    public static Logger getLogger(String name) {
        if (name == null || name.isBlank()) {
            name = "default";
        }
        final String key = name.toLowerCase(Locale.ROOT);
        return LOGGERS.computeIfAbsent(key, k -> LoggerFactory.getLogger(k));
    }

    /**
     * Generic logging entry point using a textual level descriptor.
     *
     * @param loggerName textual logger name (case-insensitive)
     * @param level      textual level: "trace","debug","info","warn","error" (case-insensitive)
     * @param message    parameterized message (SLF4J style)
     * @param args       optional args for the message (last arg may be a Throwable, SLF4J will treat it specially)
     */
    public static void log(String loggerName, String level, String message, Object... args) {
        Logger logger = getLogger(loggerName);
        String lvl = (level == null) ? "info" : level.trim().toLowerCase(Locale.ROOT);

        switch (lvl) {
            case "trace":
                if (logger.isTraceEnabled()) logger.trace(message, args);
                break;
            case "debug":
                if (logger.isDebugEnabled()) logger.debug(message, args);
                break;
            case "info":
                if (logger.isInfoEnabled()) logger.info(message, args);
                break;
            case "warn":
            case "warning":
                if (logger.isWarnEnabled()) logger.warn(message, args);
                break;
            case "error":
            case "severe":
                if (logger.isErrorEnabled()) logger.error(message, args);
                break;
            default:
                // unknown level -> info
                if (logger.isInfoEnabled()) logger.info(message, args);
                break;
        }
    }

    /**
     * Convenience overload for logging with an explicit Throwable.
     *
     * @param loggerName textual logger name
     * @param level      textual level
     * @param t          Throwable to include
     * @param message    parameterized message
     * @param args       message arguments
     */
    public static void log(String loggerName, String level, Throwable t, String message, Object... args) {
        Logger logger = getLogger(loggerName);
        String lvl = (level == null) ? "error" : level.trim().toLowerCase(Locale.ROOT);

        // pass throwable explicitly as last parameter to SLF4J
        Object[] argsWithThrowable = appendThrowable(args, t);

        switch (lvl) {
            case "trace":
                if (logger.isTraceEnabled()) logger.trace(message, argsWithThrowable);
                break;
            case "debug":
                if (logger.isDebugEnabled()) logger.debug(message, argsWithThrowable);
                break;
            case "info":
                if (logger.isInfoEnabled()) logger.info(message, argsWithThrowable);
                break;
            case "warn":
            case "warning":
                if (logger.isWarnEnabled()) logger.warn(message, argsWithThrowable);
                break;
            case "error":
            case "severe":
            default:
                if (logger.isErrorEnabled()) logger.error(message, argsWithThrowable);
                break;
        }
    }

    // utility: append throwable at the end of args array (or create new if null)
    private static Object[] appendThrowable(Object[] args, Throwable t) {
        if (t == null) return args;
        if (args == null || args.length == 0) {
            return new Object[]{t};
        }
        Object[] extended = new Object[args.length + 1];
        System.arraycopy(args, 0, extended, 0, args.length);
        extended[args.length] = t;
        return extended;
    }
}
