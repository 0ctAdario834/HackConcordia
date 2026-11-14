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

    /**
     * Lists places, optionally filtered.
     * @param q    text to find in the name or description (case-insensitive); null or blank for none
     * @param tags a place matches if it has any of these tags (OR); empty for no tag filter
     */
    public static List<Place> listPlaces(String q, List<String> tags) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, description, tags FROM places WHERE 1=1");
        List<String> params = new ArrayList<>();

        if (q != null && !q.isBlank()) {
            String pattern = "%" + escapeLike(q.trim()) + "%";
            sql.append(" AND (name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\')");
            params.add(pattern);
            params.add(pattern);
        }
        if (!tags.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                // Tags are stored as ",Study,Library," style text, so match whole tags only
                sql.append("(',' || tags || ',') LIKE ?");
                params.add("%," + tags.get(i) + ",%");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY id");

        List<Place> places = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    places.add(new Place(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            splitTags(rs.getString("tags"))));
                }
            }
        }
        return places;
    }

    /** Escapes LIKE wildcards so user input is matched literally. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
