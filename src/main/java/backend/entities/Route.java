package backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @NotNull
    @Positive
    private Long id;

    @Column(name = "name", nullable = false)
    @NotNull
    @NotBlank
    private String name;

    @Embedded
    @NotNull
    private Coordinates coordinates;

    @Column(name = "creation_date", nullable = false)
    @NotNull
    private ZonedDateTime creationDate;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "x", column = @Column(name = "from_x")),
        @AttributeOverride(name = "y", column = @Column(name = "from_y")),
        @AttributeOverride(name = "name", column = @Column(name = "from_name"))
    })
    @NotNull
    private Location from;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "x", column = @Column(name = "to_x")),
        @AttributeOverride(name = "y", column = @Column(name = "to_y")),
        @AttributeOverride(name = "name", column = @Column(name = "to_name"))
    })
    private Location to;

    @Column(name = "distance", nullable = false)
    @Min(value = 1, message = "Distance must be greater than 1")
    private int distance;

    @Column(name = "rating", nullable = false)
    @NotNull
    @Positive
    private Long rating;

    @PrePersist
    protected void onCreate() {
        if (creationDate == null) {
            creationDate = ZonedDateTime.now();
        }
    }

    // Constructors
    public Route() {}

    public Route(String name, Coordinates coordinates, Location from, Location to, int distance, Long rating) {
        this.name = name;
        this.coordinates = coordinates;
        this.from = from;
        this.to = to;
        this.distance = distance;
        this.rating = rating;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
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

    @Override
    public String toString() {
        return String.format(
                "{" +
                        "id=%d," +
                        "name=%s," +
                        "coordinates=%s," +
                        "creationDate=%s," +
                        "from=%s," +
                        "to=%s," +
                        "distance=%d," +
                        "rating=%d," +
                        "}",
                id, name, coordinates, creationDate, from, to, distance, rating
        );
    }
}