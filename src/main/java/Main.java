import external.Geocoder;
import external.NominatimGeocoder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;

import java.util.List;

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
        Database.init();

        Javalin app = Javalin.create(config ->
            config.staticFiles.add("/public", Location.CLASSPATH)
        );

        app.get("/api/places", ctx -> {
            // e.g. /api/places?q=coffee&tags=Study&tags=Café
            String q = ctx.queryParam("q");
            List<String> tags = ctx.queryParams("tags");
            requireAllowedTags(tags);
            ctx.json(Database.listPlaces(q, tags));
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

            Database.Place saved = Database.insertPlace(body.name.trim(), description, List.copyOf(tags));
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
