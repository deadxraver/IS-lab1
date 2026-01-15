package backend.interceptor;

import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.eclipse.persistence.jpa.JpaEntityManager;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.internal.identitymaps.IdentityMap;

import java.util.logging.Logger;

@Interceptor
@CacheStatistics
public class CacheStatisticsInterceptor {

    private static final Logger logger = Logger.getLogger(CacheStatisticsInterceptor.class.getName());

    @PersistenceContext(unitName = "routeManagementPU")
    private EntityManager entityManager;

    private static volatile boolean cacheLoggingEnabled = true;
    
    private static volatile long cacheOperations = 0;
    private static volatile long cacheSize = 0;

    public static void setCacheLoggingEnabled(boolean enabled) {
        cacheLoggingEnabled = enabled;
        logger.info("Cache statistics logging " + (enabled ? "enabled" : "disabled"));
    }

    public static boolean isCacheLoggingEnabled() {
        return cacheLoggingEnabled;
    }
    
    public static long getCacheOperations() {
        return cacheOperations;
    }
    
    public static long getCacheSize() {
        return cacheSize;
    }
    
    public static void resetCacheStatistics() {
        cacheOperations = 0;
        cacheSize = 0;
        logger.info("Cache statistics reset");
    }

    @AroundInvoke
    public Object logCacheStatistics(InvocationContext context) throws Exception {
        if (!cacheLoggingEnabled) {
            return context.proceed();
        }

        long startTime = System.currentTimeMillis();
        String methodName = context.getMethod().getDeclaringClass().getSimpleName() + "." + context.getMethod().getName();
        
        long cacheSizeBefore = getCacheSizeFromSession();
        cacheOperations++;

        Object result = context.proceed();

        long duration = System.currentTimeMillis() - startTime;
        
        long cacheSizeAfter = getCacheSizeFromSession();
        cacheSize = cacheSizeAfter;
        
        logger.info(String.format(
            "L2 Cache Statistics for %s: Duration=%dms, Cache Size Before=%d, Cache Size After=%d, Total Operations=%d",
            methodName,
            duration,
            cacheSizeBefore,
            cacheSizeAfter,
            cacheOperations
        ));

        return result;
    }
    
    private long getCacheSizeFromSession() {
        try {
            if (entityManager != null) {
                JpaEntityManager jpaEm = entityManager.unwrap(JpaEntityManager.class);
                if (jpaEm != null) {
                    Session session = jpaEm.getActiveSession();
                    if (session != null && session instanceof AbstractSession) {
                        AbstractSession absSession = (AbstractSession) session;
                        try {
                            IdentityMap identityMap = absSession.getIdentityMapAccessor().getIdentityMapManager().getIdentityMap();
                            if (identityMap != null) {
                                return identityMap.getSize();
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return 0;
    }
}

