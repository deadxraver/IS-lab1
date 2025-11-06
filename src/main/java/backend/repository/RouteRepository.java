package backend.repository;

import backend.entities.Route;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RouteRepository {

    @PersistenceContext(unitName = "routeManagementPU")
    private EntityManager entityManager;

    public Route save(Route route) {
        // если это новая сущность — persist + flush, иначе merge + flush
        if (route.getId() == null) {
            entityManager.persist(route);
            entityManager.flush(); // форсируем вставку, чтобы получить сгенерированный id
            return route;
        } else {
            Route merged = entityManager.merge(route);
            entityManager.flush();
            return merged;
        }
//        return entityManager.merge(route);
    }

    public void delete(Long id) {
        Route route = entityManager.find(Route.class, id);
        if (route != null) {
            entityManager.remove(route);
        }
    }

    public Optional<Route> findById(Long id) {
        Route route = entityManager.find(Route.class, id);
        return Optional.ofNullable(route);
    }

    public List<Route> findAll() {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r ORDER BY r.id", Route.class);
        return query.getResultList();
    }

    public List<Route> findAll(int page, int size) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r ORDER BY r.id", Route.class);
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public long count() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(r) FROM Route r", Long.class);
        return query.getSingleResult();
    }

    public List<Route> findByNameContaining(String name) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.name) LIKE LOWER(:name) ORDER BY r.id", Route.class);
        query.setParameter("name", "%" + name + "%");
        return query.getResultList();
    }

    public List<Route> findByRatingGreaterThan(Long rating) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE r.rating > :rating ORDER BY r.id", Route.class);
        query.setParameter("rating", rating);
        return query.getResultList();
    }

    public long countByRatingGreaterThan(Long rating) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(r) FROM Route r WHERE r.rating > :rating", Long.class);
        query.setParameter("rating", rating);
        return query.getSingleResult();
    }

    public List<Long> findDistinctRatings() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT DISTINCT r.rating FROM Route r ORDER BY r.rating", Long.class);
        return query.getResultList();
    }

    public List<Route> findByFromLocation(String fromName) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.from.name) LIKE LOWER(:fromName) ORDER BY r.id", Route.class);
        query.setParameter("fromName", "%" + fromName + "%");
        return query.getResultList();
    }

    public List<Route> findByToLocation(String toName) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.to.name) LIKE LOWER(:toName) ORDER BY r.id", Route.class);
        query.setParameter("toName", "%" + toName + "%");
        return query.getResultList();
    }

    public List<Route> findByLocations(String fromName, String toName) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.from.name) LIKE LOWER(:fromName) AND LOWER(r.to.name) LIKE LOWER(:toName) ORDER BY r.distance", Route.class);
        query.setParameter("fromName", "%" + fromName + "%");
        query.setParameter("toName", "%" + toName + "%");
        return query.getResultList();
    }

    public Optional<Route> findShortestRoute(String fromName, String toName) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.from.name) LIKE LOWER(:fromName) AND LOWER(r.to.name) LIKE LOWER(:toName) ORDER BY r.distance ASC", Route.class);
        query.setParameter("fromName", "%" + fromName + "%");
        query.setParameter("toName", "%" + toName + "%");
        query.setMaxResults(1);
        List<Route> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<Route> findLongestRoute(String fromName, String toName) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE LOWER(r.from.name) LIKE LOWER(:fromName) AND LOWER(r.to.name) LIKE LOWER(:toName) ORDER BY r.distance DESC", Route.class);
        query.setParameter("fromName", "%" + fromName + "%");
        query.setParameter("toName", "%" + toName + "%");
        query.setMaxResults(1);
        List<Route> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public boolean deleteByRating(Long rating) {
        TypedQuery<Route> query = entityManager.createQuery(
            "SELECT r FROM Route r WHERE r.rating = :rating", Route.class);
        query.setParameter("rating", rating);
        query.setMaxResults(1);
        List<Route> results = query.getResultList();

        if (!results.isEmpty()) {
            entityManager.remove(results.get(0));
            return true;
        }
        return false;
    }
}
