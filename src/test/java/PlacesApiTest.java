import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for GET/POST /api/places against a throwaway SQLite file. */
class PlacesApiTest {

    private static Javalin app;
    private static HttpClient client;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws Exception {
        Path dbFile = Files.createTempFile("places-test", ".db");
        dbFile.toFile().deleteOnExit();
        System.setProperty("app.db.url", "jdbc:sqlite:" + dbFile.toAbsolutePath());

        app = Main.createApp().start(0);
        client = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + app.port();

        // Seed places for the filter tests. Names are distinctive so other tests don't interfere.
        send("POST", "/api/places", json("Filter Alpha", "sunny reading room", "[\"Study\",\"Library\"]"));
        send("POST", "/api/places", json("Filter Beta", "espresso and cake", "[\"Café\"]"));
        send("POST", "/api/places", json("Filter Gamma", "hot lunches", "[\"Cafeteria\"]"));
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    private static HttpResponse<String> send(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String json(String name, String description, String tagsJson) {
        return "{\"name\": \"" + name + "\", \"description\": \"" + description + "\", \"tags\": " + tagsJson + "}";
    }

    @Test
    void listStartsAsJsonArray() throws Exception {
        HttpResponse<String> response = send("GET", "/api/places", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(response.body().trim().startsWith("["));
    }

    @Test
    void createPlaceReturns201WithSavedPlace() throws Exception {
        HttpResponse<String> response = send("POST", "/api/places",
                json("Quiet Corner", "Good for exams", "[\"Study\",\"Library\"]"));

        assertEquals(201, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("\"name\":\"Quiet Corner\""), body);
        assertTrue(body.contains("\"description\":\"Good for exams\""), body);
        assertTrue(body.contains("\"Study\"") && body.contains("\"Library\""), body);
        assertTrue(body.contains("\"id\":"), body);
    }

    @Test
    void createdPlaceAppearsInList() throws Exception {
        send("POST", "/api/places", json("Espresso Bar", "Strong coffee", "[\"Café\"]"));

        String list = send("GET", "/api/places", null).body();
        assertTrue(list.contains("\"name\":\"Espresso Bar\""), list);
        assertTrue(list.contains("\"Café\""), list);
    }

    @Test
    void nameIsTrimmed() throws Exception {
        HttpResponse<String> response = send("POST", "/api/places", json("  Padded  ", "", "[]"));
        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"name\":\"Padded\""), response.body());
    }

    @Test
    void descriptionIsOptional() throws Exception {
        HttpResponse<String> response = send("POST", "/api/places", "{\"name\": \"No Details\"}");
        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"description\":\"\""), response.body());
        assertTrue(response.body().contains("\"tags\":[]"), response.body());
    }

    @Test
    void missingNameIsRejected() throws Exception {
        assertEquals(400, send("POST", "/api/places", "{\"description\": \"no name\"}").statusCode());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        assertEquals(400, send("POST", "/api/places", json("   ", "x", "[]")).statusCode());
    }

    @Test
    void tooLongNameIsRejected() throws Exception {
        String longName = "a".repeat(101);
        assertEquals(400, send("POST", "/api/places", json(longName, "", "[]")).statusCode());
    }

    @Test
    void tooLongDescriptionIsRejected() throws Exception {
        String longDescription = "b".repeat(501);
        assertEquals(400, send("POST", "/api/places", json("Name", longDescription, "[]")).statusCode());
    }

    @Test
    void unknownTagIsRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/api/places", json("Name", "", "[\"Gym\"]"));
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Unknown tag: Gym"), response.body());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        assertEquals(400, send("POST", "/api/places", "{not json").statusCode());
    }

    @Test
    void rejectedPlaceIsNotSaved() throws Exception {
        send("POST", "/api/places", json("Should Not Exist", "", "[\"Gym\"]"));
        String list = send("GET", "/api/places", null).body();
        assertFalse(list.contains("Should Not Exist"), list);
    }

    // --- filtering -----------------------------------------------------------

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String filter(String query) throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/api/places?" + query, null);
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    @Test
    void searchMatchesNameCaseInsensitively() throws Exception {
        String body = filter("q=" + enc("ALPHA"));
        assertTrue(body.contains("Filter Alpha"), body);
        assertFalse(body.contains("Filter Beta"), body);
    }

    @Test
    void searchMatchesDescription() throws Exception {
        String body = filter("q=" + enc("sunny"));
        assertTrue(body.contains("Filter Alpha"), body);
        assertFalse(body.contains("Filter Gamma"), body);
    }

    @Test
    void searchTreatsPercentLiterally() throws Exception {
        // "%" must not act as a wildcard that matches every place.
        String body = filter("q=" + enc("%"));
        assertFalse(body.contains("Filter Alpha"), body);
        assertFalse(body.contains("Filter Beta"), body);
    }

    @Test
    void searchTreatsUnderscoreLiterally() throws Exception {
        String body = filter("q=" + enc("_"));
        assertFalse(body.contains("Filter"), body);
    }

    @Test
    void blankSearchReturnsEverything() throws Exception {
        String body = filter("q=" + enc("   "));
        assertTrue(body.contains("Filter Alpha") && body.contains("Filter Beta"), body);
    }

    @Test
    void tagFilterMatchesAnySelectedTag() throws Exception {
        // OR: Study (Alpha) or Café (Beta), but not Cafeteria (Gamma)
        String body = filter("tags=" + enc("Study") + "&tags=" + enc("Café"));
        assertTrue(body.contains("Filter Alpha"), body);
        assertTrue(body.contains("Filter Beta"), body);
        assertFalse(body.contains("Filter Gamma"), body);
    }

    @Test
    void tagFilterMatchesWholeTagsOnly() throws Exception {
        // "Café" must not match a place tagged "Cafeteria"
        String body = filter("tags=" + enc("Café"));
        assertTrue(body.contains("Filter Beta"), body);
        assertFalse(body.contains("Filter Gamma"), body);
    }

    @Test
    void searchAndTagsCombineWithAnd() throws Exception {
        // "cake" is only in Beta, which is not tagged Study
        String body = filter("q=" + enc("cake") + "&tags=" + enc("Study"));
        assertFalse(body.contains("Filter"), body);
    }

    @Test
    void unknownTagInFilterIsRejected() throws Exception {
        assertEquals(400, send("GET", "/api/places?tags=" + enc("Gym"), null).statusCode());
    }

    @Test
    void htmlInNameIsStoredAsText() throws Exception {
        HttpResponse<String> response = send("POST", "/api/places", json("<b>bold</b>", "", "[]"));
        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("<b>bold</b>"), response.body());
    }
}
