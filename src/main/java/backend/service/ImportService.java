package backend.service;

import backend.entities.Location;
import backend.entities.Route;
import backend.entities.Coordinates;
import backend.repository.ImportRepository;
import backend.repository.RouteRepository;
import backend.util.DataSourceProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ImportService {

    @Inject
    private ImportRepository importRepository;

    @Inject
    private RouteRepository routeRepository;

    @Inject
    private MinIOService minIOService;

    public Long importFromXml(InputStream xmlStream, String username) {
        // Сохраняем содержимое InputStream в память для повторного использования
        byte[] xmlBytes;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = xmlStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            xmlBytes = baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read XML stream: " + e.getMessage(), e);
        }

        DataSource ds = DataSourceProvider.getDataSource();
        Long historyId = null;
        List<Route> routes;
        try {
            routes = parseAndValidate(new ByteArrayInputStream(xmlBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse/validate XML: " + e.getMessage(), e);
        }

        // Используем распределенную транзакцию (двухфазный коммит)
        // Используем массивы для хранения значений, которые изменяются в лямбда-выражениях
        final String[] fileObjectName = new String[1];
        final Connection[] dbConnection = new Connection[1];
        final int[] addedCountRef = new int[1];
        
        List<DistributedTransactionManager.TransactionParticipant> participants = new ArrayList<>();
        
        // Участник 1: MinIO (файловое хранилище)
        DistributedTransactionManager.TransactionParticipant minioParticipant = 
            new DistributedTransactionManager.TransactionParticipant(
                "MinIO",
                () -> {
                    // Prepare: загружаем файл в MinIO
                    try {
                        if (!minIOService.isAvailable()) {
                            throw new RuntimeException("MinIO service is not available");
                        }
                        fileObjectName[0] = minIOService.uploadFile(
                            new ByteArrayInputStream(xmlBytes),
                            "application/xml",
                            xmlBytes.length
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to prepare MinIO upload: " + e.getMessage(), e);
                    }
                },
                () -> {
                    // Commit: файл уже загружен, ничего не делаем
                },
                () -> {
                    // Rollback: удаляем загруженный файл
                    if (fileObjectName[0] != null) {
                        try {
                            minIOService.deleteFile(fileObjectName[0]);
                        } catch (Exception e) {
                            System.err.println("Failed to rollback MinIO file: " + e.getMessage());
                        }
                    }
                }
            );
        participants.add(minioParticipant);
        
        // Участник 2: База данных
        DistributedTransactionManager.TransactionParticipant dbParticipant = 
            new DistributedTransactionManager.TransactionParticipant(
                "Database",
                () -> {
                    // Prepare: открываем транзакцию и проверяем данные
                    try {
                        dbConnection[0] = ds.getConnection();
                        dbConnection[0].setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                        dbConnection[0].setAutoCommit(false);
                        
                        // Убеждаемся, что таблица routes существует
                        try (Statement st = dbConnection[0].createStatement()) {
                            st.executeUpdate("CREATE TABLE IF NOT EXISTS routes (" +
                                    "id SERIAL PRIMARY KEY," +
                                    "creation_date TIMESTAMP WITH TIME ZONE NOT NULL," +
                                    "distance INTEGER NOT NULL," +
                                    "name VARCHAR(255) NOT NULL," +
                                    "rating BIGINT NOT NULL," +
                                    "coordinate_x DOUBLE PRECISION," +
                                    "coordinate_y REAL," +
                                    "from_name VARCHAR(255)," +
                                    "from_x BIGINT," +
                                    "from_y INTEGER," +
                                    "to_name VARCHAR(255)," +
                                    "to_x BIGINT," +
                                    "to_y INTEGER" +
                                    ")");
                        }
                        
                        // проверка уникальности в рамках одной транзакции
                        try (PreparedStatement checkStmt = dbConnection[0].prepareStatement("SELECT COUNT(*) FROM routes WHERE name = ?")) {
                            for (Route r : routes) {
                                checkStmt.setString(1, r.getName());
                                try (ResultSet rs = checkStmt.executeQuery()) {
                                    rs.next();
                                    if (rs.getInt(1) > 0) {
                                        throw new IllegalArgumentException("Route name already exists: " + r.getName());
                                    }
                                }
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to prepare database transaction: " + e.getMessage(), e);
                    }
                },
                () -> {
                    // Commit: вставляем данные и коммитим транзакцию
                    try {
                        String insertSql = "INSERT INTO routes (creation_date, distance, name, rating, coordinate_x, coordinate_y, from_name, from_x, from_y, to_name, to_x, to_y) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement ins = dbConnection[0].prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                            for (Route r : routes) {
                                if (r.getCreationDate() == null) r.setCreationDate(ZonedDateTime.now());
                                ins.setTimestamp(1, Timestamp.from(r.getCreationDate().toInstant()));
                                ins.setInt(2, r.getDistance());
                                ins.setString(3, r.getName());
                                ins.setLong(4, r.getRating());
                                if (r.getCoordinates() != null) {
                                    ins.setDouble(5, r.getCoordinates().getX());
                                    ins.setFloat(6, r.getCoordinates().getY());
                                } else {
                                    ins.setNull(5, Types.DOUBLE);
                                    ins.setNull(6, Types.FLOAT);
                                }
                                if (r.getFrom() != null) {
                                    ins.setString(7, r.getFrom().getName());
                                    ins.setLong(8, r.getFrom().getX());
                                    if (r.getFrom().getY() != null) ins.setInt(9, r.getFrom().getY()); else ins.setNull(9, Types.INTEGER);
                                } else {
                                    ins.setNull(7, Types.VARCHAR); ins.setNull(8, Types.BIGINT); ins.setNull(9, Types.INTEGER);
                                }
                                if (r.getTo() != null) {
                                    ins.setString(10, r.getTo().getName());
                                    ins.setLong(11, r.getTo().getX());
                                    if (r.getTo().getY() != null) ins.setInt(12, r.getTo().getY()); else ins.setNull(12, Types.INTEGER);
                                } else {
                                    ins.setNull(10, Types.VARCHAR); ins.setNull(11, Types.BIGINT); ins.setNull(12, Types.INTEGER);
                                }
                                ins.executeUpdate();
                                try (ResultSet keys = ins.getGeneratedKeys()) {
                                    if (keys != null && keys.next()) {
                                        r.setId(keys.getLong(1));
                                    }
                                }
                                addedCountRef[0]++;
                            }
                        }
                        dbConnection[0].commit();
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to commit database transaction: " + e.getMessage(), e);
                    }
                },
                () -> {
                    // Rollback: откатываем транзакцию БД
                    if (dbConnection[0] != null) {
                        try {
                            dbConnection[0].rollback();
                        } catch (SQLException e) {
                            System.err.println("Failed to rollback database transaction: " + e.getMessage());
                        } finally {
                            try {
                                dbConnection[0].setAutoCommit(true);
                                dbConnection[0].close();
                            } catch (SQLException e) {
                                System.err.println("Failed to close database connection: " + e.getMessage());
                            }
                        }
                    }
                }
            );
        participants.add(dbParticipant);
        
        try {
            // Выполняем распределенную транзакцию
            DistributedTransactionManager.execute(participants, () -> {
                // Бизнес-логика уже выполнена в prepare/commit фазах
                return null;
            });
            
            // Если все успешно, записываем историю импорта (в отдельной транзакции)
            int addedCount = addedCountRef[0];
            try (Connection historyConn = ds.getConnection()) {
                historyConn.setAutoCommit(true);
                historyId = importRepository.insertOperation(historyConn, username, "SUCCESS", addedCount, fileObjectName[0]);
            } catch (Exception e) {
                System.err.println("Failed to write import history (SUCCESS): " + e.getMessage());
            }
            
            return historyId;
        } catch (Exception e) {
            // Записываем историю FAILED
            try {
                importRepository.insertOperation(null, username, "FAILED", 0, null);
            } catch (Exception e2) {
                System.err.println("Failed to write import history (FAILED): " + e2.getMessage());
            }
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        } finally {
            if (dbConnection[0] != null) {
                try {
                    if (!dbConnection[0].isClosed()) {
                        dbConnection[0].setAutoCommit(true);
                        dbConnection[0].close();
                    }
                } catch (SQLException e) {
                    System.err.println("Failed to close database connection: " + e.getMessage());
                }
            }
        }
    }

    private List<Route> parseAndValidate(InputStream xmlStream) throws Exception {
        List<Route> list = new ArrayList<>();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlStream);
        doc.getDocumentElement().normalize();
        NodeList nodes = doc.getElementsByTagName("route");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            Route r = new Route();

            String name = getTextContent(el, "name");
            if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Route name is required");
            r.setName(name.trim());

            Element coordsEl = getChildElement(el, "coordinates");
            if (coordsEl == null) throw new IllegalArgumentException("coordinates required");
            Coordinates c = new Coordinates();
            String xs = getTextContent(coordsEl, "x");
            String ys = getTextContent(coordsEl, "y");
            if (xs == null || ys == null) throw new IllegalArgumentException("coordinates.x and coordinates.y required");
            c.setX(Double.parseDouble(xs));
            c.setY(Float.parseFloat(ys));
            r.setCoordinates(c);

            Element fromEl = getChildElement(el, "from");
            if (fromEl == null) throw new IllegalArgumentException("from required");
            Location from = new Location();
            String fromName = getTextContent(fromEl, "name");
            if (fromName == null || fromName.trim().isEmpty()) throw new IllegalArgumentException("from.name required");
            from.setName(fromName.trim());
            from.setX(Long.parseLong(getTextContent(fromEl, "x")));
            String fromY = getTextContent(fromEl, "y");
            if (fromY != null) from.setY(Integer.parseInt(fromY));
            r.setFrom(from);

            Element toEl = getChildElement(el, "to");
            if (toEl != null) {
                Location to = new Location();
                String toName = getTextContent(toEl, "name");
                if (toName != null && !toName.trim().isEmpty()) to.setName(toName.trim());
                String tx = getTextContent(toEl, "x");
                if (tx != null) to.setX(Long.parseLong(tx));
                String ty = getTextContent(toEl, "y");
                if (ty != null) to.setY(Integer.parseInt(ty));
                r.setTo(to);
            }

            String distS = getTextContent(el, "distance");
            if (distS == null) throw new IllegalArgumentException("distance required");
            int dist = Integer.parseInt(distS);
            if (dist < 2) throw new IllegalArgumentException("distance must be >= 2");
            r.setDistance(dist);

            String ratingS = getTextContent(el, "rating");
            if (ratingS == null) throw new IllegalArgumentException("rating required");
            long rating = Long.parseLong(ratingS);
            if (rating <= 0) throw new IllegalArgumentException("rating must be > 0");
            r.setRating(rating);

            list.add(r);
        }
        return list;
    }

    private Element getChildElement(Element parent, String name) {
        NodeList nl = parent.getElementsByTagName(name);
        if (nl.getLength() == 0) return null;
        return (Element) nl.item(0);
    }

    private String getTextContent(Element parent, String childName) {
        Element e = getChildElement(parent, childName);
        if (e == null) return null;
        String txt = e.getTextContent();
        return txt == null ? null : txt.trim();
    }
}
