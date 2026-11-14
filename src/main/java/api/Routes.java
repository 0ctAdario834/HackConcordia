package api;

import io.javalin.Javalin;

/**
 * A group of related API routes that can be added to a Javalin app.
 *
 * @see io.javalin.Javalin
 */
public interface Routes {
    /**
     * Adds this group's routes to the app.
     *
     * @param app the Javalin app to register routes on
     */
    void register(Javalin app);
}
