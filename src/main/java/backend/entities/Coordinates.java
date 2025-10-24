package backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Coordinates {
    @Column(name = "coordinate_x")
    private double x;
    
    @Column(name = "coordinate_y")
    private float y;

    // Constructors
    public Coordinates() {}

    public Coordinates(double x, float y) {
        this.x = x;
        this.y = y;
    }

    // Getters and Setters
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    @Override
    public String toString() {
        return String.format(
                "{" +
                        "x=%.3f," +
                        "y=%.3f," +
                        "}",
                x, y
        );
    }
}