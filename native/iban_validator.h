#ifndef IBAN_VALIDATOR_H
#define IBAN_VALIDATOR_H

#include <stddef.h>

/**
 * Validates an IBAN according to ISO 13616 (MOD97 checksum).
 * 
 * @param iban The IBAN string to validate (may contain spaces or hyphens, will be normalized)
 * @param error_out Pointer to error code (only set if validation fails):
 *                  1 = invalid length (not 15-34 characters)
 *                  2 = invalid country code (first 2 chars must be A-Z)
 *                  3 = invalid character (only A-Z, 0-9, space, hyphen allowed)
 *                  4 = MOD97 checksum failed (invalid control digits)
 * 
 * @return 1 if IBAN is valid (format + MOD97 checksum OK), 0 otherwise
 */
int validate_iban(const char* iban, int* error_out);

/**
 * Validates a BIC (Business Identifier Code) according to ISO 9362.
 * BIC format: AAAA BB CC [DDD]
 *   - 4 letters: bank code
 *   - 2 letters: country code (ISO 3166-1 alpha-2)
 *   - 2 characters (letters or digits): location code
 *   - 3 characters (letters or digits, optional): branch code
 * 
 * @param bic The BIC string to validate
 * @return 1 if BIC is valid, 0 otherwise
 */
int validate_bic(const char* bic);

#endif // IBAN_VALIDATOR_H
