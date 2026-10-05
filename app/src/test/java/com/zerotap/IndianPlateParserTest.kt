package com.zerotap

import com.zerotap.domain.vision.IndianPlateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndianPlateParserTest {

    @Test
    fun testBharatSeriesExtraction() {
        val raw = "IND 22 BH 6517 A"
        val parsed = IndianPlateParser.parse(raw)
        assertEquals("22BH6517A", parsed)
    }

    @Test
    fun testBharatSeriesArtifactSanitization() {
        // Optical glare / diagonal leg artifact from digit '7' prefixing 'L' to 'A'
        val rawWithArtifact = "22 BH 6517 LA"
        val parsed = IndianPlateParser.parse(rawWithArtifact)
        assertEquals("22BH6517A", parsed)
    }

    @Test
    fun testStandardStatePlateExtraction() {
        val raw = "TN 07 CB 1234"
        val parsed = IndianPlateParser.parse(raw)
        assertEquals("TN07CB1234", parsed)
    }

    @Test
    fun testStandardPlateTruncationBeyondFourDigits() {
        // Frame / border characters after the 4-digit number should be truncated
        val rawWithBorder = "KA 05 MH 8821 XYZ"
        val parsed = IndianPlateParser.parse(rawWithBorder)
        assertEquals("KA05MH8821", parsed)
    }

    @Test
    fun testSingleDigitRTOFormatting() {
        val raw = "DL 1 AB 1234"
        val parsed = IndianPlateParser.parse(raw)
        assertEquals("DL01AB1234", parsed)
    }

    @Test
    fun testPositionalConfusionCorrection() {
        // Letter 'O' in RTO slot, 'S' in digit slot
        val raw = "MH OS DE 1433"
        val parsed = IndianPlateParser.parse(raw)
        assertEquals("MH05DE1433", parsed)
    }

    @Test
    fun testNonPlateReturnsNull() {
        assertNull(IndianPlateParser.parse("HELLO WORLD"))
        assertNull(IndianPlateParser.parse("MY PALM AND FINGERS"))
        assertNull(IndianPlateParser.parse("DESK TABLE"))
        assertNull(IndianPlateParser.parse(""))
    }
}
