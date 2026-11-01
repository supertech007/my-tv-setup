package com.mytvsetup.launcher.ui.settings

import org.junit.Assert.*
import org.junit.Test

class CitySearchTest {
    @Test fun parsesLocationsAndIgnoresInvalidCoordinates() {
        val cities = parseCities("""{"results":[
            {"name":"Mumbai","admin1":"Mumbai","country":"India","latitude":19.08,"longitude":72.88},
            {"name":"Bad","latitude":91,"longitude":0},
            {"name":"Missing","longitude":77},
            {"name":"London","latitude":51.5,"longitude":-0.12,"extra":"ignored"}
        ]}""")
        assertEquals(listOf(City("Mumbai", "Mumbai", "India", 19.08, 72.88),
            City("London", "", "", 51.5, -0.12)), cities)
    }
    @Test fun noResultsIsAnEmptyList() { assertTrue(parseCities("{}").isEmpty()) }
    @Test fun cityQueryIsEncodedAndLimitedToFiveResults() {
        val url = citySearchUrl("Navi Mumbai & MMR")
        assertEquals("geocoding-api.open-meteo.com", url.host)
        assertEquals("/v1/search", url.encodedPath)
        assertEquals("Navi Mumbai & MMR", url.queryParameter("name"))
        assertEquals("5", url.queryParameter("count"))
    }
}
