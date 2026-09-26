package com.masarif.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MerchantCleanerTest {

    @Test
    fun `removes branch numbers and cities`() {
        assertEquals("PANDA", MerchantCleaner.clean("PANDA 0412 RIYADH"))
        assertEquals("بنده", MerchantCleaner.clean("بنده فرع 12"))
        assertEquals("ALOTHAIM MARKETS", MerchantCleaner.clean("ALOTHAIM MARKETS #77 JEDDAH"))
    }

    @Test
    fun `removes symbols and collapses spaces`() {
        assertEquals("STC Jawwy", MerchantCleaner.clean("STC/Jawwy***"))
        assertEquals("Sanabel Foodstuff Groce", MerchantCleaner.clean("  Sanabel   Foodstuff  Groce "))
    }

    @Test
    fun `keeps sample merchants intact`() {
        assertEquals("ALDREES Station Company", MerchantCleaner.clean("ALDREES Station Company"))
        assertEquals("tamwinat alnoor", MerchantCleaner.clean("tamwinat alnoor"))
        assertEquals("Zain Recharge", MerchantCleaner.clean("Zain Recharge"))
        assertEquals("GIVEBOX", MerchantCleaner.clean("GIVEBOX"))
        assertEquals("SKYLINE", MerchantCleaner.clean("SKYLINE"))
    }

    @Test
    fun `key is lowercase with unified arabic letters`() {
        assertEquals("panda", MerchantCleaner.key("PANDA 0412 RIYADH"))
        assertEquals("بنده", MerchantCleaner.key("بندة فرع 12"))
        assertEquals("احسان", MerchantCleaner.key("إحسان"))
    }

    @Test
    fun `purely numeric name falls back to raw`() {
        assertEquals("12345", MerchantCleaner.clean("12345"))
    }
}
