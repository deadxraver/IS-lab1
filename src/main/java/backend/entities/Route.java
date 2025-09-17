package backend.entities;

public class Route {
	private Long id; //Поле не может быть null, Значение поля должно быть больше 0, Значение этого поля должно быть уникальным, Значение этого поля должно генерироваться автоматически
	private String name; //Поле не может быть null, Строка не может быть пустой
	private Coordinates coordinates; //Поле не может быть null
	private java.time.ZonedDateTime creationDate; //Поле не может быть null, Значение этого поля должно генерироваться автоматически
	private Location from; //Поле не может быть null
	private Location to; //Поле может быть null
	private int distance; //Значение поля должно быть больше 1
	private Long rating; //Поле не может быть null, Значение поля должно быть больше 0
}