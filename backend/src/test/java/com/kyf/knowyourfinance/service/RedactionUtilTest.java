package com.kyf.knowyourfinance.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the single most security-critical class in the app.
 * RedactionUtil has no Spring dependencies at all (no @Autowired, no
 * database, nothing) - it's a pure function from String to String - so
 * these are plain JUnit tests with no Spring context needed to spin up.
 * That's deliberate: keeping security logic in a dependency-free class
 * means it's this cheap and this fast to verify thoroughly.
 */
class RedactionUtilTest {

    @Test
    void redactsLongDigitRunsLikeAccountNumbers() {
        var result = RedactionUtil.redact("ACH TRANSFER REF 48217536901");

        assertTrue(result.isRedacted());
        assertEquals("ACH TRANSFER REF [REDACTED]", result.getText());
    }

    @Test
    void leavesShortStoreNumbersAlone() {
        // "STARBUCKS #4521" has only 4 digits - well under the 8-digit
        // threshold - so it should pass through completely untouched.
        var result = RedactionUtil.redact("STARBUCKS #4521");

        assertFalse(result.isRedacted());
        assertEquals("STARBUCKS #4521", result.getText());
    }

    @Test
    void leavesPlainDescriptionsUntouched() {
        var result = RedactionUtil.redact("PAYROLL DEPOSIT");

        assertFalse(result.isRedacted());
        assertEquals("PAYROLL DEPOSIT", result.getText());
    }

    @Test
    void redactsMultipleLongNumbersInOneLine() {
        var result = RedactionUtil.redact("REF 123456789 TO 987654321012");

        assertTrue(result.isRedacted());
        assertEquals("REF [REDACTED] TO [REDACTED]", result.getText());
    }

    @Test
    void handlesNullInputSafely() {
        // A malformed CSV row could theoretically produce a null
        // description before this is called - redact() should never
        // throw, just return an empty, unredacted result.
        var result = RedactionUtil.redact(null);

        assertFalse(result.isRedacted());
        assertEquals("", result.getText());
    }

    @Test
    void exactlyEightDigitsIsTheRedactionThreshold() {
        // 7 digits: a merchant reference number, left alone.
        var sevenDigits = RedactionUtil.redact("REF 1234567");
        assertFalse(sevenDigits.isRedacted());

        // 8 digits: treated as potentially sensitive, redacted.
        var eightDigits = RedactionUtil.redact("REF 12345678");
        assertTrue(eightDigits.isRedacted());
    }

    @Test
    void redactsAccountNumbersSplitUpWithDashes() {
        // Real banks often print account/card numbers broken into
        // groups like this instead of one unbroken digit run - the
        // original regex (\d{8,}) would have missed this entirely.
        var result = RedactionUtil.redact("CARD PAYMENT 4821-7536-9012");

        assertTrue(result.isRedacted());
        assertEquals("CARD PAYMENT [REDACTED]", result.getText());
    }

    @Test
    void redactsAccountNumbersSplitUpWithSpaces() {
        var result = RedactionUtil.redact("WIRE TRANSFER 4821 7536 9012");

        assertTrue(result.isRedacted());
        assertEquals("WIRE TRANSFER [REDACTED]", result.getText());
    }

    @Test
    void leavesShortDashSeparatedReferencesAlone() {
        // Only 6 digits total across the groups - under the threshold,
        // so this should be left alone, same as a short store number.
        var result = RedactionUtil.redact("REF 12-34-56");

        assertFalse(result.isRedacted());
        assertEquals("REF 12-34-56", result.getText());
    }
    @Test
    void removesEmailAndFormattedPhoneFromOneDescription() {
        var result = RedactionUtil.redact("Refund to alex+shop@example.org, call +1 (507) 555-0123.");
        assertTrue(result.isRedacted());
        assertEquals("Refund to [REDACTED], call [REDACTED].", result.getText());
    }

    @Test
    void removesDottedPhoneAndKeepsStoreNumber() {
        var result = RedactionUtil.redact("Shop #4521 507.555.0123");
        assertEquals("Shop #4521 [REDACTED]", result.getText());
    }

    // ----- what is removed -----

    @ParameterizedTest
    @ValueSource(strings = {
            "jane.doe@example.com",
            "JANE.DOE@EXAMPLE.COM",
            "Jane.Doe@Example.Com",
            "jane+rent@example.com",
            "JANE+RENT.2026@MAIL.EXAMPLE.CO.UK",
            "first_last@sub-domain.example.org",
            "o'brien@example.com"
    })
    void removesEmailAddresses(String email) {
        var result = RedactionUtil.redact("PAYMENT FROM " + email + " RECEIVED");

        assertTrue(result.isRedacted());
        assertEquals("PAYMENT FROM [REDACTED] RECEIVED", result.getText());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "123-456-7890",
            "507-555-0123",
            "507.555.0123",
            "507 555 0123",
            "5075550123",
            "(507) 555-0123",
            "(507)555-0123",
            "1-507-555-0123",
            "+1 507 555 0123",
            "+1.507.555.0123",
            "+1 (507) 555-0123",
            "+15075550123"
    })
    void removesUsPhoneNumbers(String phone) {
        var result = RedactionUtil.redact("SUPPORT " + phone + " THANKS");

        assertTrue(result.isRedacted());
        assertEquals("SUPPORT [REDACTED] THANKS", result.getText());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4111111111111111",
            "4111 1111 1111 1111",
            "4111-1111-1111-1111",
            "378282246310005",
            "3782 822463 10005",
            "6011000990139424123"
    })
    void removesCardLikeNumbers(String number) {
        var result = RedactionUtil.redact("CARD " + number + " PURCHASE");

        assertTrue(result.isRedacted());
        assertEquals("CARD [REDACTED] PURCHASE", result.getText());
    }

    // ----- known misses -----
    //
    // The tests below describe what redaction does NOT remove today. They are
    // limits of matching patterns in text, written down so nobody assumes more
    // protection than there is. A passing test here is not a good result. If
    // redaction is improved, move the case into one of the tests above.

    @ParameterizedTest
    @ValueSource(strings = {
            "ZELLE PAYMENT TO JANE DOE",
            "CHECK FROM ALEX Q SAMPLE"
    })
    void knownMissPeoplesNamesAreKept(String description) {
        assertUnchanged(description);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DEPOSIT 123 MAIN ST APT 4B SPRINGFIELD",
            "RENT 45 N OAK AVENUE UNIT 7 MARSHALL MN 56258"
    })
    void knownMissStreetAddressesAreKept(String description) {
        assertUnchanged(description);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CARD ENDING 4821",
            "TRANSFER TO ACCT XXXX1234",
            "ACCT *1234567"
    })
    void knownMissShortAccountEndingsAreKept(String description) {
        assertUnchanged(description);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CALL +44.20.7946.0958",
            "CALL 01.23.45.67.89"
    })
    void knownMissSomeNonUsPhoneNumbersAreKept(String description) {
        assertUnchanged(description);
    }

    // Other numbers from outside the US are only caught because they happen to
    // contain 8 or more digits, and the country or area code can be left over.
    @ParameterizedTest
    @CsvSource({
            "CALL +44 20 7946 0958, CALL +[REDACTED]",
            "CALL +977 984-1234567, CALL +977 [REDACTED]",
            "CALL (020) 7946 0958, CALL (020) [REDACTED]"
    })
    void knownMissSomeNonUsPhoneNumbersAreOnlyPartlyRemoved(String description, String expected) {
        assertEquals(expected, RedactionUtil.redact(description).getText());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CALL 555-0123",
            "CALL 507/555/0123",
            "CALL 507-555-0123X45"
    })
    void knownMissLocalSlashedAndExtensionPhoneNumbersAreKept(String description) {
        assertUnchanged(description);
    }

    // These can hold a full account number, so they matter more than the rest.
    @ParameterizedTest
    @ValueSource(strings = {
            "ACCT123456789",
            "XXXXXX123456789",
            "ACCT 4821.7536.9012",
            "ACCT 4821  7536  9012"
    })
    void knownMissLongNumbersJoinedToLettersOrSplitByDotsOrDoubleSpacesAreKept(String description) {
        assertUnchanged(description);
    }

    @Test
    void knownMissSpelledOutEmailAddressesAreKept() {
        assertUnchanged("CONTACT jane at example dot com");
    }

    private static void assertUnchanged(String description) {
        var result = RedactionUtil.redact(description);

        assertFalse(result.isRedacted());
        assertEquals(description, result.getText());
    }
}
