package backend.api;

import backend.api.dto.CreateRouteRequest;
import backend.entities.Route;
import backend.service.RouteService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Context;

import java.util.List;
import java.util.Optional;

@Path("/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RouteResource {

    @Inject
    private RouteService routeService;

    @GET
    public Response getAllRoutes(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {
        try {
            List<Route> routes = routeService.getRoutes(page, size);
            long totalCount = routeService.getTotalRoutesCount();
            
            return Response.ok()
                    .header("X-Total-Count", totalCount)
                    .header("X-Page", page)
                    .header("X-Size", size)
                    .entity(routes)
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error retrieving routes: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getRouteById(@PathParam("id") Long id) {
        try {
            Optional<Route> route = routeService.getRouteById(id);
            if (route.isPresent()) {
                return Response.ok(route.get()).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Route with id " + id + " not found")
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error retrieving route: " + e.getMessage())
                    .build();
        }
    }

    @POST
    public Response createRoute(@Context UriInfo uriInfo, @Valid CreateRouteRequest request) {
        try {
            // Маппим DTO в сущность; не копируем id/creationDate — они генерируются на сервере/БД
            Route route = new Route();
            route.setName(request.getName());
            route.setCoordinates(request.getCoordinates());
            route.setFrom(request.getFrom());
            route.setTo(request.getTo());
            route.setDistance(request.getDistance());
            route.setRating(request.getRating());

            Route createdRoute = routeService.createRoute(route);
            if (createdRoute != null && createdRoute.getId() != null) {
                return Response.created(uriInfo.getAbsolutePathBuilder().path(createdRoute.getId().toString()).build())
                        .entity(createdRoute)
                        .build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity("Route created but id is null")
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error creating route: " + e.getMessage())
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateRoute(@PathParam("id") Long id, @Valid Route route) {
        try {
            Route updatedRoute = routeService.updateRoute(id, route);
            return Response.ok(updatedRoute).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(e.getMessage())
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Error updating route: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteRoute(@PathParam("id") Long id) {
        try {
            routeService.deleteRoute(id);
            return Response.noContent().build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error deleting route: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/search")
    public Response searchRoutes(@QueryParam("name") String name) {
        try {
            List<Route> routes = routeService.searchRoutesByName(name);
            return Response.ok(routes).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error searching routes: " + e.getMessage())
                    .build();
        }
    }

    // Специальные операции

    @DELETE
    @Path("/by-rating/{rating}")
    public Response deleteRouteByRating(@PathParam("rating") Long rating) {
        try {
            boolean deleted = routeService.deleteRouteByRating(rating);
            if (deleted) {
                return Response.ok("Route with rating " + rating + " deleted successfully").build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No route found with rating " + rating)
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error deleting route by rating: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/count-by-rating")
    public Response countRoutesByRating(@QueryParam("rating") Long rating) {
        try {
            long count = routeService.countRoutesByRatingGreaterThan(rating);
            return Response.ok(count).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error counting routes: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/distinct-ratings")
    public Response getDistinctRatings() {
        try {
            List<Long> ratings = routeService.getDistinctRatings();
            return Response.ok(ratings).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error getting distinct ratings: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/shortest")
    public Response findShortestRoute(
            @QueryParam("from") String fromLocation,
            @QueryParam("to") String toLocation) {
        try {
            Optional<Route> route = routeService.findShortestRoute(fromLocation, toLocation);
            if (route.isPresent()) {
                return Response.ok(route.get()).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No route found between " + fromLocation + " and " + toLocation)
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error finding shortest route: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/longest")
    public Response findLongestRoute(
            @QueryParam("from") String fromLocation,
            @QueryParam("to") String toLocation) {
        try {
            Optional<Route> route = routeService.findLongestRoute(fromLocation, toLocation);
            if (route.isPresent()) {
                return Response.ok(route.get()).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("No route found between " + fromLocation + " and " + toLocation)
                        .build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error finding longest route: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Path("/between")
    public Response findRoutesBetweenLocations(
            @QueryParam("from") String fromLocation,
            @QueryParam("to") String toLocation) {
        try {
            List<Route> routes = routeService.findRoutesBetweenLocations(fromLocation, toLocation);
            return Response.ok(routes).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error finding routes between locations: " + e.getMessage())
                    .build();
        }
    }
}
