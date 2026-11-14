import external.Coordinates;
import external.Geocoder;
import external.NominatimGeocoder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;

import java.util.List;
import java.util.Optional;

/**
 * Entry point for the HackConcordia web app.
 *
 * <p>Serves the single-page front end from {@code /public} on the classpath and
 * exposes a small JSON API under {@code /api/places} for listing, searching,
 * filtering, and adding places. Data is stored in SQLite via {@link Database}.</p>
 *
 * <p>Run {@link #main(String[])} to start the server on port 7000.</p>
 */
public class Main {

    /** Tags a place may have. Must match the tag checkboxes in index.html. */
    static final List<String> ALLOWED_TAGS = List.of("Study", "Library", "Café", "Cafeteria");

    /**
     * JSON body accepted by {@code POST /api/places}.
     *
     * <p>Public fields so Jackson can populate them. Only {@code name} is required.</p>
     */
    public static class NewPlace {
        /** Display name of the place. Required, at most 100 characters once trimmed. */
        public String name;
        /** Optional description, at most 500 characters once trimmed. */
        public String description;
        /** Optional tags. Each must be one of {@link Main#ALLOWED_TAGS}. */
        public List<String> tags;
        public String address;
    }

    /**
     * Creates the Javalin application with its routes and static file handler.
     *
     * <p>Initialises the database schema first, so the tables exist before any
     * request is served. Does not start the server; call {@code start(port)} on the
     * result. Tests use this to start the app on a random port.</p>
     *
     * @return a configured, not-yet-started Javalin instance
     */
    public static Javalin createApp() {
        return createApp(new NominatimGeocoder());
    }

    public static Javalin createApp(Geocoder geocoder) {
        Database.init();

        Javalin app = Javalin.create(config ->
            config.staticFiles.add("/public", Location.CLASSPATH)
        );

        app.get("/api/places", ctx -> {
            // e.g. /api/places?q=coffee&tags=Study&tags=Café
            String q = ctx.queryParam("q");
            List<String> tags = ctx.queryParams("tags");
            requireAllowedTags(tags);
            Double lat = parseCoordinate(ctx.queryParam("lat"), -90, 90, "lat");
            Double lon = parseCoordinate(ctx.queryParam("lon"), -180, 180, "lon");
            if ((lat == null) != (lon == null)) {
                throw new BadRequestResponse("lat and lon must be given together.");
            }
            ctx.json(Database.listPlaces(q, tags, lat, lon));
        });

        app.post("/api/places", ctx -> {
            NewPlace body;
            try {
                body = ctx.bodyAsClass(NewPlace.class);
            } catch (Exception e) {
                throw new BadRequestResponse("Request body must be valid JSON.");
            }

            if (body.name == null || body.name.isBlank()) {
                throw new BadRequestResponse("Name is required.");
            }
            if (body.name.trim().length() > 100) {
                throw new BadRequestResponse("Name must be 100 characters or fewer.");
            }
            String description = body.description == null ? "" : body.description.trim();
            if (description.length() > 500) {
                throw new BadRequestResponse("Description must be 500 characters or fewer.");
            }
            List<String> tags = body.tags == null ? List.of() : body.tags;
            requireAllowedTags(tags);

            String address = body.address == null ? null : body.address.trim();
            if (address != null && address.length() > 300) {
                throw new BadRequestResponse("Address must be 300 characters or fewer.");
            }
            Double lat = null;
            Double lon = null;
            if (address != null && !address.isEmpty()) {
                Optional<Coordinates> found = geocoder.geocode(address);
                if (found.isPresent()) {
                    lat = found.get().lat();
                    lon = found.get().lon();
                }
            } else {
                address = null;
            }

            Database.Place saved = Database.insertPlace(
                    body.name.trim(), description, List.copyOf(tags), address, lat, lon);
            ctx.status(201).json(saved);
        });

        return app;
    }

    /**
     * Rejects any tag that is not in {@link #ALLOWED_TAGS}.
     *
     * @param tags tags supplied by the client
     * @throws BadRequestResponse (HTTP 400) naming the first unknown tag
     */
    private static Double parseCoordinate(String raw, double min, double max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            if (Double.isNaN(value) || value < min || value > max) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid " + label + ".");
        }
    }
    private static void requireAllowedTags(List<String> tags) {
        for (String tag : tags) {
            if (!ALLOWED_TAGS.contains(tag)) {
                throw new BadRequestResponse("Unknown tag: " + tag);
            }
        }
    }

    /**
     * Starts the server on port 7000.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        createApp().start(7000);
    }
}
