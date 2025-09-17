package backend.entities;

public class Location {
	private long x;
	private Integer y; //Поле не может быть null
	private String name; //Поле не может быть null

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
