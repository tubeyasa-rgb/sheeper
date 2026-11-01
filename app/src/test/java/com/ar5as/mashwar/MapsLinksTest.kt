package com.ar5as.mashwar
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
class MapsLinksTest {
    @Test fun directionsEncodeOriginAndArabicDestination() {
        val link = MapsLinks.forTrip("30.123456,31.654321", "ميدان التحرير & شارع")!!
        assertTrue(link.startsWith("https://www.google.com/maps/dir/?api=1&"))
        assertTrue(link.contains("origin=30.123456%2C31.654321"))
        assertTrue(URLDecoder.decode(link, "UTF-8").contains("destination=ميدان التحرير & شارع"))
        assertFalse(link.contains("destination=ميدان التحرير & شارع"))
    }
    @Test fun blankOriginSearchesAndBlankDestinationRejected() {
        assertTrue(MapsLinks.forTrip(" ", "Cairo")!!.contains("/search/?api=1&query=Cairo"))
        assertNull(MapsLinks.forTrip("Cairo", " "))
    }
}
