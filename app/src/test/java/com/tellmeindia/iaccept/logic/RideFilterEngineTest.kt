package com.tellmeindia.iaccept.logic

import org.junit.Assert.*
import org.junit.Test

class RideFilterEngineTest {

    private val engine = RideFilterEngine()

    @Test
    fun testParseNotification_Standard() {
        val title = "New Ride Request"
        val text = "Fare: ₹150, Distance: 5.2 km"
        
        val result = engine.parseNotification(title, text)
        
        assertNotNull(result)
        assertEquals(150, result?.fare)
        assertEquals(5.2, result?.distance ?: 0.0, 0.01)
    }

    @Test
    fun testParseNotification_AlternativeFormat() {
        val title = "Ride Alert"
        val text = "You have a new ride for ₹ 200 at 10km distance"
        
        val result = engine.parseNotification(title, text)
        
        assertNotNull(result)
        assertEquals(200, result?.fare)
        assertEquals(10.0, result?.distance ?: 0.0, 0.01)
    }

    @Test
    fun testMatches_Positive() {
        val info = RideInfo(150, 5.0, "Title", "Text")
        assertTrue(engine.matches(info, 100, 10.0))
    }

    @Test
    fun testMatches_NegativeFare() {
        val info = RideInfo(80, 5.0, "Title", "Text")
        assertFalse(engine.matches(info, 100, 10.0))
    }

    @Test
    fun testMatches_NegativeDistance() {
        val info = RideInfo(150, 15.0, "Title", "Text")
        assertFalse(engine.matches(info, 100, 10.0))
    }
}
