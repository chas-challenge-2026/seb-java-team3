package se.comerit.seb.service;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailAddressTest {

    @Test
    void normalize_trimsAndLowercases() {
        assertEquals("lisa@malmobygg.se", EmailAddress.normalize("  Lisa@MalmoBygg.SE \t"));
    }

    @Test
    void normalize_leavesAnAlreadyNormalAddressAlone() {
        assertEquals("johan@malmobygg.se", EmailAddress.normalize("johan@malmobygg.se"));
    }

    @Test
    void normalize_keepsNullAsNull_andBlankAsEmpty() {
        assertNull(EmailAddress.normalize(null));
        assertEquals("", EmailAddress.normalize("   "));
    }

    // Locale.ROOT: med turkiskt språk på datorn blir "I" annars ett prickfritt "ı", och adressen matchar inte.
    @Test
    void normalize_doesNotDependOnTheComputersLanguage() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("info@example.se", EmailAddress.normalize("INFO@EXAMPLE.SE"));
        } finally {
            Locale.setDefault(before);
        }
    }
}
