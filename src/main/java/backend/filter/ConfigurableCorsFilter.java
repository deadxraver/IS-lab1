package backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Настраиваемый CORS фильтр с конфигурацией через параметры
 */
@WebFilter(
    urlPatterns = {"/*"},
    initParams = {
        @WebInitParam(name = "allowedOrigins", value = "*"),
        @WebInitParam(name = "allowedMethods", value = "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD"),
        @WebInitParam(name = "allowedHeaders", value = "Origin,X-Requested-With,Content-Type,Accept,Authorization,Cache-Control,Pragma,Date,If-Modified-Since,If-None-Match"),
        @WebInitParam(name = "exposedHeaders", value = "Content-Length,Content-Type,Date,Server,X-Powered-By"),
        @WebInitParam(name = "allowCredentials", value = "true"),
        @WebInitParam(name = "maxAge", value = "3600")
    }
)
public class ConfigurableCorsFilter implements Filter {

    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials;
    private String maxAge;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Получаем параметры конфигурации
        String originsParam = filterConfig.getInitParameter("allowedOrigins");
        String methodsParam = filterConfig.getInitParameter("allowedMethods");
        String headersParam = filterConfig.getInitParameter("allowedHeaders");
        String exposedHeadersParam = filterConfig.getInitParameter("exposedHeaders");
        String credentialsParam = filterConfig.getInitParameter("allowCredentials");
        String maxAgeParam = filterConfig.getInitParameter("maxAge");

        // Парсим параметры
        this.allowedOrigins = Arrays.asList(originsParam.split(","));
        this.allowedMethods = Arrays.asList(methodsParam.split(","));
        this.allowedHeaders = Arrays.asList(headersParam.split(","));
        this.exposedHeaders = Arrays.asList(exposedHeadersParam.split(","));
        this.allowCredentials = Boolean.parseBoolean(credentialsParam);
        this.maxAge = maxAgeParam;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Получаем Origin из запроса
        String origin = httpRequest.getHeader("Origin");
        
        // Проверяем, разрешен ли origin
        if (isOriginAllowed(origin)) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
        } else if (allowedOrigins.contains("*")) {
            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        }

        // Устанавливаем разрешенные методы
        httpResponse.setHeader("Access-Control-Allow-Methods", 
            String.join(", ", allowedMethods));

        // Устанавливаем разрешенные заголовки
        httpResponse.setHeader("Access-Control-Allow-Headers", 
            String.join(", ", allowedHeaders));

        // Устанавливаем заголовки, которые клиент может читать
        httpResponse.setHeader("Access-Control-Expose-Headers", 
            String.join(", ", exposedHeaders));

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

    private boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        
        // Проверяем точное совпадение
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        
        // Проверяем wildcard поддомены (например, *.example.com)
        for (String allowedOrigin : allowedOrigins) {
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

