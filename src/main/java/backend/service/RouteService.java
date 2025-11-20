package backend.service;

import backend.entities.Route;
import backend.repository.RouteRepository;
import backend.websocket.RouteWebSocket;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

@ApplicationScoped
public class RouteService {

    @Inject
    private RouteRepository routeRepository;

    public Route createRoute(Route route) {

		System.out.println("started creating route in route service");
        if (route.getCreationDate() == null) {
            route.setCreationDate(ZonedDateTime.now());
        }

        if (route.getName() != null && routeRepository.findByExactName(route.getName()).isPresent()) {
            throw new IllegalArgumentException("Route with name '" + route.getName() + "' already exists");
        }

		System.out.println(route);
        Route saved = routeRepository.save(route);
		System.out.println(saved);
		System.out.println(route);
        RouteWebSocket.notifyRouteCreated();
        return saved;
    }

    public Route updateRoute(Long id, Route updatedRoute) {
        Optional<Route> existingRoute = routeRepository.findById(id);
        if (existingRoute.isPresent()) {
            Route route = existingRoute.get();

            String newName = updatedRoute.getName();
            if (newName != null && !newName.equals(route.getName())) {
                Optional<Route> byName = routeRepository.findByExactName(newName);
                if (byName.isPresent() && !byName.get().getId().equals(id)) {
                    throw new IllegalArgumentException("Route with name '" + newName + "' already exists");
                }
            }

            route.setName(updatedRoute.getName());
            route.setCoordinates(updatedRoute.getCoordinates());
            route.setFrom(updatedRoute.getFrom());
            route.setTo(updatedRoute.getTo());
            route.setDistance(updatedRoute.getDistance());
            route.setRating(updatedRoute.getRating());
            Route savedRoute = routeRepository.save(route);
            RouteWebSocket.notifyRouteUpdated();
            return savedRoute;
        }
        throw new IllegalArgumentException("Route with id " + id + " not found");
    }

    public void deleteRoute(Long id) {
        routeRepository.delete(id);
        RouteWebSocket.notifyRouteDeleted();
    }

    public Optional<Route> getRouteById(Long id) {
        return routeRepository.findById(id);
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public List<Route> getRoutes(int page, int size) {
        return routeRepository.findAll(page, size);
    }

    public long getTotalRoutesCount() {
        return routeRepository.count();
    }

    public List<Route> searchRoutesByName(String name) {
        return routeRepository.findByNameContaining(name);
    }

    /**
     * Удалить один (любой) объект, значение поля rating которого эквивалентно заданному
     */
    public boolean deleteRouteByRating(Long rating) {
        return routeRepository.deleteByRating(rating);
    }

    /**
     * Вернуть количество объектов, значение поля rating которых больше заданного
     */
    public long countRoutesByRatingGreaterThan(Long rating) {
        return routeRepository.countByRatingGreaterThan(rating);
    }

    /**
     * Вернуть массив уникальных значений поля rating по всем объектам
     */
    public List<Long> getDistinctRatings() {
        return routeRepository.findDistinctRatings();
    }

    /**
     * Найти самый короткий маршрут между указанными пользователем локациями
     */
    public Optional<Route> findShortestRoute(String fromLocation, String toLocation) {
        return routeRepository.findShortestRoute(fromLocation, toLocation);
    }

    /**
     * Найти самый длинный маршрут между указанными пользователем локациями
     */
    public Optional<Route> findLongestRoute(String fromLocation, String toLocation) {
        return routeRepository.findLongestRoute(fromLocation, toLocation);
    }

    /**
     * Найти все маршруты между указанными пользователем локациями, отсортировать список по заданному параметру
     */
    public List<Route> findRoutesBetweenLocations(String fromLocation, String toLocation) {
        return routeRepository.findByLocations(fromLocation, toLocation);
    }
}
