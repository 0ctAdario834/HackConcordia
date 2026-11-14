package api;

import io.javalin.Javalin;
import io.javalin.http.Context;

public class LocationRoutes implements Routes {
    private final Database service;

    public LocationRoutes(Database service) {
        this.service = service;
    }

    @Override
    public void register(Javalin app) {
        app.get("/api/locations", this::list);
        app.post("/api/locations", this::create);
        app.post("/api/locations/{id}/ratings", this::addRating);
    }

    private void list(Context ctx) {
        ctx.json(service.search(ctx.queryParam("tag")));
    }

    private void create(Context ctx) { /* ... */ }
    private void addRating(Context ctx) { /* ... */ }
}