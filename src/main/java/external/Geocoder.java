package external;

import java.util.Optional;

public interface Geocoder {

    /**
     * Resolves a free-text address to coordinates.
     *
     * @return the coordinates, or empty if the address could not be resolved
     */
    Optional<Coordinates> geocode(String address);
}
