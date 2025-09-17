package backend.entities;

public class Coordinates {
	private double x;
	private float y;

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