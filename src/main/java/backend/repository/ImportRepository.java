package backend.repository;

import backend.entities.ImportOperation;
import backend.util.DataSourceProvider;

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

    private DataSource getDataSource() {
        return DataSourceProvider.getDataSource();
    }

    private Connection getConnectionWithSchema() throws SQLException {
        Connection conn = getDataSource().getConnection();
        ensureSchemaExists(conn);
        return conn;
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
                    "added_count INTEGER," +
                    "file_object_name VARCHAR(255)" +
                    ")";
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(ddl);
                schemaInitialized = true;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create or verify import_operations table", e);
            }
        }
    }

    public Long insertOperation(Connection externalConn, String username, String status, Integer addedCount, String fileObjectName) throws SQLException {
        boolean useExternal = externalConn != null;
        if (useExternal) {
            String sql = "INSERT INTO import_operations (username, status, created_at, added_count, file_object_name) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = externalConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, status);
                ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant()));
                if (addedCount != null) ps.setInt(4, addedCount); else ps.setNull(4, Types.INTEGER);
                if (fileObjectName != null) ps.setString(5, fileObjectName); else ps.setNull(5, Types.VARCHAR);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
		} else {
            try (Connection conn = getConnectionWithSchema()) {
                conn.setAutoCommit(true);
                String sql = "INSERT INTO import_operations (username, status, created_at, added_count, file_object_name) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, status);
                    ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant()));
                    if (addedCount != null) ps.setInt(4, addedCount); else ps.setNull(4, Types.INTEGER);
                    if (fileObjectName != null) ps.setString(5, fileObjectName); else ps.setNull(5, Types.VARCHAR);
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
        try (Connection conn = getConnectionWithSchema();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                List<ImportOperation> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRowToImportOperation(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read import operations", e);
        }
    }

    public ImportOperation findById(Long id) {
        String sql = "SELECT * FROM import_operations WHERE id = ?";
        try (Connection conn = getConnectionWithSchema();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToImportOperation(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read import operation by id", e);
        }
    }

    private ImportOperation mapRowToImportOperation(ResultSet rs) throws SQLException {
        ImportOperation op = new ImportOperation();
        op.setId(rs.getLong("id"));
        op.setUser(rs.getString("username"));
        op.setStatus(rs.getString("status"));
        Timestamp t = rs.getTimestamp("created_at");
        if (t != null) op.setCreatedAt(ZonedDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault()));
        int c = rs.getInt("added_count");
        if (!rs.wasNull()) op.setAddedCount(c);
        op.setFileObjectName(rs.getString("file_object_name"));
        return op;
    }
}
