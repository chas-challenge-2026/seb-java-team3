package se.comerit.seb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.infrastructure.csv.CsvParserService;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CsvPaymentImportService {
    public static final int MAX_FILE_BYTES = 1_000_000;
    public static final int MAX_ROWS = 500;
    private static final String EXPECTED_HEADER =
            "from_account_id,to_iban,amount,reference";

    private final CsvParserService csvParserService;
    private final PaymentService paymentService;

    public CsvPaymentImportService(CsvParserService csvParserService,
                                   PaymentService paymentService) {
        this.csvParserService = csvParserService;
        this.paymentService = paymentService;
    }

    @Transactional
    public List<PaymentResponse> importCsv(byte[] content, Long tenantId, Long userId) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("CSV-filen är tom.");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("CSV-filen får vara högst 1 MB.");
        }

        String csv = decodeUtf8(content);
        validateHeader(csv);

        List<CsvParserService.ParsedCsvRow> rows = csvParserService.parse(csv);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV-filen innehåller inga betalningar.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new IllegalArgumentException("CSV-filen får innehålla högst 500 betalningar.");
        }

        for (int index = 0; index < rows.size(); index++) {
            CsvParserService.ParsedCsvRow row = rows.get(index);
            int csvLineNumber = index + 2;
            if (!row.valid()) {
                throw new IllegalArgumentException(
                        "CSV-rad %d: %s".formatted(csvLineNumber, row.error()));
            }

            BigDecimal amount = BigDecimal.valueOf(row.amount());
            if (amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException(
                        "CSV-rad %d: Beloppet får ha högst två decimaler."
                                .formatted(csvLineNumber));
            }
        }

        return rows.stream()
                .map(row -> paymentService.createPayment(new CreatePaymentRequest(
                        tenantId,
                        (long) row.fromAccountId(),
                        row.toIban(),
                        BigDecimal.valueOf(row.amount()),
                        row.reference(),
                        userId
                )))
                .toList();
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("CSV-filen måste vara kodad som UTF-8.", ex);
        }
    }

    private static void validateHeader(String csv) {
        String firstLine = csv.lines().findFirst().orElse("");
        if (firstLine.startsWith("\uFEFF")) {
            firstLine = firstLine.substring(1);
        }
        if (!EXPECTED_HEADER.equals(firstLine.trim())) {
            throw new IllegalArgumentException(
                    "CSV-filen måste börja med kolumnerna: " + EXPECTED_HEADER);
        }
    }
}
