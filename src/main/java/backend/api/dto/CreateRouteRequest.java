package backend.api.dto;

import backend.entities.Coordinates;
import backend.entities.Location;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * DTO запроса на создание Route.
 * Поля id и creationDate отсутствуют — они генерируются на сервере.
 */
public class CreateRouteRequest {

    @NotNull
    @NotBlank
    private String name;

    @NotNull
    private Coordinates coordinates;

    @NotNull
    private Location from;

    private Location to;

    @Min(value = 2, message = "Distance must be greater than 1")
    private int distance;

    @NotNull
    @Positive
    private Long rating;

    // Геттеры/сеттеры

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Coordinates getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }

    public Location getFrom() {
        return from;
    }

    public void setFrom(Location from) {
        this.from = from;
    }

    public Location getTo() {
        return to;
    }

    public void setTo(Location to) {
        this.to = to;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public Long getRating() {
        return rating;
    }

    public void setRating(Long rating) {
        this.rating = rating;
    }
}

