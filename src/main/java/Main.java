import external.Geocoder;
import external.NominatimGeocoder;
import io.javalin.Javalin;

void main() {
    Geocoder geocoder = new NominatimGeocoder();

    Javalin app = Javalin.create().start(7000);
    app.get("/", ctx -> ctx.result("Hello World"));
    app.start();
}
