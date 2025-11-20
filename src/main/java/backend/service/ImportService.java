package backend.service;

import backend.entities.Location;
import backend.entities.Route;
import backend.entities.Coordinates;
import backend.repository.ImportRepository;
import backend.repository.RouteRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ImportService {

    @Inject
    private ImportRepository importRepository;

    @Inject
    private RouteRepository routeRepository;

    private static final String[] JNDI_NAMES = new String[] {
            "java:jboss/datasources/studs",
            "java:jboss/datasources/PostgresDS",
            "java:/jdbc/studs",
            "java:comp/DefaultDataSource"
    };

    private DataSource lookupDataSource() {
        NamingException lastEx = null;
        for (String name : JNDI_NAMES) {
            try {
                InitialContext ic = new InitialContext();
                Object looked = ic.lookup(name);
                if (looked instanceof DataSource) return (DataSource) looked;
            } catch (NamingException e) {
                lastEx = e;
            }
        }
        throw new RuntimeException("DataSource lookup failed for JNDI names: " + String.join(", ", JNDI_NAMES), lastEx);
    }

    public Long importFromXml(InputStream xmlStream, String username) {
        DataSource ds = lookupDataSource();
        int addedCount = 0;
        Long historyId = null;
        List<Route> routes;
        try {
            routes = parseAndValidate(xmlStream);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse/validate XML: " + e.getMessage(), e);
        }

        final int MAX_RETRIES = 3;
        int attempt = 0;
        boolean success = false;
        Exception lastEx = null;

        while (attempt < MAX_RETRIES && !success) {
            attempt++;
            try (Connection conn = ds.getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                conn.setAutoCommit(false);
                try {
                    // проверка уникальности в рамках одной транзакции
                    try (PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM routes WHERE name = ?")) {
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

                    String insertSql = "INSERT INTO routes (creation_date, distance, name, rating, coordinate_x, coordinate_y, from_name, from_x, from_y, to_name, to_x, to_y) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ins = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
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
                            addedCount++;
                        }
                    }

                    conn.commit();
                    success = true;
                    try {
                        historyId = importRepository.insertOperation(conn, username, "SUCCESS", addedCount);
                    } catch (Exception e) {
                        System.err.println("Failed to write import history (SUCCESS): " + e.getMessage());
                    }
                    return historyId;
                } catch (SQLException ex) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    String sqlState = ex.getSQLState();
                    // serialization failure
                    if ("40001".equals(sqlState) && attempt < MAX_RETRIES) {
                        lastEx = ex;
                        try { Thread.sleep(100L * attempt); } catch (InterruptedException ignore) {}
                        continue; // retry
                    } else {
                        // записываем историю FAILED (вне транзакции)
                        try {
                            importRepository.insertOperation(null, username, "FAILED", 0);
                        } catch (Exception e2) {
                            System.err.println("Failed to write import history (FAILED): " + e2.getMessage());
                        }
                        throw new RuntimeException("Import failed: " + ex.getMessage(), ex);
                    }
                } catch (IllegalArgumentException ie) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    try {
                        importRepository.insertOperation(null, username, "FAILED", 0);
                    } catch (Exception e2) {
                        System.err.println("Failed to write import history (FAILED): " + e2.getMessage());
                    }
                    throw ie;
                } finally {
                    try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
                }
            } catch (SQLException e) {
                lastEx = e;
                // если получение соединения упало — не ретраим десятками раз, выходим
                break;
            }
        }

        throw new RuntimeException("Import failed after retries", lastEx);
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
