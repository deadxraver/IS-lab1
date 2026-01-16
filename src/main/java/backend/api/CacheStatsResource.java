package backend.api;

import backend.config.CacheStatisticsManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import javax.cache.Cache;
import javax.cache.CacheManager;
import java.util.HashMap;
import java.util.Map;

@Path("/cache-stats")
@Produces(MediaType.APPLICATION_JSON)
public class CacheStatsResource {

    private static final Logger LOGGER = Logger.getLogger(CacheStatsResource.class);

    @Inject
    private CacheStatisticsManager statisticsManager;

    @Inject
    private CacheManager cacheManager;

    @GET
    @Path("/status")
    public Response getLoggingStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("loggingEnabled", statisticsManager.isLoggingEnabled());
        return Response.ok(response).build();
    }

    @POST
    @Path("/enable")
    public Response enableLogging() {
        statisticsManager.enableLogging();
        LOGGER.info("Cache statistics logging enabled");
        return Response.ok("Cache statistics logging enabled").build();
    }

    @POST
    @Path("/disable")
    public Response disableLogging() {
        statisticsManager.disableLogging();
        LOGGER.info("Cache statistics logging disabled");
        return Response.ok("Cache statistics logging disabled").build();
    }

    @GET
    @Path("/current")
    public Response getCurrentStatistics() {
        if (cacheManager == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Cache manager not available")
                    .build();
        }

        Map<String, Map<String, Object>> stats = new HashMap<>();

        try {
            for (String cacheName : cacheManager.getCacheNames()) {
                Cache<?, ?> cache = cacheManager.getCache(cacheName);
                if (cache != null) {
                    try {
                        // Use reflection to get statistics if available
                        java.lang.reflect.Method getStatsMethod = cache.getClass().getMethod("getStatistics");
                        Object cacheStats = getStatsMethod.invoke(cache);
                        if (cacheStats != null) {
                            Map<String, Object> cacheData = new HashMap<>();
                            long hits = (Long) cacheStats.getClass().getMethod("getCacheHits").invoke(cacheStats);
                            long misses = (Long) cacheStats.getClass().getMethod("getCacheMisses").invoke(cacheStats);
                            long puts = (Long) cacheStats.getClass().getMethod("getCachePuts").invoke(cacheStats);
                            long evictions = (Long) cacheStats.getClass().getMethod("getCacheEvictions").invoke(cacheStats);
                            long removals = (Long) cacheStats.getClass().getMethod("getCacheRemovals").invoke(cacheStats);
                            
                            cacheData.put("hits", hits);
                            cacheData.put("misses", misses);
                            cacheData.put("puts", puts);
                            cacheData.put("evictions", evictions);
                            cacheData.put("removals", removals);
                            
                            double hitRatio = (hits + misses > 0) 
                                ? (double) hits / (hits + misses) * 100.0 
                                : 0.0;
                            cacheData.put("hitRatio", String.format("%.2f%%", hitRatio));
                            
                            stats.put(cacheName, cacheData);
                        }
                    } catch (NoSuchMethodException e) {
                        // Statistics not available for this cache
                        LOGGER.debugf("Statistics not available for cache %s", cacheName);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to collect cache statistics", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error collecting statistics: " + e.getMessage())
                    .build();
        }

        return Response.ok(stats).build();
    }
}

