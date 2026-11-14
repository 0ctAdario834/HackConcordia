package api;

import io.javalin.Javalin;

public interface Routes {
    void register(Javalin app);
}