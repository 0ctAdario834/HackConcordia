import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * SQLite access for HackConcordia.
 *
 * <p>The database file is {@code app.db} in the working directory by default.
 * Override it with the {@code app.db.url} system property (a full JDBC URL), which
 * the tests use to avoid touching the real file.</p>
 *
 * <p>Tags are stored as one comma-separated string per place, for example
 * {@code Study,Library}. See {@link #listPlaces(String, List)} for how they are matched.</p>
 */
public class Database {
    // Creates app.db in the folder the app runs from (the project root in IntelliJ).
    // Tests override this with -Dapp.db.url=... so they never touch the real file.
    private static final String DEFAULT_URL = "jdbc:sqlite:app.db";

    /**
     * Returns the JDBC URL the app connects to.
     *
     * @return the value of {@code app.db.url} if set, otherwise {@code jdbc:sqlite:app.db}
     */
    public static String url() {
        return System.getProperty("app.db.url", DEFAULT_URL);
    }

    /**
     * Opens a new connection to the configured database.
     *
     * <p>The caller is responsible for closing it, for example with try-with-resources.</p>
     *
     * @return an open connection
     * @throws SQLException if the database cannot be opened
     */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url());
    }

    /**
     * Creates the {@code users} and {@code places} tables if they do not exist.
     *
     * <p>Safe to call on every startup; existing data is kept.</p>
     *
     * @throws RuntimeException if the schema cannot be created
     */
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
                  tags        TEXT NOT NULL DEFAULT '',
                  address     TEXT,
                  latitude    REAL,
                  longitude   REAL
              )""";
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute(usersSql);
            st.execute(placesSql);
            // Upgrade databases created before geolocation was added.
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(places)")) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }
            if (!columns.contains("address")) {
                st.execute("ALTER TABLE places ADD COLUMN address TEXT");
            }
            if (!columns.contains("latitude")) {
                st.execute("ALTER TABLE places ADD COLUMN latitude REAL");
            }
            if (!columns.contains("longitude")) {
                st.execute("ALTER TABLE places ADD COLUMN longitude REAL");
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB init failed", e);
        }
    }

    /**
     * Saves a new place.
     *
     * @param name        display name, already validated by the caller
     * @param description description text, may be empty
     * @param tags        tags for the place; stored comma-separated
     * @return the saved place, including its generated id
     * @throws SQLException if the insert fails
     */
    public static Place insertPlace(String name, String description, List<String> tags) throws SQLException {
        return insertPlace(name, description, tags, null, null, null);
    }

    /** Inserts a place; address, latitude and longitude may be null when unknown. */
    public static Place insertPlace(String name, String description, List<String> tags,
                                    String address, Double latitude, Double longitude) throws SQLException {
        String sql = "INSERT INTO places (name, description, tags, address, latitude, longitude) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, String.join(",", tags));
            ps.setString(4, address);
            ps.setObject(5, latitude);
            ps.setObject(6, longitude);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Place(keys.getLong(1), name, description, tags, address, latitude, longitude, null);
            }
        }
    }

    /**
     * Lists places, optionally filtered, oldest first.
     *
     * <p>When both filters are given, a place must match the text <em>and</em> have one
     * of the tags. Search text is matched literally: {@code %} and {@code _} are not
     * treated as wildcards.</p>
     *
     * @param q    text to find in the name or description (case-insensitive for ASCII
     *             letters only); {@code null} or blank for no text filter
     * @param tags a place matches if it has any of these tags (OR); empty for no tag filter
     * @return the matching places, in insertion order
     * @throws SQLException if the query fails
     */
    public static List<Place> listPlaces(String q, List<String> tags) throws SQLException {
        return listPlaces(q, tags, null, null);
    }

    /**
     * Like {@link #listPlaces(String, List)}, but when userLat/userLon are both given, each place
     * gets a distanceKm and results are sorted nearest first (places without coordinates last).
     */
    public static List<Place> listPlaces(String q, List<String> tags, Double userLat, Double userLon)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, name, description, tags, address, latitude, longitude FROM places WHERE 1=1");
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
                    double lat = rs.getDouble("latitude");
                    Double latitude = rs.wasNull() ? null : lat;
                    double lon = rs.getDouble("longitude");
                    Double longitude = rs.wasNull() ? null : lon;
                    Double distance = (userLat != null && userLon != null && latitude != null && longitude != null)
                            ? haversineKm(userLat, userLon, latitude, longitude) : null;
                    places.add(new Place(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            splitTags(rs.getString("tags")),
                            rs.getString("address"),
                            latitude, longitude, distance));
                }
            }
        }
        if (userLat != null && userLon != null) {
            places.sort(Comparator.comparing((Place p) -> p.distanceKm == null)
                    .thenComparing(p -> p.distanceKm, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return places;
    }

    /** Great-circle distance between two points, in kilometres. */
    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Escapes the LIKE wildcards {@code %}, {@code _} and the escape character itself,
     * so user input is matched literally. Pairs with {@code ESCAPE '\\'} in the query.
     *
     * @param text raw user input
     * @return the text with wildcards escaped
     */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Splits the stored comma-separated tag string into a list, skipping blanks.
     *
     * @param stored value of the {@code tags} column; may be {@code null}
     * @return the individual tags, never {@code null}
     */
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

    /**
     * A saved place, serialized to JSON by Javalin.
     *
     * <p>Public fields so Jackson can read them directly.</p>
     */
    public static class Place {
        /** Database id, assigned on insert. */
        public long id;
        /** Display name. */
        public String name;
        /** Description text; empty string if none was given. */
        public String description;
        /** Tags for this place; empty list if none. */
        public List<String> tags;

        public String address;
        public Double latitude;
        public Double longitude;
        /** Set only when the list was requested with the user's location. */
        public Double distanceKm;

        /**
         * Creates a place value.
         *
         * @param id          database id
         * @param name        display name
         * @param description description text
         * @param tags        tags for the place
         * @param address     address of the place
         * @param latitude    latitude coordinate
         * @param longitude   longitude coordinate
         * @param distanceKm  distance in km from user location
         */
        public Place(long id, String name, String description, List<String> tags,
                     String address, Double latitude, Double longitude, Double distanceKm) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tags = tags;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.distanceKm = distanceKm;
        }
    }
}
