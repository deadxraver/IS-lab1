package backend.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CacheStatisticsManager {
    private final AtomicBoolean loggingEnabled = new AtomicBoolean(false);

    public boolean isLoggingEnabled() {
        return loggingEnabled.get();
    }

    public void setLoggingEnabled(boolean enabled) {
        loggingEnabled.set(enabled);
    }

    public void enableLogging() {
        loggingEnabled.set(true);
    }

    public void disableLogging() {
        loggingEnabled.set(false);
    }
}

