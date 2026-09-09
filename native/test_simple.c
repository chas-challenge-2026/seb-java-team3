/**
 * Simple standalone test program for IBAN validator (without external dependencies)
 * 
 * This can be run immediately without the Check framework.
 * For full test suite, use iban_validator_test.c with Check framework.
 * 
 * Usage: gcc -Wall -Wextra -std=c99 -o test_simple iban_validator.c test_simple.c && ./test_simple
 */

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "iban_validator.h"

// Color codes for output
#define GREEN   "\x1b[32m"
#define RED     "\x1b[31m"
#define YELLOW  "\x1b[33m"
#define RESET   "\x1b[0m"

typedef struct {
    const char* test_name;
    const char* iban;
    int expected_result;
    int expected_error;  // -1 = don't check error code
} IbanTest;

typedef struct {
    const char* test_name;
    const char* bic;
    int expected_result;
} BicTest;

int passed = 0;
int failed = 0;

void test_iban_validator(void)
{
    printf("\n=== IBAN Validator Tests ===\n\n");

    IbanTest tests[] = {
        // Valid IBANs
        {"Valid IBAN: Sweden", "SE4550000000058398257466", 1, -1},
        {"Valid IBAN: Sweden with spaces", "SE45 5000 0000 0583 9825 7466", 1, -1},
        {"Valid IBAN: Sweden lowercase", "se4550000000058398257466", 1, -1},
        {"Valid IBAN: Germany", "DE89370400440532013000", 1, -1},
        {"Valid IBAN: France", "FR1420041010050500013M02606", 1, -1},
        {"Valid IBAN: UK", "GB82WEST12345698765432", 1, -1},
        {"Valid IBAN: Italy", "IT60X0542811101000000123456", 1, -1},

        // Invalid lengths
        {"Invalid: too short", "SE45", 0, 1},
        {"Invalid: too long (>60 chars)", "SE45500000000058398257466123456789012345678901234567890123456789", 0, 1},
        {"Invalid: empty", "", 0, 1},

        // Invalid country codes
        {"Invalid: country code numbers", "1245500000000583982574", 0, 2},
        {"Invalid: country code single letter", "S4550000000058398257466", 0, 2},

        // Invalid control digits
        {"Invalid: control digits letters", "SEAB50000000058398257466", 0, 4},

        // Invalid characters
        {"Invalid: special characters", "SE45@5000000005839825", 0, 3},
        {"Invalid: underscore", "SE45_5000000005839825", 0, 3},

        // MOD97 checksum failures
        {"Invalid: checksum off by 1", "SE4450000000058398257466", 0, 4},
        {"Invalid: account digit changed", "SE4550000000058398257465", 0, 4},
    };

    int num_tests = sizeof(tests) / sizeof(tests[0]);

    for (int i = 0; i < num_tests; i++) {
        int error = 0;
        int result = validate_iban(tests[i].iban, &error);

        int test_passed = (result == tests[i].expected_result) &&
                         (tests[i].expected_error == -1 || error == tests[i].expected_error);

        if (test_passed) {
            printf(GREEN "✓ PASS" RESET " %s\n", tests[i].test_name);
            passed++;
        } else {
            printf(RED "✗ FAIL" RESET " %s\n", tests[i].test_name);
            printf("  Expected: result=%d, error=%d\n", tests[i].expected_result, tests[i].expected_error);
            printf("  Got:      result=%d, error=%d\n", result, error);
            failed++;
        }
    }

    // Test null pointer
    printf("\n-- Edge Cases --\n");
    int result = validate_iban(NULL, NULL);
    if (result == 0) {
        printf(GREEN "✓ PASS" RESET " NULL IBAN handling\n");
        passed++;
    } else {
        printf(RED "✗ FAIL" RESET " NULL IBAN handling\n");
        failed++;
    }
}

void test_bic_validator(void)
{
    printf("\n=== BIC Validator Tests ===\n\n");

    BicTest tests[] = {
        // Valid BICs
        {"Valid BIC: 8 chars", "DEUTDE33", 1},
        {"Valid BIC: 11 chars", "DEUTDE33XXX", 1},
        {"Valid BIC: 11 chars with digits", "DEUTDE33A1C", 1},
        {"Valid BIC: Swedish", "HANDSE22", 1},
        {"Valid BIC: Swedish with branch", "HANDSE22ABC", 1},

        // Invalid BICs
        {"Invalid: too short", "DEUT", 0},
        {"Invalid: too long", "DEUTDE33XXXX", 0},
        {"Invalid: empty", "", 0},
        {"Invalid: bank code with digits", "DEU1DE33", 0},
        {"Invalid: country code with digits", "DEUTD133", 0},
        {"Invalid: location code special chars", "DEUTDE@!", 0},
        {"Invalid: branch code special chars", "DEUTDE33@#$", 0},
        {"Invalid: with spaces", "DEUT DE33", 0},
        {"Invalid: lowercase", "deutde33", 0},
    };

    int num_tests = sizeof(tests) / sizeof(tests[0]);

    for (int i = 0; i < num_tests; i++) {
        int result = validate_bic(tests[i].bic);

        if (result == tests[i].expected_result) {
            printf(GREEN "✓ PASS" RESET " %s\n", tests[i].test_name);
            passed++;
        } else {
            printf(RED "✗ FAIL" RESET " %s\n", tests[i].test_name);
            printf("  Expected: %d, Got: %d\n", tests[i].expected_result, result);
            failed++;
        }
    }

    // Test null pointer
    printf("\n-- Edge Cases --\n");
    int result = validate_bic(NULL);
    if (result == 0) {
        printf(GREEN "✓ PASS" RESET " NULL BIC handling\n");
        passed++;
    } else {
        printf(RED "✗ FAIL" RESET " NULL BIC handling\n");
        failed++;
    }
}

int main(void)
{
    printf(YELLOW "================================\n");
    printf("IBAN/BIC Validator Test Suite\n");
    printf("================================\n" RESET);

    test_iban_validator();
    test_bic_validator();

    // Summary
    printf("\n" YELLOW "================================\n");
    printf("Test Results: %d " GREEN "PASSED" RESET ", %d " RED "FAILED" RESET "\n", passed, failed);
    printf(YELLOW "================================\n" RESET);

    return (failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
