package backend.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
@ServerEndpoint("/ws/routes")
public class RouteWebSocket {

    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        System.out.println("WebSocket connection opened: " + session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        System.out.println("WebSocket connection closed: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error for session " + session.getId() + ": " + throwable.getMessage());
        sessions.remove(session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Received message: " + message);
    }

    public static void broadcast(String message) {
        synchronized (sessions) {
            sessions.removeIf(session -> {
                try {
                    if (session.isOpen()) {
                        session.getBasicRemote().sendText(message);
                        return false;
                    }
                } catch (IOException e) {
                    System.err.println("Error sending message to session " + session.getId() + ": " + e.getMessage());
                }
                return true;
            });
        }
    }

    public static void notifyRouteCreated() {
        broadcast("{\"type\":\"route_created\",\"message\":\"Новый маршрут добавлен\"}");
    }

    public static void notifyRouteUpdated() {
        broadcast("{\"type\":\"route_updated\",\"message\":\"Маршрут обновлен\"}");
    }

    public static void notifyRouteDeleted() {
        broadcast("{\"type\":\"route_deleted\",\"message\":\"Маршрут удален\"}");
    }
}
