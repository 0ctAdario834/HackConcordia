package external;

import java.util.Optional;

public class StaticGeocoder implements Geocoder {

    private static final Coordinates DOWNTOWN_MONTREAL = new Coordinates(45.5017, -73.5673);

    /** Always returns fixed coordinates near downtown Montreal, without network access. */
    @Override
    public Optional<Coordinates> geocode(String address) {
        return Optional.of(DOWNTOWN_MONTREAL);
    }
}
