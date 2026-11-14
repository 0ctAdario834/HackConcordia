package external;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominatimGeocoderTest {

    @Test
    void parsesFirstResultWithStringCoordinates() {
        String json = """
                [{"place_id":1,"lat":"45.5017","lon":"-73.5673","display_name":"Montreal"},
                 {"place_id":2,"lat":"46.0000","lon":"-72.0000","display_name":"Elsewhere"}]
                """;

        Optional<Coordinates> result = NominatimGeocoder.parseFirstResult(json);

        assertEquals(Optional.of(new Coordinates(45.5017, -73.5673)), result);
    }

    @Test
    void emptyArrayReturnsEmpty() {
        assertTrue(NominatimGeocoder.parseFirstResult("[]").isEmpty());
    }

    @Test
    void malformedJsonReturnsEmpty() {
        assertTrue(NominatimGeocoder.parseFirstResult("<html>rate limited</html>").isEmpty());
    }

    @Test
    void missingLonReturnsEmpty() {
        assertTrue(NominatimGeocoder.parseFirstResult("[{\"lat\":\"45.5\"}]").isEmpty());
    }

    @Test
    void nonNumericCoordinatesReturnEmpty() {
        assertTrue(NominatimGeocoder.parseFirstResult("[{\"lat\":\"abc\",\"lon\":\"1\"}]").isEmpty());
    }
}
