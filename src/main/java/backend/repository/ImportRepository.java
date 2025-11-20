package backend.repository;

import backend.entities.ImportOperation;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImportRepository {

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
                                    throw new RuntimeException("Failed to initialize import schema after DataSource lookup", e);
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
            String ddl = "CREATE TABLE IF NOT EXISTS import_operations (" +
                    "id SERIAL PRIMARY KEY," +
                    "username VARCHAR(255) NOT NULL," +
                    "status VARCHAR(20) NOT NULL," +
                    "created_at TIMESTAMP WITH TIME ZONE NOT NULL," +
                    "added_count INTEGER" +
                    ")";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(ddl);
                schemaInitialized = true;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create or verify import_operations table", e);
            }
        }
    }

    public Long insertOperation(Connection externalConn, String username, String status, Integer addedCount) throws SQLException {
        boolean useExternal = externalConn != null;
        if (useExternal) {
            String sql = "INSERT INTO import_operations (username, status, created_at, added_count) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = externalConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, status);
                ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant()));
                if (addedCount != null) ps.setInt(4, addedCount); else ps.setNull(4, Types.INTEGER);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
		} else {
            try (Connection conn = getDataSource().getConnection()) {
                conn.setAutoCommit(true);
                String sql = "INSERT INTO import_operations (username, status, created_at, added_count) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, status);
                    ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant()));
                    if (addedCount != null) ps.setInt(4, addedCount); else ps.setNull(4, Types.INTEGER);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) return rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to insert import operation", e);
            }
		}
		return null;
	}

    public List<ImportOperation> findByUser(String username) {
        String sql = "SELECT * FROM import_operations WHERE username = ? ORDER BY id DESC";
        try (Connection conn = getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                List<ImportOperation> list = new ArrayList<>();
                while (rs.next()) {
                    ImportOperation op = new ImportOperation();
                    op.setId(rs.getLong("id"));
                    op.setUser(rs.getString("username"));
                    op.setStatus(rs.getString("status"));
                    Timestamp t = rs.getTimestamp("created_at");
                    if (t != null) op.setCreatedAt(ZonedDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()));
                    int c = rs.getInt("added_count");
                    if (!rs.wasNull()) op.setAddedCount(c);
                    list.add(op);
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read import operations", e);
        }
    }
}
