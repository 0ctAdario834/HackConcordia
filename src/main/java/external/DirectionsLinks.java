package external;

/**
 * Builds links to external map services for directions.
 *
 * <p>Utility class; not meant to be instantiated.</p>
 */
public final class DirectionsLinks {

    private DirectionsLinks() {
    }

    /**
     * Returns a Google Maps directions URL with the given point as destination.
     *
     * <p>The origin is left to Google Maps, which uses the viewer's current location.</p>
     *
     * @param lat destination latitude
     * @param lon destination longitude
     * @return a {@code https://www.google.com/maps/dir/} URL
     */
    public static String directionsUrl(double lat, double lon) {
        return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon;
    }
}
