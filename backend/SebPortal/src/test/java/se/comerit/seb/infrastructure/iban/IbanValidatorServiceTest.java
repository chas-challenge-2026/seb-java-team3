package se.comerit.seb.infrastructure.iban;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IbanValidatorServiceTest {

    private final IbanValidatorService validator = new IbanValidatorService(false);

    @Test
    void testValidSwedishIban() {
        assertTrue(validator.validateIban("SE4550000000058398257466"));
    }

    @Test
    void testValidIbanWithSpaces() {
        assertTrue(validator.validateIban("SE45 5000 0000 0583 9825 7466"));
    }

    @Test
    void testInvalidChecksum() {
        assertFalse(validator.validateIban("SE4450000000058398257466"));
    }

    @Test
    void testValidBic() {
        assertTrue(validator.validateBic("HANDSE22"));
    }

    @Test
    void testInvalidBic() {
        assertFalse(validator.validateBic("INVALID"));
    }
}
