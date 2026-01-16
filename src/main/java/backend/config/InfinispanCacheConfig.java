package backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.jboss.logging.Logger;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.net.URL;

@ApplicationScoped
public class InfinispanCacheConfig {

    private static final Logger LOGGER = Logger.getLogger(InfinispanCacheConfig.class);
    private CacheManager cacheManager;

    @PostConstruct
    public void init() {
        try {
            CachingProvider provider = null;
            
            // Try embedded provider first (bundled in WAR)
            try {
                provider = Caching.getCachingProvider("org.infinispan.jcache.embedded.JCachingProvider");
                LOGGER.info("Using embedded Infinispan JCache provider");
            } catch (Exception e) {
                LOGGER.warn("Embedded provider lookup failed, trying default: " + e.getMessage());
                try {
                    // Try default provider discovery
                    provider = Caching.getCachingProvider();
                    LOGGER.info("Using default JCache provider: " + provider.getClass().getName());
                } catch (Exception e2) {
                    LOGGER.warn("Default provider lookup also failed: " + e2.getMessage());
                    // Try WildFly-provided provider as last resort (though not available)
                    try {
                        provider = Caching.getCachingProvider("org.infinispan.jcache.JCachingProvider");
                        LOGGER.info("Using WildFly-provided Infinispan JCache provider");
                    } catch (Exception e3) {
                        LOGGER.error("All provider lookup attempts failed. Cache statistics may not be available.", e3);
                        // Don't throw - let EclipseLink handle JCache on its own
                        return;
                    }
                }
            }

            if (provider == null) {
                LOGGER.warn("No JCache provider available. Cache statistics may not be available.");
                return;
            }

            // Load Infinispan configuration if available
            URL configUrl = Thread.currentThread().getContextClassLoader().getResource("META-INF/infinispan.xml");
            if (configUrl != null) {
                LOGGER.info("Loading Infinispan configuration from: " + configUrl);
                try {
                    cacheManager = provider.getCacheManager(configUrl.toURI(), 
                            Thread.currentThread().getContextClassLoader());
                } catch (Exception e) {
                    LOGGER.warn("Failed to load cache manager with config, using default: " + e.getMessage());
                    cacheManager = provider.getCacheManager();
                }
            } else {
                LOGGER.info("No Infinispan configuration found, using default");
                cacheManager = provider.getCacheManager();
            }

            LOGGER.info("Infinispan CacheManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Infinispan CacheManager. Cache statistics may not be available.", e);
            // Don't throw - let the application continue without cache statistics
            cacheManager = null;
        }
    }

    @Produces
    @ApplicationScoped
    public CacheManager getCacheManager() {
        // Return null if not initialized - CacheStatsResource will handle this gracefully
        return cacheManager;
    }

    @PreDestroy
    public void destroy() {
        if (cacheManager != null) {
            cacheManager.close();
            LOGGER.info("Infinispan CacheManager closed");
        }
    }
}
