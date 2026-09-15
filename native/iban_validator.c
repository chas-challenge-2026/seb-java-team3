#include "iban_validator.h"
#include <string.h>
#include <ctype.h>
#include <stdio.h>

/**
 * Helper: Check if a character is a valid IBAN character (A-Z, 0-9, space, hyphen)
 */
static int is_valid_iban_char(char c) {
    return (c >= 'A' && c <= 'Z') || 
           (c >= 'a' && c <= 'z') || 
           (c >= '0' && c <= '9') || 
           c == ' ' || 
           c == '-';
}

/**
 * Helper: Check if character is a valid country code character (A-Z)
 */
static int is_alpha(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
}

/**
 * Helper: Check if character is a digit
 */
static int is_digit(char c) {
    return c >= '0' && c <= '9';
}

/**
 * Helper: Normalize IBAN by removing spaces/hyphens and converting to uppercase.
 * Returns normalized IBAN length, or -1 if invalid character found.
 */
static int normalize_iban(const char* iban, char* normalized) {
    if (!iban || !normalized) return -1;
    
    // First pass: verify all characters are valid
    for (int i = 0; iban[i] != '\0'; i++) {
        if (!is_valid_iban_char(iban[i])) {
            return -1;  // Invalid character found
        }
    }
    
    // Second pass: remove spaces/hyphens and uppercase
    int pos = 0;
    for (int i = 0; iban[i] != '\0' && pos < 34; i++) {
        char c = iban[i];
        
        // Skip spaces and hyphens
        if (c == ' ' || c == '-') {
            continue;
        }
        
        // Convert to uppercase
        normalized[pos++] = toupper((unsigned char)c);
    }
    
    normalized[pos] = '\0';
    return pos;
}

/**
 * Helper: Convert IBAN to numeric string for MOD97 calculation.
 * Moves first 4 chars to end, replaces letters with digit codes.
 * A=10, B=11, ..., Z=35
 */
static int iban_to_numeric(const char* normalized, char* numeric) {
    int len = strlen(normalized);
    
    // IBAN must be 15-34 characters after normalization
    if (len < 15 || len > 34) {
        return -1;
    }
    
    // Move first 4 characters to end
    // Resulting string will be: chars[4..] + chars[0..3]
    int numeric_pos = 0;
    
    // Process characters 4 onwards
    for (int i = 4; i < len; i++) {
        char c = normalized[i];
        if (is_digit(c)) {
            numeric[numeric_pos++] = c;
        } else if (is_alpha(c)) {
            // Convert letter to numeric: A/a=10, B/b=11, ..., Z/z=35
            int val = (toupper((unsigned char)c) - 'A') + 10;
            numeric_pos += sprintf(&numeric[numeric_pos], "%d", val);
        } else {
            return -1;
        }
    }
    
    // Process first 4 characters (country code + check digits)
    for (int i = 0; i < 4; i++) {
        char c = normalized[i];
        if (is_digit(c)) {
            numeric[numeric_pos++] = c;
        } else if (is_alpha(c)) {
            int val = (toupper((unsigned char)c) - 'A') + 10;
            numeric_pos += sprintf(&numeric[numeric_pos], "%d", val);
        } else {
            return -1;
        }
    }
    
    numeric[numeric_pos] = '\0';
    return numeric_pos;
}

/**
 * Helper: Calculate mod 97 using string input to handle large numbers.
 * Implements modular arithmetic on a numeric string.
 */
static int mod97(const char* numeric_str) {
    int remainder = 0;
    
    for (int i = 0; numeric_str[i] != '\0'; i++) {
        int digit = numeric_str[i] - '0';
        remainder = (remainder * 10 + digit) % 97;
    }
    
    return remainder;
}

/**
 * Public API: Validate IBAN
 */
int validate_iban(const char* iban, int* error_out) {
    if (!iban) {
        if (error_out) *error_out = 1;
        return 0;
    }
    
    // Check original length (including spaces/hyphens) to catch extremely long inputs
    int orig_len = strlen(iban);
    if (orig_len > 60) {  // Maximum reasonable length (34 chars + spaces/hyphens)
        if (error_out) *error_out = 1;  // Invalid length
        return 0;
    }
    
    // Normalize: remove spaces/hyphens, convert to uppercase
    char normalized[36];  // 34 chars + null terminator + 1 for safety
    int norm_len = normalize_iban(iban, normalized);
    
    // Check for invalid characters (returned as -1 from normalize_iban)
    if (norm_len < 0) {
        if (error_out) *error_out = 3;  // Invalid character
        return 0;
    }
    
    // Check length after normalization
    if (norm_len < 15 || norm_len > 34) {
        if (error_out) *error_out = 1;  // Invalid length
        return 0;
    }
    
    // Check country code (first 2 characters must be A-Z)
    if (!is_alpha(normalized[0]) || !is_alpha(normalized[1])) {
        if (error_out) *error_out = 2;  // Invalid country code
        return 0;
    }
    
    // Check check digits (characters 3-4 must be digits)
    if (!is_digit(normalized[2]) || !is_digit(normalized[3])) {
        if (error_out) *error_out = 4;  // Invalid control digits
        return 0;
    }
    
    // Convert to numeric string for MOD97
    char numeric[128];
    int num_len = iban_to_numeric(normalized, numeric);
    
    if (num_len < 0) {
        if (error_out) *error_out = 3;  // Invalid character
        return 0;
    }
    
    // Calculate MOD97: should equal 1 for valid IBAN
    int remainder = mod97(numeric);
    
    if (remainder != 1) {
        if (error_out) *error_out = 4;  // MOD97 checksum failed
        return 0;
    }
    
    return 1;  // Valid
}

/**
 * Public API: Validate BIC
 */
int validate_bic(const char* bic) {
    if (!bic) return 0;
    
    int len = strlen(bic);
    
    // BIC must be 8 or 11 characters
    if (len != 8 && len != 11) {
        return 0;
    }
    
    // BICs must be uppercase — reject lowercase or mixed case
    for (int i = 0; i < len; i++) {
        char c = bic[i];
        if (c >= 'a' && c <= 'z') {
            return 0;  // Lowercase letter found
        }
    }
    
    // First 4 characters: bank code (letters only, A-Z)
    for (int i = 0; i < 4; i++) {
        if (!is_alpha(bic[i])) {
            return 0;
        }
    }
    
    // Characters 5-6: country code (letters only, A-Z, ISO 3166-1 alpha-2)
    for (int i = 4; i < 6; i++) {
        if (!is_alpha(bic[i])) {
            return 0;
        }
    }
    
    // Characters 7-8: location code (letters or digits)
    for (int i = 6; i < 8; i++) {
        if (!is_alpha(bic[i]) && !is_digit(bic[i])) {
            return 0;
        }
    }
    
    // Characters 9-11 (if present): branch code (letters or digits)
    for (int i = 8; i < len; i++) {
        if (!is_alpha(bic[i]) && !is_digit(bic[i])) {
            return 0;
        }
    }
    
    return 1;  // Valid
}
