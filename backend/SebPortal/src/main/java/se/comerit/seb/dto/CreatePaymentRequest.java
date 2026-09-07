package se.comerit.seb.dto;

import java.math.BigDecimal;

// "record" är en genväg i Java (sedan Java 16) för en klass som bara
// håller data. Du slipper skriva konstruktor, getters, equals() och
// hashCode() för hand - Java genererar allt det åt dig automatiskt
// bara genom att du listar fälten. Perfekt för DTO:er som bara ska
// bära data från A till B utan egen logik.
public record CreatePaymentRequest(
        Long tenantId,
        Long fromAccountId,
        String toIban,
        BigDecimal amount,
        String reference,
        Long createdBy
) {}