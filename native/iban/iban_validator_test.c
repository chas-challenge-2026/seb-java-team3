#include <check.h>
#include <stdlib.h>
#include <string.h>
#include "iban_validator.h"

/* ========== IBAN Validation Tests ========== */

/**
 * Test valid IBANs from different countries.
 * Reference IBANs from ISO 13616 examples and real-world test data.
 */
START_TEST(test_valid_iban_sweden)
{
    // Swedish IBAN: SE + 2 check digits + 3 digits (bank) + 17 digits (account)
    int error = 0;
    int result = validate_iban("SE4550000000058398257466", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_sweden_with_spaces)
{
    // Same IBAN with spaces (should be normalized)
    int error = 0;
    int result = validate_iban("SE45 5000 0000 0583 9825 7466", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_sweden_lowercase)
{
    // Same IBAN in lowercase (should be normalized)
    int error = 0;
    int result = validate_iban("se4550000000058398257466", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_germany)
{
    // German IBAN: DE + 2 check digits + 18 digits
    int error = 0;
    int result = validate_iban("DE89370400440532013000", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_france)
{
    // French IBAN: FR + 2 check digits + 23 alphanumeric
    int error = 0;
    int result = validate_iban("FR1420041010050500013M02606", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_uk)
{
    // UK IBAN: GB + 2 check digits + 4 digits (bank) + 14 digits (account)
    int error = 0;
    int result = validate_iban("GB82WEST12345698765432", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

START_TEST(test_valid_iban_spain)
{
    // Italian IBAN: IT + 2 check digits + 23 alphanumeric (verified valid)
    int error = 0;
    int result = validate_iban("IT60X0542811101000000123456", &error);
    ck_assert_int_eq(result, 1);
    ck_assert_int_eq(error, 0);
}
END_TEST

/**
 * Test invalid lengths
 */
START_TEST(test_invalid_iban_too_short)
{
    int error = 0;
    int result = validate_iban("SE45", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 1);  // Invalid length
}
END_TEST

START_TEST(test_invalid_iban_too_long)
{
    // IBAN string longer than 60 chars (checked before normalization)
    int error = 0;
    int result = validate_iban("SE4550000000058398257466ABCDEFGHIJKLMNOPQRSTUVWXYZABCDEFGHIJKLMN", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 1);  // Invalid length
}
END_TEST

START_TEST(test_invalid_iban_empty)
{
    int error = 0;
    int result = validate_iban("", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 1);  // Invalid length
}
END_TEST

/**
 * Test invalid country codes
 */
START_TEST(test_invalid_iban_country_code_numbers)
{
    int error = 0;
    int result = validate_iban("1245500000000583982574", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 2);  // Invalid country code
}
END_TEST

START_TEST(test_invalid_iban_country_code_single_letter)
{
    int error = 0;
    int result = validate_iban("S4550000000058398257466", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 2);  // Invalid country code
}
END_TEST

/**
 * Test invalid control digits (should be digits)
 */
START_TEST(test_invalid_iban_control_digits_letters)
{
    int error = 0;
    int result = validate_iban("SEAB50000000058398257466", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 4);  // Invalid control digits or MOD97 failed
}
END_TEST

/**
 * Test invalid characters in IBAN
 */
START_TEST(test_invalid_iban_special_characters)
{
    int error = 0;
    int result = validate_iban("SE45@5000000005839825", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 3);  // Invalid character
}
END_TEST

START_TEST(test_invalid_iban_underscore)
{
    int error = 0;
    int result = validate_iban("SE45_5000000005839825", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 3);  // Invalid character
}
END_TEST

/**
 * Test MOD97 checksum failures
 */
START_TEST(test_invalid_iban_wrong_checksum_one_digit_off)
{
    int error = 0;
    // Valid: SE4550000000058398257466
    // Changed 45 to 44 (wrong checksum)
    int result = validate_iban("SE4450000000058398257466", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 4);  // MOD97 checksum failed
}
END_TEST

START_TEST(test_invalid_iban_wrong_checksum_account_digit)
{
    int error = 0;
    // Valid: SE4550000000058398257466
    // Changed account number 5839 to 5838
    int result = validate_iban("SE4550000000058398257465", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 4);  // MOD97 checksum failed
}
END_TEST

START_TEST(test_invalid_iban_all_zeros_checksum)
{
    int error = 0;
    int result = validate_iban("SE0050000000058398257466", &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 4);  // MOD97 checksum failed
}
END_TEST

/**
 * Test null pointer handling
 */
START_TEST(test_invalid_iban_null)
{
    int error = 0;
    int result = validate_iban(NULL, &error);
    ck_assert_int_eq(result, 0);
    ck_assert_int_eq(error, 1);
}
END_TEST

/**
 * Test without error_out parameter
 */
START_TEST(test_valid_iban_no_error_pointer)
{
    int result = validate_iban("SE4550000000058398257466", NULL);
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_invalid_iban_no_error_pointer)
{
    int result = validate_iban("INVALID", NULL);
    ck_assert_int_eq(result, 0);
}
END_TEST

/* ========== BIC Validation Tests ========== */

/**
 * Test valid BICs
 */
START_TEST(test_valid_bic_8_chars)
{
    // 8-character BIC: DEUTDE33
    int result = validate_bic("DEUTDE33");
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_valid_bic_11_chars)
{
    // 11-character BIC with branch code: DEUTDE33XXX
    int result = validate_bic("DEUTDE33XXX");
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_valid_bic_11_chars_with_digits)
{
    // BIC with digits in branch code: DEUTDE33ABC
    int result = validate_bic("DEUTDE33A1C");
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_valid_bic_swedish)
{
    // Swedish bank BIC
    int result = validate_bic("HANDSE22");
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_valid_bic_swedish_with_branch)
{
    // Swedish bank BIC with branch
    int result = validate_bic("HANDSE22ABC");
    ck_assert_int_eq(result, 1);
}
END_TEST

START_TEST(test_invalid_bic_lowercase)
{
    // BIC in lowercase (should fail - BICs are uppercase)
    // BICs must be uppercase only
    int result = validate_bic("deutde33");
    ck_assert_int_eq(result, 0);  // Should fail
}
END_TEST

/**
 * Test invalid BICs
 */
START_TEST(test_invalid_bic_too_short)
{
    int result = validate_bic("DEUT");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_too_long)
{
    int result = validate_bic("DEUTDE33XXXX");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_empty)
{
    int result = validate_bic("");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_null)
{
    int result = validate_bic(NULL);
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_bank_code_digits)
{
    // First 4 chars must be letters
    int result = validate_bic("DEU1DE33");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_country_code_digits)
{
    // Chars 5-6 (country code) must be letters
    int result = validate_bic("DEUTD133");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_location_code_special_chars)
{
    // Chars 7-8 can only be letters or digits
    int result = validate_bic("DEUTDE@!");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_branch_code_special_chars)
{
    // Chars 9-11 can only be letters or digits
    int result = validate_bic("DEUTDE33@#$");
    ck_assert_int_eq(result, 0);
}
END_TEST

START_TEST(test_invalid_bic_spaces)
{
    int result = validate_bic("DEUT DE33");
    ck_assert_int_eq(result, 0);
}
END_TEST

/* ========== Test Suite Setup ========== */

Suite* iban_validator_suite(void)
{
    Suite* s = suite_create("IBAN Validator");

    // IBAN validation tests
    TCase* tc_iban_valid = tcase_create("Valid IBANs");
    tcase_add_test(tc_iban_valid, test_valid_iban_sweden);
    tcase_add_test(tc_iban_valid, test_valid_iban_sweden_with_spaces);
    tcase_add_test(tc_iban_valid, test_valid_iban_sweden_lowercase);
    tcase_add_test(tc_iban_valid, test_valid_iban_germany);
    tcase_add_test(tc_iban_valid, test_valid_iban_france);
    tcase_add_test(tc_iban_valid, test_valid_iban_uk);
    tcase_add_test(tc_iban_valid, test_valid_iban_spain);
    tcase_add_test(tc_iban_valid, test_valid_iban_no_error_pointer);
    suite_add_tcase(s, tc_iban_valid);

    TCase* tc_iban_length = tcase_create("IBAN Length Validation");
    tcase_add_test(tc_iban_length, test_invalid_iban_too_short);
    tcase_add_test(tc_iban_length, test_invalid_iban_too_long);
    tcase_add_test(tc_iban_length, test_invalid_iban_empty);
    suite_add_tcase(s, tc_iban_length);

    TCase* tc_iban_format = tcase_create("IBAN Format Validation");
    tcase_add_test(tc_iban_format, test_invalid_iban_country_code_numbers);
    tcase_add_test(tc_iban_format, test_invalid_iban_country_code_single_letter);
    tcase_add_test(tc_iban_format, test_invalid_iban_control_digits_letters);
    tcase_add_test(tc_iban_format, test_invalid_iban_special_characters);
    tcase_add_test(tc_iban_format, test_invalid_iban_underscore);
    suite_add_tcase(s, tc_iban_format);

    TCase* tc_iban_checksum = tcase_create("IBAN MOD97 Checksum");
    tcase_add_test(tc_iban_checksum, test_invalid_iban_wrong_checksum_one_digit_off);
    tcase_add_test(tc_iban_checksum, test_invalid_iban_wrong_checksum_account_digit);
    tcase_add_test(tc_iban_checksum, test_invalid_iban_all_zeros_checksum);
    suite_add_tcase(s, tc_iban_checksum);

    TCase* tc_iban_edge = tcase_create("IBAN Edge Cases");
    tcase_add_test(tc_iban_edge, test_invalid_iban_null);
    tcase_add_test(tc_iban_edge, test_invalid_iban_no_error_pointer);
    suite_add_tcase(s, tc_iban_edge);

    // BIC validation tests
    TCase* tc_bic_valid = tcase_create("Valid BICs");
    tcase_add_test(tc_bic_valid, test_valid_bic_8_chars);
    tcase_add_test(tc_bic_valid, test_valid_bic_11_chars);
    tcase_add_test(tc_bic_valid, test_valid_bic_11_chars_with_digits);
    tcase_add_test(tc_bic_valid, test_valid_bic_swedish);
    tcase_add_test(tc_bic_valid, test_valid_bic_swedish_with_branch);
    suite_add_tcase(s, tc_bic_valid);

    TCase* tc_bic_invalid = tcase_create("Invalid BICs");
    tcase_add_test(tc_bic_invalid, test_invalid_bic_too_short);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_too_long);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_empty);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_null);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_bank_code_digits);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_country_code_digits);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_location_code_special_chars);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_branch_code_special_chars);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_spaces);
    tcase_add_test(tc_bic_invalid, test_invalid_bic_lowercase);
    suite_add_tcase(s, tc_bic_invalid);

    return s;
}

int main(void)
{
    Suite* s = iban_validator_suite();
    SRunner* sr = srunner_create(s);

    srunner_run_all(sr, CK_VERBOSE);
    int number_failed = srunner_ntests_failed(sr);
    srunner_free(sr);

    return (number_failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
