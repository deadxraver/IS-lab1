package backend.api;

import backend.interceptor.CacheStatisticsInterceptor;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/cache")
@Produces(MediaType.APPLICATION_JSON)
public class CacheResource {

    @GET
    @Path("/statistics/enabled")
    public Response isCacheStatisticsEnabled() {
        boolean enabled = CacheStatisticsInterceptor.isCacheLoggingEnabled();
        return Response.ok()
                .entity("{\"enabled\":" + enabled + "}")
                .build();
    }

    @POST
    @Path("/statistics/enable")
    public Response enableCacheStatistics() {
        CacheStatisticsInterceptor.setCacheLoggingEnabled(true);
        return Response.ok()
                .entity("{\"message\":\"Cache statistics logging enabled\"}")
                .build();
    }

    @POST
    @Path("/statistics/disable")
    public Response disableCacheStatistics() {
        CacheStatisticsInterceptor.setCacheLoggingEnabled(false);
        return Response.ok()
                .entity("{\"message\":\"Cache statistics logging disabled\"}")
                .build();
    }

    @GET
    @Path("/statistics")
    public Response getCacheStatistics() {
        long operations = CacheStatisticsInterceptor.getCacheOperations();
        long size = CacheStatisticsInterceptor.getCacheSize();
        boolean enabled = CacheStatisticsInterceptor.isCacheLoggingEnabled();
        
        String json = String.format(
            "{\"enabled\":%s,\"operations\":%d,\"cacheSize\":%d}",
            enabled,
            operations,
            size
        );
        
        return Response.ok()
                .entity(json)
                .build();
    }

    @POST
    @Path("/statistics/reset")
    public Response resetCacheStatistics() {
        CacheStatisticsInterceptor.resetCacheStatistics();
        return Response.ok()
                .entity("{\"message\":\"Cache statistics reset\"}")
                .build();
    }
}

