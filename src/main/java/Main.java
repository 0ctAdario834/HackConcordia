import external.Geocoder;
import external.NominatimGeocoder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;

import java.util.List;

public class Main {

    /** Tags a place may have. Must match the tag checkboxes in index.html. */
    static final List<String> ALLOWED_TAGS = List.of("Study", "Library", "Café", "Cafeteria");

    /** JSON body for POST /api/places. Public fields so Jackson can fill them. */
    public static class NewPlace {
        public String name;
        public String description;
        public List<String> tags;
    }

    public static Javalin createApp() {
        Database.init();

        Javalin app = Javalin.create(config ->
            config.staticFiles.add("/public", Location.CLASSPATH)
        );

        app.get("/api/places", ctx -> ctx.json(Database.listPlaces()));

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
            for (String tag : tags) {
                if (!ALLOWED_TAGS.contains(tag)) {
                    throw new BadRequestResponse("Unknown tag: " + tag);
                }
            }

            Database.Place saved = Database.insertPlace(body.name.trim(), description, List.copyOf(tags));
            ctx.status(201).json(saved);
        });

        return app;
    }

    public static void main(String[] args) {
        createApp().start(7000);
    }
}
