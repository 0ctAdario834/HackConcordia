import java.sql.*;

public class Database {
    // Creates app.db in the folder the app runs from (the project root in IntelliJ)
    private static final String URL = "jdbc:sqlite:app.db";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static void init() {
        String sql = """
                  CREATE TABLE IF NOT EXISTS users (
                  id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  name  TEXT NOT NULL,
                  email TEXT UNIQUE
              )""";
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }
}