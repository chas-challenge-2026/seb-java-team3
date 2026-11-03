package se.comerit.seb.service;

import org.junit.jupiter.api.Test;
import se.comerit.seb.domain.PaymentStatus;
import se.comerit.seb.dto.CreatePaymentRequest;
import se.comerit.seb.dto.PaymentResponse;
import se.comerit.seb.infrastructure.csv.CsvParserService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CsvPaymentImportServiceTest {
    private final CsvParserService parser = mock(CsvParserService.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final CsvPaymentImportService service = new CsvPaymentImportService(parser, payments);
    private final byte[] header = "from_account_id,to_iban,amount,reference\n"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void createsPaymentsUsingAuthenticatedTenantAndUser() {
        when(parser.parse(any())).thenReturn(List.of(
                new CsvParserService.ParsedCsvRow(
                        42, "SE4550000000058398257466", 100.5, "Invoice, 1", true, "")
        ));
        PaymentResponse response = new PaymentResponse(
                7L,
                new BigDecimal("100.50"),
                "SE4550000000058398257466",
                PaymentStatus.PENDING_APPROVAL,
                null,
                1,
                1
        );
        when(payments.createPayment(any(CreatePaymentRequest.class))).thenReturn(response);

        List<PaymentResponse> result = service.importCsv(header, 12L, 34L);

        assertEquals(List.of(response), result);
        verify(payments).createPayment(argThat(request ->
                request.tenantId().equals(12L)
                        && request.createdBy().equals(34L)
                        && request.fromAccountId().equals(42L)
                        && request.amount().compareTo(new BigDecimal("100.50")) == 0
                        && request.reference().equals("Invoice, 1")));
    }

    @Test
    void acceptsUtf8BomBeforeTheHeader() {
        byte[] content = ("\uFEFFfrom_account_id,to_iban,amount,reference\n")
                .getBytes(StandardCharsets.UTF_8);
        when(parser.parse(any())).thenReturn(List.of(
                new CsvParserService.ParsedCsvRow(
                        42, "SE4550000000058398257466", 100, "", true, "")
        ));

        service.importCsv(content, 12L, 34L);

        verify(parser).parse("﻿from_account_id,to_iban,amount,reference\n");
        verify(payments).createPayment(any(CreatePaymentRequest.class));
    }

    @Test
    void rejectsWrongHeaderBeforeCallingParser() {
        byte[] badHeader = "iban,amount,reference,account\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> service.importCsv(badHeader, 12L, 34L));

        verifyNoInteractions(parser, payments);
    }

    @Test
    void rejectsInvalidRowsBeforeCreatingAnyPayment() {
        when(parser.parse(any())).thenReturn(List.of(
                new CsvParserService.ParsedCsvRow(
                        42, "SE4550000000058398257466", 100, "", true, ""),
                new CsvParserService.ParsedCsvRow(
                        42, "", 0, "", false, "Invalid amount")
        ));

        assertThrows(IllegalArgumentException.class,
                () -> service.importCsv(header, 12L, 34L));

        verifyNoInteractions(payments);
    }

    @Test
    void rejectsEmptyFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importCsv(new byte[0], 12L, 34L));
        verifyNoInteractions(parser, payments);
    }

    @Test
    void rejectsFilesOverSizeLimit() {
        byte[] oversized = new byte[CsvPaymentImportService.MAX_FILE_BYTES + 1];

        assertThrows(IllegalArgumentException.class,
                () -> service.importCsv(oversized, 12L, 34L));

        verifyNoInteractions(parser, payments);
    }
}
