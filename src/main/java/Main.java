import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

void main() {
    Javalin.create(config ->
        config.staticFiles.add("/public", Location.CLASSPATH)
    ).start(7000);
}
