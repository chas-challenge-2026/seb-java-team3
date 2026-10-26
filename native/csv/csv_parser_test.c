#include <check.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "csv_parser.h"

#define HEADER "from_account_id,to_iban,amount,reference\n"

static CsvRow* parse_str(const char* s, int* rows_out)
{
    return parse_csv(s, (int)strlen(s), rows_out);
}

/* ========== Valid input ========== */

START_TEST(test_example_batch_file)
{
    // Same content as shared/example-batch.csv
    const char* csv = HEADER
        "1,SE8550000000054910000003,5000.00,Faktura #2001\n"
        "1,SE8550000000054910000005,12500.00,Faktura #2002\n"
        "1,SE8550000000054910000006,8750.50,Faktura #2003\n";
    int n = -1;
    CsvRow* rows = parse_str(csv, &n);

    ck_assert_ptr_nonnull(rows);
    ck_assert_int_eq(n, 3);
    for (int i = 0; i < n; i++) {
        ck_assert_int_eq(rows[i].valid, 1);
        ck_assert_str_eq(rows[i].error, "");
        ck_assert_int_eq(rows[i].from_account_id, 1);
    }
    ck_assert_str_eq(rows[0].to_iban, "SE8550000000054910000003");
    ck_assert_double_eq_tol(rows[0].amount, 5000.00, 0.001);
    ck_assert_str_eq(rows[0].reference, "Faktura #2001");
    ck_assert_double_eq_tol(rows[2].amount, 8750.50, 0.001);
    ck_assert_str_eq(rows[2].reference, "Faktura #2003");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_crlf_line_endings)
{
    const char* csv =
        "from_account_id,to_iban,amount,reference\r\n"
        "1,SE8550000000054910000003,100.00,A\r\n"
        "2,SE8550000000054910000005,200.00,B\r\n";
    int n = -1;
    CsvRow* rows = parse_str(csv, &n);

    ck_assert_int_eq(n, 2);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "A");
    ck_assert_int_eq(rows[1].from_account_id, 2);
    ck_assert_str_eq(rows[1].reference, "B");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_no_trailing_newline)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,100.00,Sista", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "Sista");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_blank_lines_ignored)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE8550000000054910000003,100.00,A\n\n\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_utf8_bom_before_header)
{
    // Excel adds a BOM when saving as "CSV UTF-8"
    int n = -1;
    CsvRow* rows = parse_str(
        "\xEF\xBB\xBF" HEADER "1,SE8550000000054910000003,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_utf8_reference)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE8550000000054910000003,100.00,Hyra Malmö åäö\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "Hyra Malmö åäö");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_reference_max_length_100)
{
    char csv[512];
    char ref[101];
    memset(ref, 'x', 100);
    ref[100] = '\0';
    snprintf(csv, sizeof csv, HEADER "1,SE8550000000054910000003,100.00,%s\n", ref);

    int n = -1;
    CsvRow* rows = parse_str(csv, &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, ref);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_content_len_is_respected)
{
    // Content is not NUL-terminated at content_len: the second row must be ignored
    const char* csv = HEADER
        "1,SE8550000000054910000003,100.00,A\n"
        "2,SE8550000000054910000005,200.00,B\n";
    int first_row_end = (int)(strlen(HEADER) + strlen("1,SE8550000000054910000003,100.00,A\n"));
    int n = -1;
    CsvRow* rows = parse_csv(csv, first_row_end, &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].from_account_id, 1);

    free_csv_rows(rows);
}
END_TEST

/* ========== Empty / degenerate input ========== */

START_TEST(test_header_only)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER, &n);

    ck_assert_int_eq(n, 0);
    free_csv_rows(rows);
}
END_TEST

START_TEST(test_empty_content)
{
    int n = -1;
    CsvRow* rows = parse_csv("", 0, &n);

    ck_assert_int_eq(n, 0);
    free_csv_rows(rows);
}
END_TEST

START_TEST(test_null_content)
{
    int n = -1;
    CsvRow* rows = parse_csv(NULL, 0, &n);

    ck_assert_ptr_null(rows);
    ck_assert_int_eq(n, 0);
}
END_TEST

START_TEST(test_negative_length)
{
    int n = -1;
    CsvRow* rows = parse_csv(HEADER, -5, &n);

    ck_assert_ptr_null(rows);
    ck_assert_int_eq(n, 0);
}
END_TEST

START_TEST(test_null_rows_out)
{
    // Must not crash
    CsvRow* rows = parse_csv(HEADER "1,SE8550000000054910000003,100.00,A\n",
                             (int)strlen(HEADER "1,SE8550000000054910000003,100.00,A\n"),
                             NULL);
    ck_assert_ptr_null(rows);
}
END_TEST

START_TEST(test_free_null_is_safe)
{
    free_csv_rows(NULL);
}
END_TEST

/* ========== RFC 4180 quoting ========== */

START_TEST(test_quoted_field_with_comma)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE8550000000054910000003,100.00,\"Faktura, #2001\"\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "Faktura, #2001");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_quoted_field_with_escaped_quote)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE8550000000054910000003,100.00,\"Han sa \"\"hej\"\"\"\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "Han sa \"hej\"");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_quoted_field_with_newline)
{
    // The embedded newline must not split the record, and the next row must still parse
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER
        "1,SE8550000000054910000003,100.00,\"rad ett\nrad två\"\n"
        "2,SE8550000000054910000005,200.00,B\n", &n);

    ck_assert_int_eq(n, 2);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "rad ett\nrad två");
    ck_assert_int_eq(rows[1].from_account_id, 2);
    ck_assert_str_eq(rows[1].reference, "B");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_quoted_numeric_fields)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "\"1\",\"SE8550000000054910000003\",\"100.50\",\"A\"\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_int_eq(rows[0].from_account_id, 1);
    ck_assert_double_eq_tol(rows[0].amount, 100.50, 0.001);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_empty_reference_is_valid)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,100.00,\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].reference, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_unterminated_quote)
{
    // Must neither crash nor read past content_len
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE8550000000054910000003,100.00,\"aldrig stängd\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

/* ========== Invalid rows (valid = 0, error set, other rows unaffected) ========== */

START_TEST(test_invalid_amount_not_a_number)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,abc,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_amount_trailing_garbage)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,100.00kr,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_amount_zero)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,0.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_amount_negative)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,-50.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_amount_empty)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_amount_nan_and_inf)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER
        "1,SE8550000000054910000003,nan,A\n"
        "1,SE8550000000054910000003,inf,B\n"
        "1,SE8550000000054910000003,1e999,C\n", &n);

    ck_assert_int_eq(n, 3);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_int_eq(rows[1].valid, 0);
    ck_assert_int_eq(rows[2].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_account_id_not_a_number)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "abc,SE8550000000054910000003,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_account_id_overflow)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "99999999999999999999,SE8550000000054910000003,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_missing_iban)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_iban_too_long_for_buffer)
{
    // to_iban holds 34 chars + NUL, so 35+ must be rejected (not truncated)
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER "1,SE85500000000549100000030000000000000,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_reference_too_long)
{
    char csv[512];
    char ref[102];
    memset(ref, 'x', 101);
    ref[101] = '\0';
    snprintf(csv, sizeof csv, HEADER "1,SE8550000000054910000003,100.00,%s\n", ref);

    int n = -1;
    CsvRow* rows = parse_str(csv, &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_too_few_columns)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,100.00\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);
    ck_assert_str_ne(rows[0].error, "");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_invalid_too_many_columns)
{
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE8550000000054910000003,100.00,A,extra\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 0);

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_bad_row_does_not_stop_the_rest)
{
    int n = -1;
    CsvRow* rows = parse_str(
        HEADER
        "1,SE8550000000054910000003,100.00,A\n"
        "1,SE8550000000054910000005,abc,B\n"
        "1,SE8550000000054910000006,300.00,C\n", &n);

    ck_assert_int_eq(n, 3);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_int_eq(rows[1].valid, 0);
    ck_assert_int_eq(rows[2].valid, 1);
    ck_assert_str_eq(rows[2].reference, "C");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_iban_checksum_is_not_checked_here)
{
    // MOD97 is the IBAN validator's job (IbanLib); the parser only parses
    int n = -1;
    CsvRow* rows = parse_str(HEADER "1,SE0000000000000000000000,100.00,A\n", &n);

    ck_assert_int_eq(n, 1);
    ck_assert_int_eq(rows[0].valid, 1);
    ck_assert_str_eq(rows[0].to_iban, "SE0000000000000000000000");

    free_csv_rows(rows);
}
END_TEST

/* ========== Large files / parallel parsing ========== */

START_TEST(test_large_file_keeps_row_order)
{
    // Row i has from_account_id == i + 1 and every 7th row is invalid.
    // Wrong order or a race in the parallel code shows up as a mismatch.
    const int total = 10000;
    size_t cap = (size_t)total * 80 + 128;
    char* csv = malloc(cap);
    ck_assert_ptr_nonnull(csv);

    size_t len = (size_t)snprintf(csv, cap, "%s", HEADER);
    for (int i = 0; i < total; i++) {
        const char* amount = (i % 7 == 0) ? "oops" : "10.00";
        len += (size_t)snprintf(csv + len, cap - len,
                                "%d,SE8550000000054910000003,%s,Ref %d\n",
                                i + 1, amount, i + 1);
    }

    int n = -1;
    CsvRow* rows = parse_csv(csv, (int)len, &n);
    free(csv);

    ck_assert_ptr_nonnull(rows);
    ck_assert_int_eq(n, total);
    for (int i = 0; i < total; i++) {
        ck_assert_int_eq(rows[i].from_account_id, i + 1);
        ck_assert_int_eq(rows[i].valid, (i % 7 == 0) ? 0 : 1);
    }
    ck_assert_str_eq(rows[total - 1].reference, "Ref 10000");

    free_csv_rows(rows);
}
END_TEST

START_TEST(test_large_file_with_quoted_newlines)
{
    // Quoted newlines must not be treated as record boundaries by the parallel split
    const int total = 2000;
    size_t cap = (size_t)total * 80 + 128;
    char* csv = malloc(cap);
    ck_assert_ptr_nonnull(csv);

    size_t len = (size_t)snprintf(csv, cap, "%s", HEADER);
    for (int i = 0; i < total; i++) {
        len += (size_t)snprintf(csv + len, cap - len,
                                "%d,SE8550000000054910000003,10.00,\"a\nb,%d\"\n",
                                i + 1, i + 1);
    }

    int n = -1;
    CsvRow* rows = parse_csv(csv, (int)len, &n);
    free(csv);

    ck_assert_int_eq(n, total);
    for (int i = 0; i < total; i++) {
        char expected[32];
        snprintf(expected, sizeof expected, "a\nb,%d", i + 1);
        ck_assert_int_eq(rows[i].valid, 1);
        ck_assert_int_eq(rows[i].from_account_id, i + 1);
        ck_assert_str_eq(rows[i].reference, expected);
    }

    free_csv_rows(rows);
}
END_TEST

/* ========== Suite ========== */

Suite* csv_parser_suite(void)
{
    Suite* s = suite_create("CSV Parser");

    TCase* tc_valid = tcase_create("Valid Input");
    tcase_add_test(tc_valid, test_example_batch_file);
    tcase_add_test(tc_valid, test_crlf_line_endings);
    tcase_add_test(tc_valid, test_no_trailing_newline);
    tcase_add_test(tc_valid, test_blank_lines_ignored);
    tcase_add_test(tc_valid, test_utf8_bom_before_header);
    tcase_add_test(tc_valid, test_utf8_reference);
    tcase_add_test(tc_valid, test_reference_max_length_100);
    tcase_add_test(tc_valid, test_content_len_is_respected);
    suite_add_tcase(s, tc_valid);

    TCase* tc_degenerate = tcase_create("Empty And Degenerate Input");
    tcase_add_test(tc_degenerate, test_header_only);
    tcase_add_test(tc_degenerate, test_empty_content);
    tcase_add_test(tc_degenerate, test_null_content);
    tcase_add_test(tc_degenerate, test_negative_length);
    tcase_add_test(tc_degenerate, test_null_rows_out);
    tcase_add_test(tc_degenerate, test_free_null_is_safe);
    suite_add_tcase(s, tc_degenerate);

    TCase* tc_quoting = tcase_create("RFC 4180 Quoting");
    tcase_add_test(tc_quoting, test_quoted_field_with_comma);
    tcase_add_test(tc_quoting, test_quoted_field_with_escaped_quote);
    tcase_add_test(tc_quoting, test_quoted_field_with_newline);
    tcase_add_test(tc_quoting, test_quoted_numeric_fields);
    tcase_add_test(tc_quoting, test_empty_reference_is_valid);
    tcase_add_test(tc_quoting, test_unterminated_quote);
    suite_add_tcase(s, tc_quoting);

    TCase* tc_invalid = tcase_create("Invalid Rows");
    tcase_add_test(tc_invalid, test_invalid_amount_not_a_number);
    tcase_add_test(tc_invalid, test_invalid_amount_trailing_garbage);
    tcase_add_test(tc_invalid, test_invalid_amount_zero);
    tcase_add_test(tc_invalid, test_invalid_amount_negative);
    tcase_add_test(tc_invalid, test_invalid_amount_empty);
    tcase_add_test(tc_invalid, test_invalid_amount_nan_and_inf);
    tcase_add_test(tc_invalid, test_invalid_account_id_not_a_number);
    tcase_add_test(tc_invalid, test_invalid_account_id_overflow);
    tcase_add_test(tc_invalid, test_invalid_missing_iban);
    tcase_add_test(tc_invalid, test_invalid_iban_too_long_for_buffer);
    tcase_add_test(tc_invalid, test_invalid_reference_too_long);
    tcase_add_test(tc_invalid, test_invalid_too_few_columns);
    tcase_add_test(tc_invalid, test_invalid_too_many_columns);
    tcase_add_test(tc_invalid, test_bad_row_does_not_stop_the_rest);
    tcase_add_test(tc_invalid, test_iban_checksum_is_not_checked_here);
    suite_add_tcase(s, tc_invalid);

    TCase* tc_large = tcase_create("Large Files");
    tcase_set_timeout(tc_large, 30);
    tcase_add_test(tc_large, test_large_file_keeps_row_order);
    tcase_add_test(tc_large, test_large_file_with_quoted_newlines);
    suite_add_tcase(s, tc_large);

    return s;
}

int main(void)
{
    Suite* s = csv_parser_suite();
    SRunner* sr = srunner_create(s);

    srunner_run_all(sr, CK_VERBOSE);
    int number_failed = srunner_ntests_failed(sr);
    srunner_free(sr);

    return (number_failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
