#define _POSIX_C_SOURCE 200809L
#include <check.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include "audit_signer.h"

#define KEY "test-secret-key"
#define ZERO_HASH "0000000000000000000000000000000000000000000000000000000000000000"

static char dir_path[64];
static char log_path[128];

static void setup(void)
{
    snprintf(dir_path, sizeof dir_path, "/tmp/audit_test_XXXXXX");
    ck_assert_ptr_nonnull(mkdtemp(dir_path));
    snprintf(log_path, sizeof log_path, "%s/audit.log", dir_path);
}

static void teardown(void)
{
    unlink(log_path);
    rmdir(dir_path);
}

/* ========== File helpers ========== */

static char* read_file(const char* path, size_t* len_out)
{
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    char* buf = malloc((size_t)n + 1);
    ck_assert_ptr_nonnull(buf);
    size_t got = fread(buf, 1, (size_t)n, f);
    ck_assert_uint_eq(got, (size_t)n);
    buf[n] = '\0';
    fclose(f);
    if (len_out) *len_out = (size_t)n;
    return buf;
}

static void write_file(const char* path, const char* data, size_t len)
{
    FILE* f = fopen(path, "wb");
    ck_assert_ptr_nonnull(f);
    ck_assert_uint_eq(fwrite(data, 1, len, f), len);
    fclose(f);
}

static int count_lines(const char* path)
{
    char* c = read_file(path, NULL);
    if (!c) return 0;
    int n = 0;
    for (const char* p = c; *p; p++)
        if (*p == '\n') n++;
    free(c);
    return n;
}

/* Copies line number `index` (1-based, without '\n') into out. */
static void get_line(const char* path, int index, char* out, size_t cap)
{
    char* c = read_file(path, NULL);
    ck_assert_ptr_nonnull(c);
    const char* p = c;
    for (int i = 1; i < index; i++) {
        p = strchr(p, '\n');
        ck_assert_ptr_nonnull(p);
        p++;
    }
    const char* e = strchr(p, '\n');
    ck_assert_ptr_nonnull(e);
    ck_assert_uint_lt((size_t)(e - p), cap);
    memcpy(out, p, (size_t)(e - p));
    out[e - p] = '\0';
    free(c);
}

#define MAX_LINES 512
typedef struct {
    char* text[MAX_LINES];
    int n;
} Lines;

static void lines_load(Lines* l, const char* path)
{
    l->n = 0;
    char* c = read_file(path, NULL);
    ck_assert_ptr_nonnull(c);
    char* p = c;
    while (*p) {
        char* e = strchr(p, '\n');
        ck_assert_ptr_nonnull(e);
        *e = '\0';
        l->text[l->n++] = strdup(p);
        p = e + 1;
    }
    free(c);
}

static void lines_save(const Lines* l, const char* path)
{
    FILE* f = fopen(path, "wb");
    ck_assert_ptr_nonnull(f);
    for (int i = 0; i < l->n; i++) fprintf(f, "%s\n", l->text[i]);
    fclose(f);
}

static void lines_free(Lines* l)
{
    for (int i = 0; i < l->n; i++) free(l->text[i]);
    l->n = 0;
}

static void lines_delete(Lines* l, int idx)
{
    free(l->text[idx - 1]);
    for (int i = idx; i < l->n; i++) l->text[i - 1] = l->text[i];
    l->n--;
}

static void lines_swap(Lines* l, int a, int b)
{
    char* t = l->text[a - 1];
    l->text[a - 1] = l->text[b - 1];
    l->text[b - 1] = t;
}

static void lines_insert_after(Lines* l, int idx, const char* text)
{
    for (int i = l->n; i > idx; i--) l->text[i] = l->text[i - 1];
    l->text[idx] = strdup(text);
    l->n++;
}

/* Replaces the first occurrence of `from` in the file. */
static void replace_in_file(const char* path, const char* from, const char* to)
{
    size_t len;
    char* c = read_file(path, &len);
    ck_assert_ptr_nonnull(c);
    char* hit = strstr(c, from);
    ck_assert_ptr_nonnull(hit);

    size_t fl = strlen(from), tl = strlen(to);
    char* out = malloc(len - fl + tl + 1);
    ck_assert_ptr_nonnull(out);
    size_t head = (size_t)(hit - c);
    memcpy(out, c, head);
    memcpy(out + head, to, tl);
    memcpy(out + head + tl, hit + fl, len - head - fl + 1);
    write_file(path, out, len - fl + tl);
    free(out);
    free(c);
}

/* ========== Parsing / crypto helpers ========== */

/* Splits on every '|' in place (only for lines without escaped pipes). Returns the field count. */
static int split_fields(char* line, char* fields[], int max)
{
    int n = 0;
    fields[n++] = line;
    for (char* p = line; *p; p++) {
        if (*p == '|') {
            *p = '\0';
            if (n < max) fields[n] = p + 1;
            n++;
        }
    }
    return n;
}

static void to_hex(const unsigned char* d, size_t n, char* out)
{
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < n; i++) {
        out[2 * i] = digits[d[i] >> 4];
        out[2 * i + 1] = digits[d[i] & 0xF];
    }
    out[2 * n] = '\0';
}

static void sha256_hex(const char* data, size_t len, char out[65])
{
    unsigned char md[SHA256_DIGEST_LENGTH];
    SHA256((const unsigned char*)data, len, md);
    to_hex(md, sizeof md, out);
}

static void hmac_hex(const char* key, const char* data, size_t len, char out[65])
{
    unsigned char md[EVP_MAX_MD_SIZE];
    unsigned int md_len = 0;
    HMAC(EVP_sha256(), key, (int)strlen(key), (const unsigned char*)data, len, md, &md_len);
    ck_assert_uint_eq(md_len, 32);
    to_hex(md, md_len, out);
}

static int looks_like_timestamp(const char* t)
{
    // YYYY-MM-DDTHH:MM:SSZ
    const char* pattern = "dddd-dd-ddTdd:dd:ddZ";
    if (strlen(t) != strlen(pattern)) return 0;
    for (size_t i = 0; pattern[i]; i++) {
        if (pattern[i] == 'd') {
            if (t[i] < '0' || t[i] > '9') return 0;
        } else if (t[i] != pattern[i]) {
            return 0;
        }
    }
    return 1;
}

static int is_lower_hex64(const char* s)
{
    if (strlen(s) != 64) return 0;
    for (int i = 0; i < 64; i++)
        if (!((s[i] >= '0' && s[i] <= '9') || (s[i] >= 'a' && s[i] <= 'f'))) return 0;
    return 1;
}

static void append_n(int n)
{
    for (int i = 1; i <= n; i++) {
        char desc[64];
        snprintf(desc, sizeof desc, "Entry number %d", i);
        ck_assert_int_eq(audit_append(log_path, KEY, 10 + i, "CREATE_PAYMENT", 100 + i, desc), AUDIT_OK);
    }
}

/* ========== Entry format ========== */

START_TEST(test_first_entry_format)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 42, "CREATE_PAYMENT", 7, "Payment created"), AUDIT_OK);

    size_t len;
    char* content = read_file(log_path, &len);
    ck_assert_ptr_nonnull(content);
    ck_assert_uint_gt(len, 0);
    ck_assert_int_eq(content[len - 1], '\n');
    ck_assert_int_eq(count_lines(log_path), 1);

    char line[512];
    get_line(log_path, 1, line, sizeof line);
    char* f[8];
    ck_assert_int_eq(split_fields(line, f, 8), 7);

    ck_assert_msg(looks_like_timestamp(f[0]), "bad timestamp: %s", f[0]);
    ck_assert_str_eq(f[1], "42");
    ck_assert_str_eq(f[2], "CREATE_PAYMENT");
    ck_assert_str_eq(f[3], "7");
    ck_assert_str_eq(f[4], "Payment created");
    ck_assert_str_eq(f[5], ZERO_HASH);
    ck_assert_msg(is_lower_hex64(f[6]), "bad HMAC: %s", f[6]);

    free(content);
}
END_TEST

START_TEST(test_hmac_matches_independent_computation)
{
    append_n(1);

    char line[512];
    get_line(log_path, 1, line, sizeof line);
    char* last_bar = strrchr(line, '|');
    ck_assert_ptr_nonnull(last_bar);

    char expected[65];
    hmac_hex(KEY, line, (size_t)(last_bar - line), expected);
    ck_assert_str_eq(last_bar + 1, expected);
}
END_TEST

START_TEST(test_prev_hash_is_sha256_of_previous_line)
{
    append_n(3);

    for (int i = 2; i <= 3; i++) {
        char prev[512], cur[512];
        get_line(log_path, i - 1, prev, sizeof prev);
        get_line(log_path, i, cur, sizeof cur);

        char expected[65];
        sha256_hex(prev, strlen(prev), expected);

        char* f[8];
        ck_assert_int_eq(split_fields(cur, f, 8), 7);
        ck_assert_str_eq(f[5], expected);
    }
}
END_TEST

START_TEST(test_each_append_adds_one_line)
{
    for (int i = 1; i <= 5; i++) {
        append_n(1);
        ck_assert_int_eq(count_lines(log_path), i);
    }
}
END_TEST

START_TEST(test_file_created_with_mode_0600)
{
    append_n(1);

    struct stat st;
    ck_assert_int_eq(stat(log_path, &st), 0);
    ck_assert_int_eq(st.st_mode & 0777, 0600);
}
END_TEST

START_TEST(test_empty_description_allowed)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "LOGIN", 0, ""), AUDIT_OK);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_utf8_description)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "CREATE_PAYMENT", 1, "Hyra Malmö åäö"), AUDIT_OK);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);

    char line[512];
    get_line(log_path, 1, line, sizeof line);
    ck_assert_ptr_nonnull(strstr(line, "Hyra Malmö åäö"));
}
END_TEST

/* ========== Escaping and input validation ========== */

START_TEST(test_pipe_and_backslash_are_escaped)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "NOTE", 1, "a|b\\c"), AUDIT_OK);

    char* content = read_file(log_path, NULL);
    ck_assert_ptr_nonnull(content);
    ck_assert_ptr_nonnull(strstr(content, "a\\|b\\\\c"));  // file contains: a\|b\\c
    free(content);

    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_pipe_in_action_is_escaped)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "A|B", 1, "x"), AUDIT_OK);
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "C", 1, "y"), AUDIT_OK);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_newline_in_description_rejected)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "NOTE", 1, "first\nsecond"), AUDIT_ERROR);
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "NOTE", 1, "first\rsecond"), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_newline_in_action_rejected)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "A\nB", 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_empty_action_rejected)
{
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "", 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_null_arguments_rejected)
{
    ck_assert_int_eq(audit_append(NULL, KEY, 1, "A", 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(audit_append(log_path, NULL, 1, "A", 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(audit_append(log_path, KEY, 1, NULL, 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "A", 1, NULL), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_empty_key_rejected)
{
    ck_assert_int_eq(audit_append(log_path, "", 1, "A", 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_max_lengths_accepted)
{
    char action[AUDIT_MAX_ACTION_LEN + 2];
    char desc[AUDIT_MAX_DESCRIPTION_LEN + 2];
    memset(action, 'A', AUDIT_MAX_ACTION_LEN);
    action[AUDIT_MAX_ACTION_LEN] = '\0';
    memset(desc, 'd', AUDIT_MAX_DESCRIPTION_LEN);
    desc[AUDIT_MAX_DESCRIPTION_LEN] = '\0';

    ck_assert_int_eq(audit_append(log_path, KEY, 1, action, 1, desc), AUDIT_OK);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_too_long_action_rejected)
{
    char action[AUDIT_MAX_ACTION_LEN + 2];
    memset(action, 'A', AUDIT_MAX_ACTION_LEN + 1);
    action[AUDIT_MAX_ACTION_LEN + 1] = '\0';

    ck_assert_int_eq(audit_append(log_path, KEY, 1, action, 1, "x"), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_too_long_description_rejected)
{
    char desc[AUDIT_MAX_DESCRIPTION_LEN + 2];
    memset(desc, 'd', AUDIT_MAX_DESCRIPTION_LEN + 1);
    desc[AUDIT_MAX_DESCRIPTION_LEN + 1] = '\0';

    ck_assert_int_eq(audit_append(log_path, KEY, 1, "A", 1, desc), AUDIT_ERROR);
    ck_assert_int_eq(count_lines(log_path), 0);
}
END_TEST

START_TEST(test_append_to_unwritable_path_fails)
{
    ck_assert_int_eq(audit_append("/nonexistent_dir_xyz/audit.log", KEY, 1, "A", 1, "x"), AUDIT_ERROR);
}
END_TEST

START_TEST(test_append_refuses_unterminated_last_line)
{
    // A partial write leaves the last line without '\n': chaining onto it would be unsafe
    append_n(1);
    size_t len;
    char* content = read_file(log_path, &len);
    write_file(log_path, content, len - 1);

    ck_assert_int_eq(audit_append(log_path, KEY, 1, "A", 1, "x"), AUDIT_ERROR);

    size_t len_after;
    char* after = read_file(log_path, &len_after);
    ck_assert_uint_eq(len_after, len - 1);
    ck_assert_int_eq(memcmp(content, after, len_after), 0);
    free(content);
    free(after);
}
END_TEST

/* ========== Verification: intact logs ========== */

START_TEST(test_verify_missing_file_is_empty_log)
{
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_verify_empty_file)
{
    write_file(log_path, "", 0);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_verify_single_entry)
{
    append_n(1);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_verify_valid_chain)
{
    append_n(50);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

START_TEST(test_verify_arguments_and_io_errors)
{
    append_n(1);
    ck_assert_int_eq(audit_verify(NULL, KEY), AUDIT_VERIFY_IO_ERROR);
    ck_assert_int_eq(audit_verify(log_path, NULL), AUDIT_VERIFY_IO_ERROR);
    ck_assert_int_eq(audit_verify(dir_path, KEY), AUDIT_VERIFY_IO_ERROR);
}
END_TEST

/* ========== Verification: tampering (returns the 1-indexed line of the first break) ========== */

START_TEST(test_detects_modified_description)
{
    append_n(3);
    replace_in_file(log_path, "Entry number 2", "Entry number X");
    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_modified_user_id_in_first_line)
{
    append_n(3);
    replace_in_file(log_path, "|11|", "|99|");
    ck_assert_int_eq(audit_verify(log_path, KEY), 1);
}
END_TEST

START_TEST(test_detects_modified_prev_hash)
{
    append_n(3);
    char line[512];
    get_line(log_path, 3, line, sizeof line);
    char* f[8];
    split_fields(line, f, 8);

    char tampered[65];
    strcpy(tampered, f[5]);
    tampered[0] = (tampered[0] == '0') ? '1' : '0';

    char from[80], to[80];
    snprintf(from, sizeof from, "|%s|", f[5]);
    snprintf(to, sizeof to, "|%s|", tampered);

    // The hash text is unique to line 3 because every line has a different PREV_HASH
    replace_in_file(log_path, from, to);
    ck_assert_int_eq(audit_verify(log_path, KEY), 3);
}
END_TEST

START_TEST(test_detects_modified_hmac_on_last_line)
{
    append_n(3);
    char line[512];
    get_line(log_path, 3, line, sizeof line);
    char* hmac = strrchr(line, '|') + 1;

    char tampered[65];
    strcpy(tampered, hmac);
    tampered[0] = (tampered[0] == '0') ? '1' : '0';

    replace_in_file(log_path, hmac, tampered);
    ck_assert_int_eq(audit_verify(log_path, KEY), 3);
}
END_TEST

START_TEST(test_detects_deleted_middle_line)
{
    append_n(4);
    Lines l;
    lines_load(&l, log_path);
    lines_delete(&l, 2);
    lines_save(&l, log_path);
    lines_free(&l);

    // Former line 3 is now line 2 and points at a hash that is gone
    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_deleted_first_line)
{
    append_n(3);
    Lines l;
    lines_load(&l, log_path);
    lines_delete(&l, 1);
    lines_save(&l, log_path);
    lines_free(&l);

    ck_assert_int_eq(audit_verify(log_path, KEY), 1);
}
END_TEST

START_TEST(test_detects_swapped_lines)
{
    append_n(4);
    Lines l;
    lines_load(&l, log_path);
    lines_swap(&l, 2, 3);
    lines_save(&l, log_path);
    lines_free(&l);

    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_replayed_line)
{
    append_n(3);
    Lines l;
    lines_load(&l, log_path);
    char* copy = strdup(l.text[0]);
    lines_insert_after(&l, 1, copy);
    lines_save(&l, log_path);
    lines_free(&l);
    free(copy);

    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_forged_line_without_key)
{
    append_n(3);
    Lines l;
    lines_load(&l, log_path);
    lines_insert_after(&l, 1,
        "2026-01-01T00:00:00Z|1|FAKE|1|forged|" ZERO_HASH "|" ZERO_HASH);
    lines_save(&l, log_path);
    lines_free(&l);

    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_wrong_key)
{
    append_n(3);
    ck_assert_int_eq(audit_verify(log_path, "another-key"), 1);
}
END_TEST

START_TEST(test_detects_garbage_line)
{
    append_n(3);
    Lines l;
    lines_load(&l, log_path);
    lines_insert_after(&l, 2, "this is not an audit entry");
    lines_save(&l, log_path);
    lines_free(&l);

    ck_assert_int_eq(audit_verify(log_path, KEY), 3);
}
END_TEST

START_TEST(test_detects_blank_line)
{
    append_n(3);
    Lines l;
    lines_load(&l, log_path);
    lines_insert_after(&l, 1, "");
    lines_save(&l, log_path);
    lines_free(&l);

    ck_assert_int_eq(audit_verify(log_path, KEY), 2);
}
END_TEST

START_TEST(test_detects_truncated_last_line)
{
    append_n(3);
    size_t len;
    char* content = read_file(log_path, &len);
    write_file(log_path, content, len - 20);
    free(content);

    ck_assert_int_eq(audit_verify(log_path, KEY), 3);
}
END_TEST

START_TEST(test_chain_continues_after_failed_append)
{
    append_n(2);
    ck_assert_int_eq(audit_append(log_path, KEY, 1, "BAD\n", 1, "x"), AUDIT_ERROR);
    append_n(1);

    ck_assert_int_eq(count_lines(log_path), 3);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

/* ========== Concurrency ========== */

START_TEST(test_concurrent_appends_keep_chain_intact)
{
    const int procs = 4, per_proc = 25;

    for (int i = 0; i < procs; i++) {
        pid_t pid = fork();
        ck_assert_int_ge(pid, 0);
        if (pid == 0) {
            for (int j = 0; j < per_proc; j++)
                if (audit_append(log_path, KEY, i, "CONCURRENT", j, "parallel") != AUDIT_OK) _exit(1);
            _exit(0);
        }
    }
    for (int i = 0; i < procs; i++) {
        int status = 0;
        ck_assert_int_ge(waitpid(-1, &status, 0), 0);
        ck_assert_int_eq(WIFEXITED(status) && WEXITSTATUS(status) == 0, 1);
    }

    ck_assert_int_eq(count_lines(log_path), procs * per_proc);
    ck_assert_int_eq(audit_verify(log_path, KEY), AUDIT_VERIFY_OK);
}
END_TEST

/* ========== Suite ========== */

Suite* audit_signer_suite(void)
{
    Suite* s = suite_create("Audit Signer");
    TCase* tc;

    tc = tcase_create("Entry Format");
    tcase_add_checked_fixture(tc, setup, teardown);
    tcase_set_timeout(tc, 60);
    tcase_add_test(tc, test_first_entry_format);
    tcase_add_test(tc, test_hmac_matches_independent_computation);
    tcase_add_test(tc, test_prev_hash_is_sha256_of_previous_line);
    tcase_add_test(tc, test_each_append_adds_one_line);
    tcase_add_test(tc, test_file_created_with_mode_0600);
    tcase_add_test(tc, test_empty_description_allowed);
    tcase_add_test(tc, test_utf8_description);
    suite_add_tcase(s, tc);

    tc = tcase_create("Escaping And Input Validation");
    tcase_add_checked_fixture(tc, setup, teardown);
    tcase_set_timeout(tc, 60);
    tcase_add_test(tc, test_pipe_and_backslash_are_escaped);
    tcase_add_test(tc, test_pipe_in_action_is_escaped);
    tcase_add_test(tc, test_newline_in_description_rejected);
    tcase_add_test(tc, test_newline_in_action_rejected);
    tcase_add_test(tc, test_empty_action_rejected);
    tcase_add_test(tc, test_null_arguments_rejected);
    tcase_add_test(tc, test_empty_key_rejected);
    tcase_add_test(tc, test_max_lengths_accepted);
    tcase_add_test(tc, test_too_long_action_rejected);
    tcase_add_test(tc, test_too_long_description_rejected);
    tcase_add_test(tc, test_append_to_unwritable_path_fails);
    tcase_add_test(tc, test_append_refuses_unterminated_last_line);
    suite_add_tcase(s, tc);

    tc = tcase_create("Verify Intact Logs");
    tcase_add_checked_fixture(tc, setup, teardown);
    tcase_set_timeout(tc, 60);
    tcase_add_test(tc, test_verify_missing_file_is_empty_log);
    tcase_add_test(tc, test_verify_empty_file);
    tcase_add_test(tc, test_verify_single_entry);
    tcase_add_test(tc, test_verify_valid_chain);
    tcase_add_test(tc, test_verify_arguments_and_io_errors);
    suite_add_tcase(s, tc);

    tc = tcase_create("Tamper Detection");
    tcase_add_checked_fixture(tc, setup, teardown);
    tcase_set_timeout(tc, 60);
    tcase_add_test(tc, test_detects_modified_description);
    tcase_add_test(tc, test_detects_modified_user_id_in_first_line);
    tcase_add_test(tc, test_detects_modified_prev_hash);
    tcase_add_test(tc, test_detects_modified_hmac_on_last_line);
    tcase_add_test(tc, test_detects_deleted_middle_line);
    tcase_add_test(tc, test_detects_deleted_first_line);
    tcase_add_test(tc, test_detects_swapped_lines);
    tcase_add_test(tc, test_detects_replayed_line);
    tcase_add_test(tc, test_detects_forged_line_without_key);
    tcase_add_test(tc, test_detects_wrong_key);
    tcase_add_test(tc, test_detects_garbage_line);
    tcase_add_test(tc, test_detects_blank_line);
    tcase_add_test(tc, test_detects_truncated_last_line);
    tcase_add_test(tc, test_chain_continues_after_failed_append);
    suite_add_tcase(s, tc);

    tc = tcase_create("Concurrency");
    tcase_add_checked_fixture(tc, setup, teardown);
    tcase_set_timeout(tc, 60);
    tcase_add_test(tc, test_concurrent_appends_keep_chain_intact);
    suite_add_tcase(s, tc);

    return s;
}

int main(void)
{
    Suite* s = audit_signer_suite();
    SRunner* sr = srunner_create(s);

    srunner_run_all(sr, CK_VERBOSE);
    int number_failed = srunner_ntests_failed(sr);
    srunner_free(sr);

    return (number_failed == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}
