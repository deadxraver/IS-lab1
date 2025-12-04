package backend.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


import javax.sql.DataSource;

public final class DataSourceProvider {
	private static volatile HikariDataSource dataSource;

	private DataSourceProvider() {
	}

	public static DataSource getDataSource() {
		if (dataSource == null) {
			synchronized (DataSourceProvider.class) {
				if (dataSource == null) {
					String url = System.getenv("DB_URL");
					String user = System.getenv("DB_USER");
					String pass = System.getenv("DB_PASSWORD");
					String maxPool = System.getenv("DB_MAX_POOL");

					System.out.println("Database URL: " + url);
					System.out.println("Database User: " + user);
					System.out.println("Database Max Pool: " + maxPool);

					if (url == null || url.trim().isEmpty()) {
						throw new RuntimeException("DB_URL is not set.");
					}

					String jdbcUrl = url.trim();
					if (!jdbcUrl.startsWith("jdbc:")) {
						throw new RuntimeException("DB_URL must be a JDBC URL (jdbc:...), but was: " + jdbcUrl);
					}
					System.out.println(jdbcUrl);
					try {
						try {
							Class.forName("org.postgresql.Driver");
						} catch (ClassNotFoundException e) {
							System.err.println("JDBC driver not found");
						}

						int poolSize = 10;
						if (maxPool != null && !maxPool.trim().isEmpty()) {
							try {
								poolSize = Integer.parseInt(maxPool.trim());
							} catch (NumberFormatException nfe) {
								System.err.println("Invalid DB_MAX_POOL value '" + maxPool + "', using default " + poolSize);
							}
						}

						HikariConfig cfg = new HikariConfig();
						cfg.setJdbcUrl(jdbcUrl);
						cfg.setUsername(user);
						cfg.setPassword(pass);
						cfg.setMaximumPoolSize(poolSize);
						cfg.setPoolName("ISLab3-Pool");
						cfg.setConnectionTestQuery("SELECT 1");
						cfg.setInitializationFailTimeout(5000L);

						dataSource = new HikariDataSource(cfg);
						System.out.println("HikariCP initialized, poolSize=" + poolSize + " jdbcUrl=" + jdbcUrl);

						Runtime.getRuntime().addShutdownHook(new Thread(() -> {
							try {
								if (dataSource != null && !dataSource.isClosed()) {
									dataSource.close();
									System.out.println("HikariCP pool closed");
								}
							} catch (Exception ignored) {
							}
						}));
					} catch (Exception e) {
						throw new RuntimeException("Failed to initialize HikariCP DataSource: " + e.getMessage(), e);
					}
				}
			}
		}
		return dataSource;
	}
}
