package backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class JacksonConfig implements ContextResolver<ObjectMapper> {

    private static final Logger LOGGER = Logger.getLogger(JacksonConfig.class);
    private final ObjectMapper objectMapper;

    public JacksonConfig() {
        try {
            objectMapper = new ObjectMapper();
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            objectMapper.registerModule(javaTimeModule);
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            LOGGER.infof("JacksonConfig initialized successfully with JavaTimeModule");
        } catch (Exception e) {
            LOGGER.errorf(e, "Failed to initialize JacksonConfig");
            throw new RuntimeException("Failed to initialize Jackson", e);
        }
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return objectMapper;
    }
}

