import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {
    // Creates app.db in the folder the app runs from (the project root in IntelliJ).
    // Tests override this with -Dapp.db.url=... so they never touch the real file.
    private static final String DEFAULT_URL = "jdbc:sqlite:app.db";

    public static String url() {
        return System.getProperty("app.db.url", DEFAULT_URL);
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url());
    }

    public static void init() {
        String usersSql = """
                  CREATE TABLE IF NOT EXISTS users (
                  id    INTEGER PRIMARY KEY AUTOINCREMENT,
                  name  TEXT NOT NULL,
                  email TEXT UNIQUE
              )""";
        String placesSql = """
                  CREATE TABLE IF NOT EXISTS places (
                  id          INTEGER PRIMARY KEY AUTOINCREMENT,
                  name        TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  tags        TEXT NOT NULL DEFAULT ''
              )""";
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute(usersSql);
            st.execute(placesSql);
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    public static Place insertPlace(String name, String description, List<String> tags) throws SQLException {
        String sql = "INSERT INTO places (name, description, tags) VALUES (?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, String.join(",", tags));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Place(keys.getLong(1), name, description, tags);
            }
        }
    }

    public static List<Place> listPlaces() throws SQLException {
        String sql = "SELECT id, name, description, tags FROM places ORDER BY id";
        List<Place> places = new ArrayList<>();
        try (Connection conn = connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                places.add(new Place(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        splitTags(rs.getString("tags"))));
            }
        }
        return places;
    }

    private static List<String> splitTags(String stored) {
        List<String> tags = new ArrayList<>();
        if (stored != null) {
            for (String tag : stored.split(",")) {
                if (!tag.isBlank()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    /** A saved place, serialized to JSON by Javalin. */
    public static class Place {
        public long id;
        public String name;
        public String description;
        public List<String> tags;

        public Place(long id, String name, String description, List<String> tags) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tags = tags;
        }
    }
}
