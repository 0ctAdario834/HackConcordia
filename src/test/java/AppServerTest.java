import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end checks of the running Javalin server, using a random free port. */
class AppServerTest {

    private static Javalin app;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        app = Main.createApp().start(0);
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String classpathResource(String name) throws IOException {
        try (InputStream in = AppServerTest.class.getResourceAsStream("/public/" + name)) {
            assertNotNull(in, "missing classpath resource: /public/" + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void rootReturns200() throws Exception {
        assertEquals(200, get("/").statusCode());
    }

    @Test
    void rootIsServedAsHtml() throws Exception {
        String contentType = get("/").headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("text/html"), "unexpected content type: " + contentType);
    }

    @Test
    void rootBodyMatchesIndexHtmlOnClasspath() throws Exception {
        assertEquals(classpathResource("index.html"), get("/").body());
    }

    @Test
    void rootPageContainsSearchInput() throws Exception {
        assertTrue(get("/").body().contains("id=\"search\""));
    }

    @Test
    void stylesheetIsServedAsCss() throws Exception {
        HttpResponse<String> response = get("/styles.css");
        assertEquals(200, response.statusCode());
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("text/css"), "unexpected content type: " + contentType);
    }

    @Test
    void stylesheetBodyMatchesClasspathResource() throws Exception {
        assertEquals(classpathResource("styles.css"), get("/styles.css").body());
    }

    @Test
    void scriptIsServed() throws Exception {
        assertEquals(200, get("/app.js").statusCode());
    }

    @Test
    void unknownPathReturns404() throws Exception {
        assertEquals(404, get("/does-not-exist").statusCode());
    }

    @Test
    void publicFolderIsNotExposedAsUrlPrefix() throws Exception {
        // Files are served from the root, so /public/... should not be reachable.
        assertEquals(404, get("/public/index.html").statusCode());
    }

    @Test
    void createAppReturnsNewInstanceEachCall() {
        Javalin first = Main.createApp();
        Javalin second = Main.createApp();
        assertNotNull(first);
        assertNotSame(first, second);
    }
}
