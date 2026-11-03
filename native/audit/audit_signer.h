#ifndef AUDIT_SIGNER_H
#define AUDIT_SIGNER_H

/*
 * Append-only, tamper-evident audit log (HMAC-SHA256 chain).
 *
 * Every entry is one line, terminated by '\n':
 *
 *   TIMESTAMP|USER_ID|ACTION|ENTITY_ID|DESCRIPTION|PREV_HASH|HMAC
 *
 *   TIMESTAMP    UTC, set by audit_append, format YYYY-MM-DDTHH:MM:SSZ
 *   PREV_HASH    lowercase hex SHA-256 of the previous line (all of it, including
 *                its HMAC, excluding the '\n'). 64 zeros for the first line.
 *   HMAC         lowercase hex HMAC-SHA256(secret_key) over the line up to, but
 *                not including, the '|' in front of the HMAC field.
 *
 * ACTION and DESCRIPTION are escaped before they are written: '|' becomes "\|"
 * and '\' becomes "\\". CR and LF are not allowed in either (the call fails).
 *
 * Not detected by design: removing entries from the END of the log (there is
 * nothing after them to notice), or an attacker who knows the secret key.
 */

#define AUDIT_MAX_ACTION_LEN       64
#define AUDIT_MAX_DESCRIPTION_LEN  1024
#define AUDIT_HASH_HEX_LEN         64

#define AUDIT_OK                   0
#define AUDIT_ERROR               -1
#define AUDIT_VERIFY_OK           -1   /* whole chain is intact */
#define AUDIT_VERIFY_IO_ERROR      0   /* bad arguments or the file could not be read */

/*
 * Appends one signed entry. Returns AUDIT_OK or AUDIT_ERROR.
 *
 * Fails (and writes nothing) if any pointer is NULL, secret_key or action is
 * empty, action is longer than AUDIT_MAX_ACTION_LEN, description is longer than
 * AUDIT_MAX_DESCRIPTION_LEN (measured before escaping), either contains CR/LF,
 * the file cannot be opened, or its last line is not terminated by '\n'.
 *
 * The file is created with mode 0600. Concurrent callers, also from different
 * processes, are serialised so the chain never forks.
 */
int audit_append(
    const char* log_path,
    const char* secret_key,
    int user_id,
    const char* action,
    int entity_id,
    const char* description
);

/*
 * Verifies the whole log. Returns:
 *   AUDIT_VERIFY_OK        (-1)  every line has the right PREV_HASH and HMAC.
 *                                A missing or empty file counts as an intact, empty log.
 *   n >= 1                      the 1-indexed line number of the first broken entry
 *                                (wrong hash/HMAC, malformed, or not terminated by '\n').
 *   AUDIT_VERIFY_IO_ERROR  (0)   NULL arguments, or the path cannot be read (e.g. a directory).
 */
int audit_verify(const char* log_path, const char* secret_key);

#endif /* AUDIT_SIGNER_H */
