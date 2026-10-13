#define _POSIX_C_SOURCE 200809L
#define _DEFAULT_SOURCE  /* flock() */

#include "audit_signer.h"

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <openssl/crypto.h>
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>
#include <stdio.h>
#include <string.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

/* Escaped action/description can be twice as long as the input. */
#define MAX_LINE_LEN 8192
#define TAIL_READ_LEN 8192
#define TIMESTAMP_LEN 20  /* YYYY-MM-DDTHH:MM:SSZ */

static void to_hex(const unsigned char* data, size_t len, char* out)
{
    static const char digits[] = "0123456789abcdef";
    for (size_t i = 0; i < len; i++) {
        out[2 * i] = digits[data[i] >> 4];
        out[2 * i + 1] = digits[data[i] & 0x0F];
    }
    out[2 * len] = '\0';
}

static void sha256_hex(const char* data, size_t len, char out[AUDIT_HASH_HEX_LEN + 1])
{
    unsigned char md[SHA256_DIGEST_LENGTH];
    SHA256((const unsigned char*)data, len, md);
    to_hex(md, sizeof md, out);
}

static int hmac_hex(const char* key, const char* data, size_t len, char out[AUDIT_HASH_HEX_LEN + 1])
{
    size_t key_len = strlen(key);
    if (key_len > INT_MAX) return -1;

    unsigned char md[EVP_MAX_MD_SIZE];
    unsigned int md_len = 0;
    if (!HMAC(EVP_sha256(), key, (int)key_len, (const unsigned char*)data, len, md, &md_len)
        || md_len != SHA256_DIGEST_LENGTH) {
        return -1;
    }
    to_hex(md, md_len, out);
    return 0;
}

static int is_lower_hex(const char* s, size_t len)
{
    for (size_t i = 0; i < len; i++) {
        if (!((s[i] >= '0' && s[i] <= '9') || (s[i] >= 'a' && s[i] <= 'f'))) return 0;
    }
    return 1;
}

/*
 * Copies in to out, escaping '|' and '\'. Fails on CR/LF, if in is longer than
 * max_len, or if out is too small.
 */
static int escape_field(const char* in, size_t max_len, char* out, size_t out_cap)
{
    size_t len = strlen(in);
    if (len > max_len) return -1;

    size_t o = 0;
    for (size_t i = 0; i < len; i++) {
        char c = in[i];
        if (c == '\n' || c == '\r') return -1;
        if (c == '|' || c == '\\') {
            if (o + 2 >= out_cap) return -1;
            out[o++] = '\\';
        } else if (o + 1 >= out_cap) {
            return -1;
        }
        out[o++] = c;
    }
    out[o] = '\0';
    return 0;
}

static int write_all(int fd, const char* data, size_t len)
{
    while (len > 0) {
        ssize_t n = write(fd, data, len);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        data += n;
        len -= (size_t)n;
    }
    return 0;
}

static int lock_file(int fd, int operation)
{
    while (flock(fd, operation) != 0) {
        if (errno != EINTR) return -1;
    }
    return 0;
}

/*
 * Computes the hash that the next entry must carry as PREV_HASH: the SHA-256 of
 * the last line of the file (64 zeros for an empty file). Fails if the file
 * does not end in a complete line.
 */
static int last_line_hash(int fd, char out[AUDIT_HASH_HEX_LEN + 1])
{
    off_t size = lseek(fd, 0, SEEK_END);
    if (size < 0) return -1;
    if (size == 0) {
        memset(out, '0', AUDIT_HASH_HEX_LEN);
        out[AUDIT_HASH_HEX_LEN] = '\0';
        return 0;
    }

    size_t to_read = (size > TAIL_READ_LEN) ? TAIL_READ_LEN : (size_t)size;
    char buf[TAIL_READ_LEN];
    ssize_t got = pread(fd, buf, to_read, size - (off_t)to_read);
    if (got < 0 || (size_t)got != to_read) return -1;
    if (buf[to_read - 1] != '\n') return -1;

    size_t end = to_read - 1;
    size_t start = end;
    while (start > 0 && buf[start - 1] != '\n') start--;
    if (start == 0 && (size_t)size > to_read) return -1;  /* line longer than we ever write */

    sha256_hex(buf + start, end - start, out);
    return 0;
}

int audit_append(
    const char* log_path,
    const char* secret_key,
    int user_id,
    const char* action,
    int entity_id,
    const char* description)
{
    if (!log_path || !secret_key || !action || !description) return AUDIT_ERROR;
    if (secret_key[0] == '\0' || action[0] == '\0') return AUDIT_ERROR;

    char escaped_action[2 * AUDIT_MAX_ACTION_LEN + 1];
    char escaped_description[2 * AUDIT_MAX_DESCRIPTION_LEN + 1];
    if (escape_field(action, AUDIT_MAX_ACTION_LEN, escaped_action, sizeof escaped_action) != 0
        || escape_field(description, AUDIT_MAX_DESCRIPTION_LEN, escaped_description,
                        sizeof escaped_description) != 0) {
        return AUDIT_ERROR;
    }

    char timestamp[TIMESTAMP_LEN + 1];
    time_t now = time(NULL);
    struct tm utc;
    if (now == (time_t)-1 || !gmtime_r(&now, &utc)
        || strftime(timestamp, sizeof timestamp, "%Y-%m-%dT%H:%M:%SZ", &utc) != TIMESTAMP_LEN) {
        return AUDIT_ERROR;
    }

    int fd = open(log_path, O_RDWR | O_APPEND | O_CREAT | O_SYNC | O_CLOEXEC, 0600);
    if (fd < 0) return AUDIT_ERROR;

    int result = AUDIT_ERROR;
    char prev_hash[AUDIT_HASH_HEX_LEN + 1];
    char mac[AUDIT_HASH_HEX_LEN + 1];
    char line[MAX_LINE_LEN];

    /* Hold the lock from reading the last hash until the entry is written */
    if (lock_file(fd, LOCK_EX) != 0) goto done;
    if (last_line_hash(fd, prev_hash) != 0) goto done;

    int len = snprintf(line, sizeof line, "%s|%d|%s|%d|%s|%s",
                       timestamp, user_id, escaped_action, entity_id, escaped_description, prev_hash);
    if (len < 0 || (size_t)len >= sizeof line) goto done;
    if (hmac_hex(secret_key, line, (size_t)len, mac) != 0) goto done;

    int tail = snprintf(line + len, sizeof line - (size_t)len, "|%s\n", mac);
    if (tail < 0 || (size_t)tail >= sizeof line - (size_t)len) goto done;

    if (write_all(fd, line, (size_t)len + (size_t)tail) == 0) result = AUDIT_OK;

done:
    close(fd);  /* also releases the lock */
    return result;
}

/* Returns 1 if the line (without '\n') has a correct PREV_HASH and HMAC. */
static int line_is_valid(const char* secret_key, const char* line, size_t len,
                         const char* expected_prev_hash)
{
    /* Last two '|' separate PREV_HASH and HMAC; escaped pipes only occur earlier */
    const char* hmac_sep = NULL;
    const char* prev_sep = NULL;
    for (size_t i = len; i > 0; i--) {
        if (line[i - 1] != '|') continue;
        if (!hmac_sep) {
            hmac_sep = line + i - 1;
        } else {
            prev_sep = line + i - 1;
            break;
        }
    }
    if (!hmac_sep || !prev_sep) return 0;

    const char* hmac_field = hmac_sep + 1;
    const char* prev_field = prev_sep + 1;
    if ((size_t)((line + len) - hmac_field) != AUDIT_HASH_HEX_LEN) return 0;
    if ((size_t)(hmac_sep - prev_field) != AUDIT_HASH_HEX_LEN) return 0;
    if (!is_lower_hex(hmac_field, AUDIT_HASH_HEX_LEN) || !is_lower_hex(prev_field, AUDIT_HASH_HEX_LEN)) return 0;

    if (CRYPTO_memcmp(prev_field, expected_prev_hash, AUDIT_HASH_HEX_LEN) != 0) return 0;

    char expected_mac[AUDIT_HASH_HEX_LEN + 1];
    if (hmac_hex(secret_key, line, (size_t)(hmac_sep - line), expected_mac) != 0) return 0;
    return CRYPTO_memcmp(hmac_field, expected_mac, AUDIT_HASH_HEX_LEN) == 0;
}

int audit_verify(const char* log_path, const char* secret_key)
{
    if (!log_path || !secret_key) return AUDIT_VERIFY_IO_ERROR;

    int fd = open(log_path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return (errno == ENOENT) ? AUDIT_VERIFY_OK : AUDIT_VERIFY_IO_ERROR;

    struct stat st;
    if (fstat(fd, &st) != 0 || !S_ISREG(st.st_mode) || lock_file(fd, LOCK_SH) != 0) {
        close(fd);
        return AUDIT_VERIFY_IO_ERROR;
    }

    FILE* f = fdopen(fd, "rb");
    if (!f) {
        close(fd);
        return AUDIT_VERIFY_IO_ERROR;
    }

    char prev_hash[AUDIT_HASH_HEX_LEN + 1];
    memset(prev_hash, '0', AUDIT_HASH_HEX_LEN);
    prev_hash[AUDIT_HASH_HEX_LEN] = '\0';

    char line[MAX_LINE_LEN];
    int line_no = 1;
    int result = AUDIT_VERIFY_OK;

    for (;;) {
        size_t len = 0;
        int c;
        int too_long = 0;
        int has_nul = 0;
        while ((c = fgetc(f)) != EOF && c != '\n') {
            if (len < sizeof line) line[len] = (char)c;
            else too_long = 1;
            if (c == '\0') has_nul = 1;
            len++;
        }

        if (c == EOF) {
            if (ferror(f)) result = AUDIT_VERIFY_IO_ERROR;
            else if (len > 0) result = line_no;  /* last line is not terminated */
            break;
        }

        if (too_long || has_nul || !line_is_valid(secret_key, line, len, prev_hash)) {
            result = line_no;
            break;
        }
        sha256_hex(line, len, prev_hash);
        line_no++;
    }

    fclose(f);  /* also releases the lock */
    return result;
}
