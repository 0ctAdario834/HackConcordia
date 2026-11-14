import api.Routes;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;




/**
 * Registers groups of routes on a Javalin app and installs a catch-all error handler.
 *
 * <p>Each {@link Routes} group adds its own endpoints. The catch-all handler turns any
 * uncaught exception into a JSON {@code 500} response.</p>
 */
public class ApiRoutes {
    private final List<Routes> groups;

    /**
     * @param groups the route groups to register, in order
     */
    public ApiRoutes(Routes... groups) {
        this.groups = List.of(groups);
    }

    /**
     * Registers every route group, then installs the error handler.
     *
     * @param app the Javalin app to add routes to
     */
    public void registerAll(Javalin app) {
        groups.forEach(g -> g.register(app));
        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("error", String.valueOf(e.getMessage()))));
    }
}
