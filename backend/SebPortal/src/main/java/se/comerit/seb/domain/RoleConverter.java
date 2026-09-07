package se.comerit.seb.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RoleConverter implements AttributeConverter<Role, String> {

    // Körs när Java-objektet ska SPARAS till databasen
    @Override
    public String convertToDatabaseColumn(Role role) {
        if (role == null) {
            return null;
        }
        return role.name().toLowerCase(); // ATTESTANT -> "attestant"
    }

    // Körs när en databasrad ska bli ett Java-objekt (LÄSNING)
    @Override
    public Role convertToEntityAttribute(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        return Role.valueOf(dbValue.toUpperCase()); // "attestant" -> ATTESTANT
    }
}