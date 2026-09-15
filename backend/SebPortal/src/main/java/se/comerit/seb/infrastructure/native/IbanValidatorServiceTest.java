package se.comerit.seb.infrastructure.native;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IbanValidatorServiceTest {
    
    @Autowired
    private IbanValidatorService validator;

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