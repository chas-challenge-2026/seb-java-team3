package se.comerit.seb.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApprovalStepStatusConverter implements AttributeConverter<ApprovalStepStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApprovalStepStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public ApprovalStepStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : ApprovalStepStatus.valueOf(dbValue.toUpperCase());
    }
}