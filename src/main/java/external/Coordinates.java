package external;

/**
 * A geographic point in decimal degrees (WGS 84).
 *
 * @param lat latitude, north-positive
 * @param lon longitude, east-positive
 */
public record Coordinates(double lat, double lon) {
}
