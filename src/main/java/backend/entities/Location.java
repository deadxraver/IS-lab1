package backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class Location {
    @Column(name = "location_x")
    private long x;
    
    @Column(name = "location_y", nullable = false)
    @NotNull
    private Integer y; //Поле не может быть null
    
    @Column(name = "location_name", nullable = false)
    @NotNull
    private String name; //Поле не может быть null

    // Constructors
    public Location() {}

    public Location(long x, Integer y, String name) {
        this.x = x;
        this.y = y;
        this.name = name;
    }

    // Getters and Setters
    public long getX() {
        return x;
    }

    public void setX(long x) {
        this.x = x;
    }

    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format(
                "{" +
                        "x=%d," +
                        "y=%d," +
                        "name=%s," +
                        "}",
                x, y, name
        );
    }
}
