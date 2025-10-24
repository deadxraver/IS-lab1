package backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * CORS фильтр для обработки Cross-Origin Resource Sharing запросов
 */
@WebFilter(urlPatterns = {"/*"})
public class CorsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Инициализация фильтра
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Получаем Origin из запроса
        String origin = httpRequest.getHeader("Origin");
        
        // Разрешаем все источники (для разработки)
        // В продакшене лучше указать конкретные домены
        if (origin != null) {
            httpResponse.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        }

        // Разрешаем методы
        httpResponse.setHeader("Access-Control-Allow-Methods", 
            "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");

        // Разрешаем заголовки
        httpResponse.setHeader("Access-Control-Allow-Headers", 
            "Origin, X-Requested-With, Content-Type, Accept, Authorization, " +
            "Cache-Control, Pragma, Date, If-Modified-Since, If-None-Match");

        // Разрешаем отправку cookies
        httpResponse.setHeader("Access-Control-Allow-Credentials", "true");

        // Кэширование preflight запросов на 1 час
        httpResponse.setHeader("Access-Control-Max-Age", "3600");

        // Разрешаем заголовки, которые клиент может читать
        httpResponse.setHeader("Access-Control-Expose-Headers", 
            "Content-Length, Content-Type, Date, Server, X-Powered-By");

        // Обрабатываем preflight запросы
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Продолжаем цепочку фильтров
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Очистка ресурсов
    }
}

