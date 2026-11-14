package external;

public final class DirectionsLinks {

    private DirectionsLinks() {
    }

    /** Returns a Google Maps directions URL with the given point as destination. */
    public static String directionsUrl(double lat, double lon) {
        return "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon;
    }
}
