import external.Coordinates;
import external.Geocoder;
import external.NominatimGeocoder;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.staticfiles.Location;

import java.util.List;
import java.util.Optional;

public class Main {

    /** Tags a place may have. Must match the tag checkboxes in index.html. */
    static final List<String> ALLOWED_TAGS = List.of("Study", "Library", "Café", "Cafeteria");

    /** JSON body for POST /api/places. Public fields so Jackson can fill them. */
    public static class NewPlace {
        public String name;
        public String description;
        public List<String> tags;
        public String address;
    }

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

    public static void main(String[] args) {
        createApp().start(7000);
    }
}
