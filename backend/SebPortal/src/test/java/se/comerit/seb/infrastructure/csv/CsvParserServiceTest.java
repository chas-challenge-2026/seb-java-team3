package se.comerit.seb.infrastructure.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserServiceTest {
    private final CsvParserService service = new CsvParserService();

    @Test
    void parsesValidRowAndQuotedReference() {
        String csv = """
                from_account_id,to_iban,amount,reference
                123,SE4550000000058398257466,100.50,"Invoice, November"
                """;

        List<CsvParserService.ParsedCsvRow> rows = service.parse(csv);

        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertTrue(row.valid());
        assertEquals(123, row.fromAccountId());
        assertEquals("SE4550000000058398257466", row.toIban());
        assertEquals(100.50, row.amount(), 0.001);
        assertEquals("Invoice, November", row.reference());
        assertEquals("", row.error());
    }

    @Test
    void returnsErrorForInvalidAmount() {
        String csv = """
                from_account_id,to_iban,amount,reference
                123,SE4550000000058398257466,not-a-number,Invoice
                """;

        List<CsvParserService.ParsedCsvRow> rows = service.parse(csv);

        assertEquals(1, rows.size());
        assertFalse(rows.get(0).valid());
        assertEquals("Invalid amount", rows.get(0).error());
    }

    @Test
    void returnsNoRowsForHeaderOnlyCsv() {
        String csv = "from_account_id,to_iban,amount,reference\n";

        assertTrue(service.parse(csv).isEmpty());
    }

    @Test
    void rejectsNullCsv() {
        assertThrows(IllegalArgumentException.class, () -> service.parse(null));
    }

    @Test
    void rejectsCsvContainingNulCharacter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.parse("header\0,data")
        );
    }
}