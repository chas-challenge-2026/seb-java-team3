package se.comerit.seb.infrastructure.csv;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvParserService {
    public List<ParsedCsvRow> parse(String csv) {
        if (csv == null) {
            throw new IllegalArgumentException("CSV content must not be null");
        }
        if (csv.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("CSV content contains a NUL character");
        }

        byte[] content = csv.getBytes(StandardCharsets.UTF_8);
        IntByReference rowCount = new IntByReference();

        Pointer rowsPointer =
                CsvParserLib.INSTANCE.parse_csv(content, content.length, rowCount);

        if (rowsPointer == null) {
            throw new IllegalStateException("The native CSV parser failed");
        }

        try {
            int count = rowCount.getValue();
            if (count < 0 || count > 100_000) {
                throw new IllegalStateException("The native parser returned an invalid row count");
            }

            if (count == 0) {
                return List.of();
            }

            CsvParserLib.CsvRow firstRow = new CsvParserLib.CsvRow(rowsPointer);
            CsvParserLib.CsvRow[] nativeRows =
                    (CsvParserLib.CsvRow[]) firstRow.toArray(count);

            List<ParsedCsvRow> result = new ArrayList<>(count);

            for (CsvParserLib.CsvRow row : nativeRows) {
                row.read();

                result.add(new ParsedCsvRow(
                        row.fromAccountId,
                        decode(row.toIban),
                        row.amount,
                        decode(row.reference),
                        row.valid == 1,
                        decode(row.error)
                ));
            }

            return List.copyOf(result);
        } finally {
            CsvParserLib.INSTANCE.free_csv_rows(rowsPointer);
        }
    }

    private static String decode(byte[] bytes) {
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    public record ParsedCsvRow(
            int fromAccountId,
            String toIban,
            double amount,
            String reference,
            boolean valid,
            String error
    ) {}
}