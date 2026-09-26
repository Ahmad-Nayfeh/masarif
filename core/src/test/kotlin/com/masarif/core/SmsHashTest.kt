package com.masarif.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SmsHashTest {
    private val body = "Purchase by mada Pay\nAmount:47 SAR\nMada card:4321*\nAt:ALDREES Station Company\nOn:26-09-21 15:58"

    @Test
    fun `same body and time give same hash`() {
        assertEquals(SmsHash.compute(body, 1000L), SmsHash.compute(body, 1000L))
        assertEquals(64, SmsHash.compute(body, 1000L).length)
    }

    @Test
    fun `line ending and trailing whitespace differences do not change hash`() {
        assertEquals(SmsHash.compute(body, 1000L), SmsHash.compute(body.replace("\n", "\r\n") + "\n", 1000L))
    }

    @Test
    fun `different time or body gives different hash`() {
        assertNotEquals(SmsHash.compute(body, 1000L), SmsHash.compute(body, 1001L))
        assertNotEquals(SmsHash.compute(body, 1000L), SmsHash.compute(body.replace("47", "48"), 1000L))
    }
}
