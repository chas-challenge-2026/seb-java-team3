#include "csv_parser.h"
#include <errno.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef _OPENMP
#include <omp.h>
#endif

#define MAX_ROWS 100000
#define NUM_COLUMNS 4
#define FIELD_BUF 128
#define MAX_IBAN_LEN 34
#define MAX_REFERENCE_LEN 100
#define PARALLEL_THRESHOLD 1000

static void set_error(CsvRow* row, const char* msg){
    if(row->valid == 0 && row->error[0] != '\0') return; 
    row->valid = 0;
    snprintf(row->error, sizeof row->error, "%s", msg);
}

static int parse_int_field(const char* s, int* out){
    if(*s == '\0') return 0;
    char* end;
    errno = 0;
    long v = strtol(s, &end, 10);
    if(*end != '\0' || errno == ERANGE || v < INT_MIN || v > INT_MAX) return 0;
    *out = (int)v;
    return 1;
}

static int parse_amount_field(const char* s, double* out){
    if(*s == '\0') return 0;
    char* end;
    errno = 0;
    double v = strtod(s, &end);
    if(*end != '\0' || errno == ERANGE || !isfinite(v) || v <= 0.0) return 0;
    *out = v;
    return 1;
}

/* Parses one record (without its line terminator) into row. */
static void parse_record(const char* start, const char* end, CsvRow* row){
    char fields[NUM_COLUMNS][FIELD_BUF];
    int nf = 0;
    size_t len = 0;
    int in_quotes = 0;
    int too_long = 0;

    row->valid = 1;
    row->error[0] = '\0';

    for(const char* p = start; p < end; p++){
        char c = *p;
        if(in_quotes){
            if(c == '"'){
                if(p + 1 < end && p[1] == '"'){
                    c = '"';
                    p++;
                } else {
                    in_quotes = 0;
                    continue;
                }
            }
        } else if(c == '"' && len == 0){
            in_quotes = 1;
            continue;
        } else if(c == ','){
            if(nf < NUM_COLUMNS) fields[nf][len] = '\0';
            nf++;
            len = 0;
            continue;
        }
        if(nf < NUM_COLUMNS){
            if(len < FIELD_BUF - 1) fields[nf][len++] = c;
            else too_long = 1;
        }
    }
    if(nf < NUM_COLUMNS) fields[nf][len] = '\0';
    nf++;

    if(in_quotes){ set_error(row, "Unterminated quote"); return; }
    if(nf != NUM_COLUMNS){ set_error(row, "Expected 4 columns"); return; }
    if(too_long){ set_error(row, "Field too long"); return; }

    double amount;
    /* Fields that parsed fine are kept even if a later field is invalid */
    if(!parse_int_field(fields[0], &row->from_account_id)){ set_error(row, "Invalid from_account_id"); return; }
    size_t iban_len = strlen(fields[1]);
    if(iban_len == 0 || iban_len > MAX_IBAN_LEN){ set_error(row, "Invalid to_iban"); return; }
    snprintf(row->to_iban, sizeof row->to_iban, "%s", fields[1]);
    if(!parse_amount_field(fields[2], &amount)){ set_error(row, "Invalid amount"); return; }
    if(strlen(fields[3]) > MAX_REFERENCE_LEN){ set_error(row, "Reference too long"); return; }

    row->amount = amount;
    snprintf(row->reference, sizeof row->reference, "%s", fields[3]);
}

typedef struct {
    const char** starts;
    const char** ends;
    int count;
    int capacity;
    int overflow;
} RecordList;

static int add_record(RecordList* list, const char* start, const char* end, int* header_seen){
    if(end > start && end[-1] == '\r') end--;
    if(start == end) return 1;
    if(!*header_seen){ *header_seen = 1; return 1; }
    if(list->count >= MAX_ROWS){ list->overflow = 1; return 0; }
    if(list->count == list->capacity){
        int cap = list->capacity ? list->capacity * 2 : 1024;
        const char** s = realloc(list->starts, (size_t)cap * sizeof *s);
        if(!s) return 0;
        list->starts = s;
        const char** e = realloc(list->ends, (size_t)cap * sizeof *e);
        if(!e) return 0;
        list->ends = e;
        list->capacity = cap;
    }
    list->starts[list->count] = start;
    list->ends[list->count] = end;
    list->count++;
    return 1;
}

/* Pass 1: a newline only ends a record when it is outside quotes. */
static int find_records(const char* content, int content_len, RecordList* list){
    const char* p = content;
    const char* limit = content + content_len;
    int header_seen = 0;
    int in_quotes = 0;

    if(content_len >= 3 && memcmp(p, "\xEF\xBB\xBF", 3) == 0) p += 3;
    const char* record_start = p;

    for(; p < limit; p++){
        if(*p == '"') in_quotes = !in_quotes;
        else if(*p == '\n' && !in_quotes){
            if(!add_record(list, record_start, p, &header_seen)) return 0;
            record_start = p + 1;
        }
    }
    if(record_start < limit)
        return add_record(list, record_start, limit, &header_seen);
    return 1;
}

CsvRow* parse_csv(const char* content, int content_len, int* rows_out){
    if(rows_out) *rows_out = 0;
    if(!content || content_len < 0 || !rows_out) return NULL;

    RecordList list = {0};
    CsvRow* rows = NULL;

    if(!find_records(content, content_len, &list)) goto fail;

    /* calloc(1, ...) for zero rows so that NULL always means error */
    rows = calloc(list.count ? (size_t)list.count : 1, sizeof *rows);
    if(!rows) goto fail;

    #pragma omp parallel for schedule(static) if(list.count > PARALLEL_THRESHOLD)
    for(int i = 0; i < list.count; i++)
        parse_record(list.starts[i], list.ends[i], &rows[i]);

    *rows_out = list.count;
    free(list.starts);
    free(list.ends);
    return rows;

fail:
    free(list.starts);
    free(list.ends);
    free(rows);
    *rows_out = 0;
    return NULL;
}

void free_csv_rows(CsvRow* rows){
    free(rows);
}
