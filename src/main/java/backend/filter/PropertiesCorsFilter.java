package backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * CORS фильтр с настройками из properties файла
 */
@WebFilter(urlPatterns = {"/*"})
public class PropertiesCorsFilter implements Filter {

    private Properties corsProperties;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        corsProperties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("cors.properties")) {
            if (input != null) {
                corsProperties.load(input);
            } else {
                // Устанавливаем значения по умолчанию
                setDefaultProperties();
            }
        } catch (IOException e) {
            System.err.println("Ошибка загрузки cors.properties, используются настройки по умолчанию");
            setDefaultProperties();
        }
    }

    private void setDefaultProperties() {
        corsProperties.setProperty("cors.allowed.origins", "*");
        corsProperties.setProperty("cors.allowed.methods", "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD");
        corsProperties.setProperty("cors.allowed.headers", "Origin,X-Requested-With,Content-Type,Accept,Authorization,Cache-Control,Pragma,Date,If-Modified-Since,If-None-Match");
        corsProperties.setProperty("cors.exposed.headers", "Content-Length,Content-Type,Date,Server,X-Powered-By");
        corsProperties.setProperty("cors.allow.credentials", "true");
        corsProperties.setProperty("cors.max.age", "3600");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Получаем настройки из properties
        String allowedOrigins = corsProperties.getProperty("cors.allowed.origins", "*");
        String allowedMethods = corsProperties.getProperty("cors.allowed.methods", "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD");
        String allowedHeaders = corsProperties.getProperty("cors.allowed.headers", "Origin,X-Requested-With,Content-Type,Accept,Authorization,Cache-Control,Pragma,Date,If-Modified-Since,If-None-Match");
        String exposedHeaders = corsProperties.getProperty("cors.exposed.headers", "Content-Length,Content-Type,Date,Server,X-Powered-By");
        boolean allowCredentials = Boolean.parseBoolean(corsProperties.getProperty("cors.allow.credentials", "true"));
        String maxAge = corsProperties.getProperty("cors.max.age", "3600");

        // Получаем Origin из запроса
        String origin = httpRequest.getHeader("Origin");
        
        // Проверяем, разрешен ли origin
        if (isOriginAllowed(origin, allowedOrigins)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
        } else if ("*".equals(allowedOrigins)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        }

        // Устанавливаем разрешенные методы
        httpResponse.setHeader("Access-Control-Allow-Methods", allowedMethods);

        // Устанавливаем разрешенные заголовки
        httpResponse.setHeader("Access-Control-Allow-Headers", allowedHeaders);

        // Устанавливаем заголовки, которые клиент может читать
        httpResponse.setHeader("Access-Control-Expose-Headers", exposedHeaders);

        // Настройка credentials
        if (allowCredentials) {
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
        }

        // Кэширование preflight запросов
        httpResponse.setHeader("Access-Control-Max-Age", maxAge);

        // Обрабатываем preflight запросы
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Продолжаем цепочку фильтров
        chain.doFilter(request, response);
    }

    private boolean isOriginAllowed(String origin, String allowedOrigins) {
        if (origin == null || allowedOrigins == null) {
            return false;
        }
        
        List<String> allowedList = Arrays.asList(allowedOrigins.split(","));
        
        // Проверяем точное совпадение
        if (allowedList.contains(origin)) {
            return true;
        }
        
        // Проверяем wildcard поддомены (например, *.example.com)
        for (String allowedOrigin : allowedList) {
            if (allowedOrigin.startsWith("*.")) {
                String domain = allowedOrigin.substring(2);
                if (origin.endsWith(domain)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    @Override
    public void destroy() {
        // Очистка ресурсов
    }
}

