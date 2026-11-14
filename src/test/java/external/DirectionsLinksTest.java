package external;

import external.DirectionsLinks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectionsLinksTest {

    @Test
    void buildsGoogleMapsDirectionsUrl() {
        assertEquals(
                "https://www.google.com/maps/dir/?api=1&destination=45.5017,-73.5673",
                DirectionsLinks.directionsUrl(45.5017, -73.5673));
    }
}
