package external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * {@link Geocoder} backed by the public Nominatim search API (OpenStreetMap).
 *
 * <p>Nominatim's usage policy requires an identifying User-Agent and no more than one
 * request per second, so this class sends a User-Agent header and throttles itself to
 * one request per {@link #MIN_INTERVAL_MS} milliseconds. Results are limited to Canada.</p>
 *
 * <p>Every failure (network error, timeout, non-200 response, bad JSON, no result)
 * is reported as an empty {@link Optional} rather than an exception.</p>
 */
public class NominatimGeocoder implements Geocoder {

    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    /** Identifies this app to Nominatim, as its usage policy requires. */
    private static final String USER_AGENT = "Studious/1.0 (magnite50@gmail.com)/";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Minimum gap between requests, in milliseconds, to respect Nominatim's rate limit. */
    private static final long MIN_INTERVAL_MS = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private long lastRequestMillis = 0;

    /**
     * Geocodes a Canadian address via Nominatim.
     *
     * <p>Blocks for up to the throttle interval plus the request timeout. Returns the
     * first result only.</p>
     *
     * @param address free-text address; {@code null} or blank returns empty without a request
     * @return the coordinates of the best match, or empty if there is no match or the lookup failed
     */
    @Override
    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            throttle();
            String url = SEARCH_URL
                    + "?q=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                    + "&format=json&limit=1&countrycodes=ca";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parseFirstResult(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Blocks until at least {@link #MIN_INTERVAL_MS} has passed since the previous request.
     *
     * <p>Synchronized so concurrent callers take turns.</p>
     *
     * @throws InterruptedException if the wait is interrupted
     */
    private synchronized void throttle() throws InterruptedException {
        long wait = lastRequestMillis + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastRequestMillis = System.currentTimeMillis();
    }

    /**
     * Parses the first element of a Nominatim JSON array.
     *
     * <p>Nominatim returns {@code lat} and {@code lon} as strings, so they are converted
     * with {@link Double#parseDouble(String)}.</p>
     *
     * @param json response body from Nominatim
     * @return the first result's coordinates, or empty if the body is not a usable result
     */
    static Optional<Coordinates> parseFirstResult(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || !root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = root.get(0);
            JsonNode lat = first.get("lat");
            JsonNode lon = first.get("lon");
            if (lat == null || lon == null) {
                return Optional.empty();
            }
            return Optional.of(new Coordinates(
                    Double.parseDouble(lat.asText()),
                    Double.parseDouble(lon.asText())));
        } catch (JsonProcessingException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
