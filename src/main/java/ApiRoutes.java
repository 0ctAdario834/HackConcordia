import api.Routes;
import io.javalin.Javalin;

import java.util.List;
import java.util.Map;




public class ApiRoutes {
    private final List<Routes> groups;

    public ApiRoutes(Routes... groups) {
        this.groups = List.of(groups);
    }

    public void registerAll(Javalin app) {
        groups.forEach(g -> g.register(app));
        app.exception(Exception.class, (e, ctx) ->
                ctx.status(500).json(Map.of("error", String.valueOf(e.getMessage()))));
    }
}