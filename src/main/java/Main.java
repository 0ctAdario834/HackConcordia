import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class Main {

    public static Javalin createApp() {
        return Javalin.create(config ->
            config.staticFiles.add("/public", Location.CLASSPATH)
        );
    }

    public static void main(String[] args) {
        createApp().start(7000);
    }
}
