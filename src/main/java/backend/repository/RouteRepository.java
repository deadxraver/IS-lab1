package backend.repository;

import backend.entities.Route;
import backend.entities.Coordinates;
import backend.entities.Location;

import jakarta.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class RouteRepository {

    private volatile DataSource dataSource;

    private volatile boolean schemaInitialized = false;

    private static final String[] JNDI_NAMES = new String[] {
        "java:jboss/datasources/studs",
        "java:jboss/datasources/PostgresDS",
        "java:/jdbc/studs",
        "java:comp/DefaultDataSource"
    };

    private DataSource getDataSource() {
        if (dataSource == null) {
            synchronized (this) {
                if (dataSource == null) {
                    NamingException lastEx = null;
                    for (String name : JNDI_NAMES) {
                        try {
                            InitialContext ic = new InitialContext();
                            Object looked = ic.lookup(name);
                            if (looked instanceof DataSource) {
                                dataSource = (DataSource) looked;
                                try (Connection conn = dataSource.getConnection()) {
                                    conn.setAutoCommit(true);
                                    ensureSchemaExists(conn);
                                } catch (SQLException e) {
                                    throw new RuntimeException("Failed to initialize DB schema after DataSource lookup", e);
                                }
                                break;
                            } else {
                                lastEx = new NamingException("JNDI lookup returned non-DataSource object for " + name + ": " + looked);
                            }
                        } catch (NamingException e) {
                            lastEx = e;
                        }
                    }
                    if (dataSource == null) {
                        throw new RuntimeException("DataSource lookup failed for JNDI names " + String.join(", ", JNDI_NAMES),
                                lastEx);
                    }
                }
            }
        }
        return dataSource;
    }

    private void ensureSchemaExists(Connection conn) {
        if (schemaInitialized) return;
        synchronized (this) {
            if (schemaInitialized) return;
            String ddl = "CREATE TABLE IF NOT EXISTS routes (" +
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
                    ")";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(ddl);
                schemaInitialized = true;
            } catch (SQLException e) {
                System.err.println("Failed to create or verify routes table: " + e.getMessage());
                throw new RuntimeException("Failed to create or verify routes table", e);
            }
        }
    }

    public Route save(Route route) {
        if (route.getId() == null) {
            if (route.getCreationDate() == null) {
                route.setCreationDate(ZonedDateTime.now());
            }
            String sqlReturning = "INSERT INTO routes (creation_date, distance, name, rating, coordinate_x, coordinate_y, from_name, from_x, from_y, to_name, to_x, to_y) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
            try (Connection conn = getDataSource().getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(sqlReturning)) {
                    ps.setTimestamp(1, Timestamp.from(route.getCreationDate().toInstant()));
                    ps.setInt(2, route.getDistance());
                    ps.setString(3, route.getName());
                    ps.setLong(4, route.getRating());

                    if (route.getCoordinates() != null) {
                        ps.setDouble(5, route.getCoordinates().getX());
                        ps.setFloat(6, route.getCoordinates().getY());
                    } else {
                        ps.setNull(5, Types.DOUBLE);
                        ps.setNull(6, Types.FLOAT);
                    }

                    if (route.getFrom() != null) {
                        ps.setString(7, route.getFrom().getName());
                        ps.setLong(8, route.getFrom().getX());
                        if (route.getFrom().getY() != null) ps.setInt(9, route.getFrom().getY());
                        else ps.setNull(9, Types.INTEGER);
                    } else {
                        ps.setNull(7, Types.VARCHAR);
                        ps.setNull(8, Types.BIGINT);
                        ps.setNull(9, Types.INTEGER);
                    }

                    if (route.getTo() != null) {
                        ps.setString(10, route.getTo().getName());
                        ps.setLong(11, route.getTo().getX());
                        if (route.getTo().getY() != null) ps.setInt(12, route.getTo().getY());
                        else ps.setNull(12, Types.INTEGER);
                    } else {
                        ps.setNull(10, Types.VARCHAR);
                        ps.setNull(11, Types.BIGINT);
                        ps.setNull(12, Types.INTEGER);
                    }

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            route.setId(rs.getLong(1));
                            return route;
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("INSERT ... RETURNING failed: SQLState=" + e.getSQLState() + " ErrorCode=" + e.getErrorCode() + " Message=" + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("DataSource/connection error: " + e.getMessage());
                throw e;
            }

            String sqlNoReturning = "INSERT INTO routes (creation_date, distance, name, rating, coordinate_x, coordinate_y, from_name, from_x, from_y, to_name, to_x, to_y) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = getDataSource().getConnection()) {
                conn.setAutoCommit(true);
                try (PreparedStatement ps = conn.prepareStatement(sqlNoReturning, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setTimestamp(1, Timestamp.from(route.getCreationDate().toInstant()));
                    ps.setInt(2, route.getDistance());
                    ps.setString(3, route.getName());
                    ps.setLong(4, route.getRating());

                    if (route.getCoordinates() != null) {
                        ps.setDouble(5, route.getCoordinates().getX());
                        ps.setFloat(6, route.getCoordinates().getY());
                    } else {
                        ps.setNull(5, Types.DOUBLE);
                        ps.setNull(6, Types.FLOAT);
                    }

                    if (route.getFrom() != null) {
                        ps.setString(7, route.getFrom().getName());
                        ps.setLong(8, route.getFrom().getX());
                        if (route.getFrom().getY() != null) ps.setInt(9, route.getFrom().getY());
                        else ps.setNull(9, Types.INTEGER);
                    } else {
                        ps.setNull(7, Types.VARCHAR);
                        ps.setNull(8, Types.BIGINT);
                        ps.setNull(9, Types.INTEGER);
                    }

                    if (route.getTo() != null) {
                        ps.setString(10, route.getTo().getName());
                        ps.setLong(11, route.getTo().getX());
                        if (route.getTo().getY() != null) ps.setInt(12, route.getTo().getY());
                        else ps.setNull(12, Types.INTEGER);
                    } else {
                        ps.setNull(10, Types.VARCHAR);
                        ps.setNull(11, Types.BIGINT);
                        ps.setNull(12, Types.INTEGER);
                    }

                    int affected = ps.executeUpdate();
                    if (affected == 0) {
                        throw new RuntimeException("Insert returned 0 affected rows");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys != null && keys.next()) {
                            route.setId(keys.getLong(1));
                            return route;
                        } else {
                            // нет сгенерированного ключа — всё ещё ошибка
                            throw new RuntimeException("No generated key returned after insert");
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Fallback INSERT failed: SQLState=" + e.getSQLState() + " ErrorCode=" + e.getErrorCode() + " Message=" + e.getMessage());
                throw new RuntimeException("Failed to insert route via JDBC", e);
            }
        } else {
            String sql = "UPDATE routes SET creation_date = ?, distance = ?, name = ?, rating = ?, coordinate_x = ?, coordinate_y = ?, from_name = ?, from_x = ?, from_y = ?, to_name = ?, to_x = ?, to_y = ? WHERE id = ?";
            try (Connection conn = getDataSource().getConnection()) {
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setTimestamp(1, route.getCreationDate() == null ? null : Timestamp.from(route.getCreationDate().toInstant()));
                    ps.setInt(2, route.getDistance());
                    ps.setString(3, route.getName());
                    ps.setLong(4, route.getRating());

                    if (route.getCoordinates() != null) {
                        ps.setDouble(5, route.getCoordinates().getX());
                        ps.setFloat(6, route.getCoordinates().getY());
                    } else {
                        ps.setNull(5, Types.DOUBLE);
                        ps.setNull(6, Types.FLOAT);
                    }

                    if (route.getFrom() != null) {
                        ps.setString(7, route.getFrom().getName());
                        ps.setLong(8, route.getFrom().getX());
                        if (route.getFrom().getY() != null) ps.setInt(9, route.getFrom().getY());
                        else ps.setNull(9, Types.INTEGER);
                    } else {
                        ps.setNull(7, Types.VARCHAR);
                        ps.setNull(8, Types.BIGINT);
                        ps.setNull(9, Types.INTEGER);
                    }

                    if (route.getTo() != null) {
                        ps.setString(10, route.getTo().getName());
                        ps.setLong(11, route.getTo().getX());
                        if (route.getTo().getY() != null) ps.setInt(12, route.getTo().getY());
                        else ps.setNull(12, Types.INTEGER);
                    } else {
                        ps.setNull(10, Types.VARCHAR);
                        ps.setNull(11, Types.BIGINT);
                        ps.setNull(12, Types.INTEGER);
                    }

                    ps.setLong(13, route.getId());
                    ps.executeUpdate();

                    conn.commit();
                    return route;
                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignore) {}
                    throw new RuntimeException("Failed to update route via JDBC", e);
                } finally {
                    try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update route via JDBC", e);
            }
        }
    }

    public void delete(Long id) {
        String sql = "DELETE FROM routes WHERE id = ?";
        try (Connection conn = getDataSource().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw new RuntimeException("Failed to delete route via JDBC", e);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete route via JDBC", e);
        }
    }

    public Optional<Route> findById(Long id) {
        String sql = "SELECT * FROM routes WHERE id = ?";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToRoute(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find route by id via JDBC", e);
        }
    }

    public List<Route> findAll() {
        String sql = "SELECT * FROM routes ORDER BY id";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Route> list = new ArrayList<>();
            while (rs.next()) list.add(mapRowToRoute(rs));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all routes via JDBC", e);
        }
    }

    public List<Route> findAll(int page, int size) {
        String sql = "SELECT * FROM routes ORDER BY id LIMIT ? OFFSET ?";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, size);
            ps.setInt(2, page * size);
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find paged routes via JDBC", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM routes";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count routes via JDBC", e);
        }
    }

    public List<Route> findByNameContaining(String name) {
        String sql = "SELECT * FROM routes WHERE LOWER(name) LIKE LOWER(?) ORDER BY id";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + name + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search routes by name via JDBC", e);
        }
    }

    public List<Route> findByRatingGreaterThan(Long rating) {
        String sql = "SELECT * FROM routes WHERE rating > ? ORDER BY id";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rating);
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find routes by rating via JDBC", e);
        }
    }

    public long countByRatingGreaterThan(Long rating) {
        String sql = "SELECT COUNT(*) FROM routes WHERE rating > ?";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rating);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count by rating via JDBC", e);
        }
    }

    public List<Long> findDistinctRatings() {
        String sql = "SELECT DISTINCT rating FROM routes ORDER BY rating";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Long> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getLong("rating"));
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find distinct ratings via JDBC", e);
        }
    }

    public List<Route> findByFromLocation(String fromName) {
        String sql = "SELECT * FROM routes WHERE LOWER(from_name) LIKE LOWER(?) ORDER BY id";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + fromName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find by from location via JDBC", e);
        }
    }

    public List<Route> findByToLocation(String toName) {
        String sql = "SELECT * FROM routes WHERE LOWER(to_name) LIKE LOWER(?) ORDER BY id";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + toName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find by to location via JDBC", e);
        }
    }

    public List<Route> findByLocations(String fromName, String toName) {
        String sql = "SELECT * FROM routes WHERE LOWER(from_name) LIKE LOWER(?) AND LOWER(to_name) LIKE LOWER(?) ORDER BY distance";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + fromName + "%");
            ps.setString(2, "%" + toName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                List<Route> list = new ArrayList<>();
                while (rs.next()) list.add(mapRowToRoute(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find routes by locations via JDBC", e);
        }
    }

    public Optional<Route> findShortestRoute(String fromName, String toName) {
        String sql = "SELECT * FROM routes WHERE LOWER(from_name) LIKE LOWER(?) AND LOWER(to_name) LIKE LOWER(?) ORDER BY distance ASC LIMIT 1";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + fromName + "%");
            ps.setString(2, "%" + toName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToRoute(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find shortest route via JDBC", e);
        }
    }

    public Optional<Route> findLongestRoute(String fromName, String toName) {
        String sql = "SELECT * FROM routes WHERE LOWER(from_name) LIKE LOWER(?) AND LOWER(to_name) LIKE LOWER(?) ORDER BY distance DESC LIMIT 1";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + fromName + "%");
            ps.setString(2, "%" + toName + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToRoute(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find longest route via JDBC", e);
        }
    }

    public boolean deleteByRating(Long rating) {
        String select = "SELECT id FROM routes WHERE rating = ? LIMIT 1";
        String delete = "DELETE FROM routes WHERE id = ?";
        try (Connection conn = getDataSource().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try (PreparedStatement psSel = conn.prepareStatement(select)) {
                psSel.setLong(1, rating);
                try (ResultSet rs = psSel.executeQuery()) {
                    if (!rs.next()) {
                        try { conn.commit(); } catch (SQLException ignore) {}
                        return false;
                    }
                    long id = rs.getLong(1);
                    try (PreparedStatement psDel = conn.prepareStatement(delete)) {
                        psDel.setLong(1, id);
                        int affected = psDel.executeUpdate();
                        conn.commit();
                        return affected > 0;
                    }
                }
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw new RuntimeException("Failed to delete by rating via JDBC", e);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete by rating via JDBC", e);
        }
    }

    public Optional<Route> findByExactName(String name) {
        String sql = "SELECT * FROM routes WHERE name = ? LIMIT 1";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRowToRoute(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find route by exact name via JDBC", e);
        }
    }

    public Route insertWithSerializable(Route route) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM routes WHERE name = ?";
        String insertSql = "INSERT INTO routes (creation_date, distance, name, rating, coordinate_x, coordinate_y, from_name, from_x, from_y, to_name, to_x, to_y) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getDataSource().getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setString(1, route.getName());
                    try (ResultSet rs = check.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new IllegalArgumentException("Route name already exists: " + route.getName());
                        }
                    }
                }

                try (PreparedStatement ins = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    if (route.getCreationDate() == null) route.setCreationDate(ZonedDateTime.now());
                    ins.setTimestamp(1, Timestamp.from(route.getCreationDate().toInstant()));
                    ins.setInt(2, route.getDistance());
                    ins.setString(3, route.getName());
                    ins.setLong(4, route.getRating());

                    if (route.getCoordinates() != null) {
                        ins.setDouble(5, route.getCoordinates().getX());
                        ins.setFloat(6, route.getCoordinates().getY());
                    } else {
                        ins.setNull(5, Types.DOUBLE);
                        ins.setNull(6, Types.FLOAT);
                    }

                    if (route.getFrom() != null) {
                        ins.setString(7, route.getFrom().getName());
                        ins.setLong(8, route.getFrom().getX());
                        if (route.getFrom().getY() != null) ins.setInt(9, route.getFrom().getY()); else ins.setNull(9, Types.INTEGER);
                    } else {
                        ins.setNull(7, Types.VARCHAR); ins.setNull(8, Types.BIGINT); ins.setNull(9, Types.INTEGER);
                    }

                    if (route.getTo() != null) {
                        ins.setString(10, route.getTo().getName());
                        ins.setLong(11, route.getTo().getX());
                        if (route.getTo().getY() != null) ins.setInt(12, route.getTo().getY()); else ins.setNull(12, Types.INTEGER);
                    } else {
                        ins.setNull(10, Types.VARCHAR); ins.setNull(11, Types.BIGINT); ins.setNull(12, Types.INTEGER);
                    }

                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        if (keys != null && keys.next()) {
                            route.setId(keys.getLong(1));
                        } else {
                            throw new SQLException("No generated key returned after insert");
                        }
                    }
                }

                conn.commit();
                return route;
            } catch (SQLException | RuntimeException ex) {
                try { conn.rollback(); } catch (SQLException ignore) {}
                throw ex;
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
            }
        }
    }

    private Route mapRowToRoute(ResultSet rs) throws SQLException {
        Route r = new Route();
        r.setId(rs.getObject("id") == null ? null : rs.getLong("id"));
        r.setName(rs.getString("name"));

        Timestamp t = rs.getTimestamp("creation_date");
        if (t != null) {
            r.setCreationDate(ZonedDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()));
        }

        double coordX = rs.getDouble("coordinate_x");
        boolean coordXNull = rs.wasNull();
        float coordY = rs.getFloat("coordinate_y");
        boolean coordYNull = rs.wasNull();
        if (!coordXNull || !coordYNull) {
            Coordinates c = new Coordinates();
            if (!coordXNull) c.setX(coordX);
            if (!coordYNull) c.setY(coordY);
            r.setCoordinates(c);
        }

        String fromName = rs.getString("from_name");
        if (fromName != null) {
            Location from = new Location();
            from.setName(fromName);
            long fromX = rs.getLong("from_x");
            if (!rs.wasNull()) from.setX(fromX);
            int fromY = rs.getInt("from_y");
            if (!rs.wasNull()) from.setY(fromY);
            r.setFrom(from);
        }

        String toName = rs.getString("to_name");
        if (toName != null) {
            Location to = new Location();
            to.setName(toName);
            long toX = rs.getLong("to_x");
            if (!rs.wasNull()) to.setX(toX);
            int toY = rs.getInt("to_y");
            if (!rs.wasNull()) to.setY(toY);
            r.setTo(to);
        }

        r.setDistance(rs.getInt("distance"));
        r.setRating(rs.getObject("rating") == null ? null : rs.getLong("rating"));
        return r;
    }
}
