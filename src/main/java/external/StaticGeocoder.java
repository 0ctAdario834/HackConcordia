package external;

import java.util.Optional;

/**
 * {@link Geocoder} that ignores its input and returns a fixed location.
 *
 * <p>Useful for development and tests where no network access is available. Every
 * address resolves to downtown Montreal.</p>
 */
public class StaticGeocoder implements Geocoder {

    private static final Coordinates DOWNTOWN_MONTREAL = new Coordinates(45.5017, -73.5673);

    /**
     * Returns fixed coordinates near downtown Montreal, without network access.
     *
     * @param address ignored
     * @return always present: the downtown Montreal coordinates
     */
    @Override
    public Optional<Coordinates> geocode(String address) {
        return Optional.of(DOWNTOWN_MONTREAL);
    }
}
