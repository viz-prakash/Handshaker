package handshaker.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import handshaker.core.HandshakeScanner;
import handshaker.core.Result;

public class SqliteDataBaseOps {

	private static final Logger LOGGER = Logger.getLogger(handshaker.core.HandshakeScanner.class.getName());

	Connection connection;
	String dblocation;
	String dbName;
	String url;

	public SqliteDataBaseOps(String dbLocation, String dbName) throws SQLException {
		this.dblocation = dbLocation;
		this.dbName = dbName;
		this.url = "jdbc:sqlite:" + dbLocation + dbName;
		try {
			this.connection = DriverManager.getConnection(url);
			if (connection != null) {
				DatabaseMetaData meta = connection.getMetaData();
				LOGGER.info(MessageFormat.format("The driver name is: {0}", meta.getDriverName()));
				LOGGER.info(MessageFormat.format("New database has been created at location: {0}", url));
			}
		} catch (SQLException e) {
			LOGGER.severe(e.getMessage());
			throw e;
		}
	}

	public void createTable(String tableName) throws SQLException {

		String sql = String.format(
				"CREATE TABLE IF NOT EXISTS %s (\n" + " ip text PRIMARY KEY,\n" + "	handshake_data  text\n" + ");",
				tableName);
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine(stmt.toString());
			}
			stmt.execute(sql);
			LOGGER.info(MessageFormat.format("Table {0} created in database {1}", tableName, connection.getMetaData()));
		} catch (SQLException e) {
			LOGGER.severe(e.getMessage());
			throw e;
		}
	}

	public void insertData(String tableName, String ip, String handShakeData) throws SQLException {
		String sql = String.format("INSERT INTO %s(ip, handshake_data) VALUES(?,?)", tableName);
		PreparedStatement pstmt;
		try {
			pstmt = connection.prepareStatement(sql);
			pstmt.setString(1, ip);
			pstmt.setString(2, handShakeData);
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine(pstmt.toString());
			}
			pstmt.executeUpdate();
			if (LOGGER.isLoggable(Level.FINE)) {
				LOGGER.fine(MessageFormat.format("Inserted handshake:{0}\n data for ip: {1}\n", handShakeData, ip));
			}
		} catch (SQLException e) {
			LOGGER.severe(e.getMessage());
			throw e;
		}
	}
}
