package com.masarif.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * اختبارات المحلّل: عينات مصطنعة تحاكي صيغ رسائل الإنماء (كل الأسماء والأرقام والمبالغ وهمية).
 */
class SmsParserTest {

    private val zone: ZoneId = ZoneId.of("Asia/Riyadh")
    private val receivedAt = 1_800_000_000_000L

    private fun millisOf(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun parsed(body: String): ParsedSms {
        val r = SmsParser.parse(body, receivedAt, zone)
        assertIs<ParseResult.Parsed>(r, "expected Parsed but got $r for:\n$body")
        return r.sms
    }

    private fun ignored(body: String) = assertEquals(ParseResult.Ignored, SmsParser.parse(body, receivedAt, zone), "should be ignored:\n$body")

    // ---------- النوع 1: شراء حضوري (Purchase by mada Pay) ----------

    @Test
    fun `pos sample 1 aldrees`() {
        val sms = parsed("Purchase by mada Pay\nAmount:47 SAR\nMada card:4321*\nAt:ALDREES Station Company\nOn:26-09-21 15:58")
        assertEquals(4700L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.POS, sms.channel)
        assertEquals("ALDREES Station Company", sms.merchantRaw)
        assertEquals("4321", sms.cardLast4)
        assertNull(sms.country)
        assertEquals(millisOf(2026, 9, 21, 15, 58), sms.dateTimeMillis)
        assertTrue(sms.dateFromMessage)
        assertEquals("pos_purchase", sms.typeName)
    }

    @Test
    fun `pos sample 2 tamwinat with decimals`() {
        val sms = parsed("Purchase by mada Pay\nAmount:18.27 SAR\nMada card:4321*\nAt:tamwinat alnoor\nOn:26-09-20 12:24")
        assertEquals(1827L, sms.amountHalalas)
        assertEquals("tamwinat alnoor", sms.merchantRaw)
        assertEquals(millisOf(2026, 9, 20, 12, 24), sms.dateTimeMillis)
    }

    @Test
    fun `pos sample 3 sanabel`() {
        val sms = parsed("Purchase by mada Pay\nAmount:5.50 SAR\nMada card:4321*\nAt:Sanabel Foodstuff Groce\nOn:26-09-20 11:49")
        assertEquals(550L, sms.amountHalalas)
        assertEquals("Sanabel Foodstuff Groce", sms.merchantRaw)
    }

    // ---------- شراء حضوري Atheer ----------

    @Test
    fun `atheer pos purchase with four digit year and unpadded month`() {
        val sms = parsed("mada Atheer POS Purchase\nAmount: 15 SAR\nmada card: 4321*\nAccount: **1000\nAt: Modern Trading Co\nOn: 2025-8-21 10:19")
        assertEquals(1500L, sms.amountHalalas)
        assertEquals(Channel.POS, sms.channel)
        assertEquals("Modern Trading Co", sms.merchantRaw)
        assertEquals("4321", sms.cardLast4)
        assertEquals(millisOf(2025, 8, 21, 10, 19), sms.dateTimeMillis)
        assertEquals("pos_atheer", sms.typeName)
    }

    @Test
    fun `atheer short purchase with amount in header and bare date line`() {
        val sms = parsed("mada Atheer Purchase 6.25 SAR\nCard 4321*\nAt GREENWAY\n25-12-25 01:57")
        assertEquals(625L, sms.amountHalalas)
        assertEquals(Channel.POS, sms.channel)
        assertEquals("GREENWAY", sms.merchantRaw)
        assertEquals("4321", sms.cardLast4)
        assertEquals(millisOf(2025, 12, 25, 1, 57), sms.dateTimeMillis)
        assertEquals("pos_atheer_short", sms.typeName)
        assertEquals("City Pharmacy 320", parsed("mada Atheer Purchase 8 SAR\nCard 4321*\nAt City Pharmacy 320\n25-12-25 01:31").merchantRaw)
    }

    // ---------- شراء أونلاين ----------

    @Test
    fun `online local sample zain`() {
        val sms = parsed("Online Purchase\nAmount:80.50 SAR\nmada card:4321*\nAccount:*1000\nat:Zain Recharge\nOn:26-09-20 15:05")
        assertEquals(8050L, sms.amountHalalas)
        assertEquals(Channel.ONLINE, sms.channel)
        assertEquals("Zain Recharge", sms.merchantRaw)
        assertNull(sms.country)
    }

    @Test
    fun `online local sample givebox one riyal is a real transaction`() {
        val sms = parsed("Online Purchase\nAmount:1 SAR\nmada card:4321*\nAccount:*1000\nat:GIVEBOX\nOn:26-09-22 05:34")
        assertEquals(100L, sms.amountHalalas)
        assertEquals("GIVEBOX", sms.merchantRaw)
    }

    @Test
    fun `online international sample skyline with In line`() {
        val sms = parsed("Online Purchase 19.23 SAR\nmada Card: 4321*\nAccount: *1000\nAt: SKYLINE\nIn: United Kingdom\nOn: 26-09-20 12:05")
        assertEquals(1923L, sms.amountHalalas)
        assertEquals("SKYLINE", sms.merchantRaw)
        assertEquals("United Kingdom", sms.country)
        assertEquals(millisOf(2026, 9, 20, 12, 5), sms.dateTimeMillis)
    }

    @Test
    fun `online purchase with thousands separator and From Account label`() {
        val sms = parsed("Online Purchase\nAmount: 1,150 SAR\nmada card: 4321*\nFrom Account: **1000\nat: STC Pay\nOn: 2025-03-27 21:28")
        assertEquals(115_000L, sms.amountHalalas)
        assertEquals("STC Pay", sms.merchantRaw)
        assertEquals(millisOf(2025, 3, 27, 21, 28), sms.dateTimeMillis)
        assertEquals(245_000L, parsed("Online Purchase\nAmount:2,450 SAR\nmada card:4321*\nAccount:*1000\nat:noon\nOn:26-08-19 23:13").amountHalalas)
    }

    // ---------- سحب صراف ----------

    @Test
    fun `atm withdrawal`() {
        val sms = parsed("ATM withdrawal\nmada Card:4321*\nAmount:200 SAR\nIn:BANKX\nOn:2026-5-27 15:26")
        assertEquals(20_000L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.ATM, sms.channel)
        assertEquals("صراف BANKX", sms.merchantRaw)
        assertNull(sms.country)
        assertEquals(millisOf(2026, 5, 27, 15, 26), sms.dateTimeMillis)
        assertEquals("4321", parsed("ATM withdrawal\nFrom mada Card: 4321*\nAmount: 150 SAR\nIn: BANKX\nOn: 2024-9-22 17:06").cardLast4)
    }

    // ---------- تحويلات صادرة ----------

    @Test
    fun `internal debit transfer with beneficiary label`() {
        val sms = parsed("Debit Transfer Internal\nAmount: 53.50 SAR\nTo beneficiary: KHALID M A SAEED\nTo Account *4000\nFrom Account: **1000\nOn: 2025-10-28 20:43")
        assertEquals(5350L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.TRANSFER, sms.channel)
        assertEquals("KHALID M A SAEED", sms.merchantRaw)
        assertEquals(millisOf(2025, 10, 28, 20, 43), sms.dateTimeMillis)
    }

    @Test
    fun `internal debit transfer with To label and arabic name`() {
        val sms = parsed("Debit Transfer Internal\nAmount:48.00 SAR\nTo:عبدالله محمد الأحمد\nTo Account:*4000\nOn:2026-05-16 00:36")
        assertEquals(4800L, sms.amountHalalas)
        assertEquals("عبدالله محمد الأحمد", sms.merchantRaw)
    }

    @Test
    fun `outgoing local transfer without colons and with fee line and thousands`() {
        val sms = parsed("Outgoing Local Transfer\nAmount 1,000 SAR\nFee 0.58 SAR\nTo سعيد أحمد الخالد\nTo Account: **2222\nOn 26-08-26 23:12")
        assertEquals(100_000L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.TRANSFER, sms.channel)
        assertEquals("سعيد أحمد الخالد", sms.merchantRaw)
        assertEquals(millisOf(2026, 8, 26, 23, 12), sms.dateTimeMillis)
        assertEquals("OMAR TEST", parsed("Outgoing Local Transfer\nAmount 70 SAR\nFee 0.58 SAR\nTo OMAR TEST\nTo Account: **3333\nOn 26-03-26 21:56").merchantRaw)
    }

    @Test
    fun `sarie debit transfer uses beneficiary and At as date`() {
        val sms = parsed("Debit Transfer Local Deposited Confirmation (sarie)\nTo Beneficiary: MHD SALEM\nTo Account: **5555\nBeneficiary Bank: Test Bank- RIYADH\nIBAN: **5555\nAmount: 500 SAR\nFee: 0.58 SAR\nFrom Account: **1000\nAt: 2025-09-08 21:03\nRef: **000111")
        assertEquals(50_000L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.TRANSFER, sms.channel)
        assertEquals("MHD SALEM", sms.merchantRaw)
        assertEquals(millisOf(2025, 9, 8, 21, 3), sms.dateTimeMillis)
    }

    // ---------- تحويلات واردة ----------

    @Test
    fun `internal credit transfer`() {
        val sms = parsed("Credit Transfer Internal\nAmount: 21 SAR\nTo account: *1000\nFrom: محمود سالم\nOn: 25-11-21 01:51")
        assertEquals(2100L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals(Channel.TRANSFER, sms.channel)
        assertEquals("محمود سالم", sms.merchantRaw)
        assertEquals(millisOf(2025, 11, 21, 1, 51), sms.dateTimeMillis)
    }

    @Test
    fun `incoming local transfer with blank line and At as date`() {
        val sms = parsed("Incoming Local Transfer\nAmount 17 SAR\nFrom سامي الحربي\n\nAccount **1234\nAt 26-09-11 20:10")
        assertEquals(1700L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals("سامي الحربي", sms.merchantRaw)
        assertEquals(millisOf(2026, 9, 11, 20, 10), sms.dateTimeMillis)
    }

    @Test
    fun `short incoming transfer strips account suffix from sender`() {
        val sms = parsed("Transfer Incoming 13 SAR\nFrom خالد العمري*; *4000\nOn 26-09-18 23:52")
        assertEquals(1300L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals("خالد العمري", sms.merchantRaw)
        assertEquals(245_000L, parsed("Transfer Incoming 2,450 SAR\nFrom عبدالرحمن *; *5000\nOn 26-08-20 13:55").amountHalalas)
    }

    @Test
    fun `sarie incoming funds transfer`() {
        val sms = parsed("Incoming Funds Transfer (Sarie)\nTo Account: **1000\nAmount: 312.50 SAR\nFrom: منظمة الاختبار\nFrom Account: **0007\nFrom Bank: TEST NATIONAL BANK\nAt: 2024-07-04 16:33\nRef: **000222")
        assertEquals(31_250L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals(Channel.TRANSFER, sms.channel)
        assertEquals("منظمة الاختبار", sms.merchantRaw)
        assertEquals(millisOf(2024, 7, 4, 16, 33), sms.dateTimeMillis)
    }

    // ---------- راتب / استرداد ----------

    @Test
    fun `incoming salary`() {
        val sms = parsed("Incoming salary transfer\nAmount: 9000 SAR\nAccount: **1000\nOn: 26-08-31 12:38")
        assertEquals(900_000L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals(Channel.SALARY, sms.channel)
        assertEquals("راتب", sms.merchantRaw)
    }

    @Test
    fun `reverse transaction is a refund from the merchant`() {
        val sms = parsed("Reverse Transaction\nAmount: 420 SAR\nTo mada card: 4321*\nAccount: **1000\nFrom: Sephora\nOn: 2026-09-06 14:54")
        assertEquals(42_000L, sms.amountHalalas)
        assertEquals(Direction.INCOME, sms.direction)
        assertEquals(Channel.REFUND, sms.channel)
        assertEquals("Sephora", sms.merchantRaw)
        assertEquals("4321", sms.cardLast4)
    }

    // ---------- فواتير ورسوم ----------

    @Test
    fun `bill payment with biller label`() {
        val sms = parsed("Bill Payment \nNo: 966100000000\nAmount: 7.42 SAR\nBiller: Mobily\nService: Bills\nFrom Account: 1000\nOn: 2026-04-03 05:55")
        assertEquals(742L, sms.amountHalalas)
        assertEquals(Channel.OTHER, sms.channel)
        assertEquals("Mobily", sms.merchantRaw)
        assertEquals(millisOf(2026, 4, 3, 5, 55), sms.dateTimeMillis)
    }

    @Test
    fun `bill payment inline extracts biller name`() {
        val sms = parsed("Bill Payment: Bills for Mobily - Bills\nNo: 966100000000\nAmount: 230 SAR \nOn: 2026-09-05 19:50")
        assertEquals(23_000L, sms.amountHalalas)
        assertEquals("Mobily", sms.merchantRaw)
        assertEquals("bill_payment_inline", sms.typeName)
    }

    @Test
    fun `government payment with Entity and Date labels`() {
        val sms = parsed("Amount:150.0 SAR                    \nEntity:Traffic Violation\nService:Traffic Violations by Violation ID\nDate:2026-02-02 18:22")
        assertEquals(15_000L, sms.amountHalalas)
        assertEquals(Direction.EXPENSE, sms.direction)
        assertEquals(Channel.OTHER, sms.channel)
        assertEquals("Traffic Violation", sms.merchantRaw)
        assertEquals(millisOf(2026, 2, 2, 18, 22), sms.dateTimeMillis)
    }

    // ---------- تُتجاهل: رموز التحقق حتى لو فيها مبلغ ----------

    @Test
    fun `otp message without amount is ignored`() = ignored("Please use the code: 6850\nTo: Alinma App")

    @Test
    fun `otp for online purchase with amount and merchant is ignored`() =
        ignored("Please use the code:6110\nFor card:*4321\nAmount:33.50 SAR\nMerchant:Hungerstation\nOn:26-09-25 22:26")

    @Test
    fun `internet purchase otp is ignored`() =
        ignored("To complete the internet purchase transaction\nFrom Card number: 4321\nAmount: 36.4 SAR\nMerchant: TEST STORE\nPlease use OTP:0258\nOn: 21:28 2025-09-23")

    @Test
    fun `transfer otp variants are ignored`() {
        ignored("Please use the code: 3871\nAmount: 87 SAR\nTo: Local Transfer")
        ignored("Please use the code: 4984\nAmount: 21 SAR\nTo activate: Internal Transfer\nPurpose: Personal Transfer")
        ignored("Please use the OTP 1014\nAmount: 230 SAR\nto pay a bill")
        ignored("Please use the activation code 4869\nAmount: 150 SAR\nto pay MOI fees")
    }

    @Test
    fun `advert without amount and blank message are ignored`() {
        ignored("Enjoy 20% off with Alinma cards this weekend!")
        ignored("   \n ")
    }

    // ---------- غير مفهومة ----------

    @Test
    fun `unknown format with SAR amount goes to unrecognized`() {
        assertIs<ParseResult.Unrecognized>(SmsParser.parse("Salary credited\nAmount:12000 SAR\nAccount:*1000\nOn:26-09-27 09:00", receivedAt, zone))
    }

    @Test
    fun `known header but missing merchant goes to unrecognized`() {
        assertIs<ParseResult.Unrecognized>(SmsParser.parse("Purchase by mada Pay\nAmount:10 SAR\nOn:26-09-20 10:00", receivedAt, zone))
    }

    // ---------- التسامح ----------

    @Test
    fun `merchant spanning two lines is joined with single space`() {
        assertEquals("ALDREES Station Company", parsed("Purchase by mada Pay\nAmount:47 SAR\nMada card:4321*\nAt:ALDREES Station\nCompany\nOn:26-09-21 15:58").merchantRaw)
    }

    @Test
    fun `field order does not matter`() {
        val sms = parsed("Purchase by mada Pay\nOn:26-09-21 15:58\nAt:ALDREES Station Company\nMada card:4321*\nAmount:47 SAR")
        assertEquals(4700L, sms.amountHalalas)
        assertEquals("ALDREES Station Company", sms.merchantRaw)
    }

    @Test
    fun `case and whitespace are tolerated`() {
        val sms = parsed("PURCHASE BY MADA PAY\namount : 47 sar\nMADA CARD : 4321*\nAT : Aldrees\nON : 26-09-21 15:58")
        assertEquals(4700L, sms.amountHalalas)
        assertEquals("Aldrees", sms.merchantRaw)
        assertEquals("4321", sms.cardLast4)
    }

    @Test
    fun `windows line endings are tolerated`() {
        assertEquals("ALDREES Station Company", parsed("Purchase by mada Pay\r\nAmount:47 SAR\r\nMada card:4321*\r\nAt:ALDREES Station Company\r\nOn:26-09-21 15:58").merchantRaw)
    }

    @Test
    fun `falls back to received time when date is missing or invalid`() {
        val missing = parsed("Purchase by mada Pay\nAmount:47 SAR\nAt:ALDREES")
        assertEquals(receivedAt, missing.dateTimeMillis)
        assertFalse(missing.dateFromMessage)
        val invalid = parsed("Purchase by mada Pay\nAmount:47 SAR\nAt:ALDREES\nOn:26-13-45 15:58")
        assertEquals(receivedAt, invalid.dateTimeMillis)
        assertFalse(invalid.dateFromMessage)
    }

    @Test
    fun `merchant is not cut at words like On without colon`() {
        assertEquals("Burger On Fire", parsed("Purchase by mada Pay\nAmount:12 SAR\nAt:Burger On Fire\nOn:26-09-21 15:58").merchantRaw)
    }
}
