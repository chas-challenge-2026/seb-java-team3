# Native C/C++ Moduler — v2

Denna katalog innehåller (i v2) tre prestandakritiska och säkerhetskritiska moduler implementerade i C/C++. De anropas från Java via **JNA** (Java Native Access — rekommenderat, ingen boilerplate) eller **JNI** (om finkornig kontroll behövs).

## Varför native?

1. **CSV-parsern** ska hantera batchfiler på 10 000+ rader med parallell körning. En ren Java-implementation är tillräcklig för v1:s 500-radersgräns men skalas inte till produktionsvolymer.

2. **IBAN-validatorn** implementerar ISO 13616 MOD97-algoritmen. En C-implementation är trivial att formellt verifiera och enkel att porta till mobilklienter (iOS/Android via FFI).

3. **Audit-signeringen** kräver kryptografisk integritet. En HMAC-SHA256-kedja i C är lättare att granska och auditeras utan JVM-runtime-beroenden.

> **JNA vs JNI:** JNA laddar `.so`-filen direkt och mappar funktioner mot ett Java-interface i runtime — inget C-limlager, inget eget bygg-steg. JNI ger lägre overhead men kräver genererade headers (`javac -h`) och en kompilerad bryggfil. Exemplen nedan använder JNA. Beroende: `net.java.dev.jna:jna:5.x` (Maven).

---

## Modul 1: CSV-batchparser

**Fil:** `csv_parser.c` / `csv_parser.h`  
**Kompilering:** `gcc -O2 -fopenmp -shared -fPIC -o libcsvparser.so csv_parser.c`

### API

```c
typedef struct {
    int     from_account_id;
    char    to_iban[35];
    double  amount;
    char    reference[101];
    int     valid;         // 1 = ok, 0 = parsningsfel
    char    error[256];    // felmeddelande om valid == 0
} CsvRow;

// Parsar CSV-innehåll. Allokerar och returnerar array av CsvRow.
// rows_out: antal rader (exkl. header)
// Anroparen ansvarar för att frigöra minnet med free_csv_rows().
CsvRow* parse_csv(const char* content, int content_len, int* rows_out);

void free_csv_rows(CsvRow* rows);
```

### Java JNA-wrapper (ska implementeras)

```java
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;
import java.util.List;

public interface CsvParserLib extends Library {
    CsvParserLib INSTANCE = Native.load("csvparser", CsvParserLib.class);

    // Returnerar pekare till första CsvRow i den C-allokerade arrayen
    Pointer parse_csv(String content, int contentLen, IntByReference rowsOut);
    void free_csv_rows(Pointer rows);

    @Structure.FieldOrder({"fromAccountId", "toIban", "amount", "reference", "valid", "error"})
    class CsvRow extends Structure {
        public int    fromAccountId;
        public byte[] toIban    = new byte[35];
        public double amount;
        public byte[] reference = new byte[101];
        public int    valid;
        public byte[] error     = new byte[256];
    }
}

// Användning (typad array via Structure.toArray, frigör alltid C-minnet efteråt):
//   var n = new IntByReference();
//   Pointer p = CsvParserLib.INSTANCE.parse_csv(csv, csv.length(), n);
//   CsvRow first = Structure.newInstance(CsvRow.class, p);
//   CsvRow[] rows = (CsvRow[]) first.toArray(n.getValue());
//   try { ... } finally { CsvParserLib.INSTANCE.free_csv_rows(p); }
```

### Krav
- RFC 4180-kompatibel (citerade fält med inbäddade kommatecken och radbrytningar)
- OpenMP parallellism: varje tråd processar ett CSV-segment
- Mål: 500 rader < 5 ms, 10 000 rader < 50 ms på 4-kärna

---

## Modul 2: IBAN/BIC-validator

**Fil:** `iban_validator.c` / `iban_validator.h`  
**Kompilering:** `gcc -O2 -shared -fPIC -o libiban.so iban_validator.c`

### API

```c
// Returnerar 1 om IBAN är giltig (format + MOD97), annars 0
// error_out: om 0 returneras, sätts till felkod
//   1 = för kort/lång
//   2 = ogiltigt landskod
//   3 = felaktigt tecken
//   4 = MOD97-fel (fel kontrollsiffror)
int validate_iban(const char* iban, int* error_out);

// Returnerar 1 om BIC är giltig (ISO 9362), annars 0
int validate_bic(const char* bic);

// MOD97-kontrollsiffra — returnerar beräknad checksumma (0-97)
int iban_mod97(const char* iban);
```

### Algoritm (ISO 13616 MOD97)

1. Flytta de första 4 tecknen sist
2. Ersätt varje bokstav med dess numeriska värde (A=10, B=11, ..., Z=35)
3. Beräkna MOD 97 på den resulterande heltalssträngen
4. Resultatet ska vara 1

### Java JNA-wrapper (ska implementeras)

```java
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

public interface IbanLib extends Library {
    IbanLib INSTANCE = Native.load("iban", IbanLib.class);
    int validate_iban(String iban, IntByReference errorOut);
    int validate_bic(String bic);
    int iban_mod97(String iban);
}

// Anrop med graceful fallback om biblioteket saknas (matchar v1-beteendet):
public boolean validateIban(String iban) {
    if (!nativeLibraryAvailable) {
        return fallbackValidateIban(iban); // regex fallback (= v1 BUG-003-läget)
    }
    IntByReference err = new IntByReference();
    return IbanLib.INSTANCE.validate_iban(iban.replace(" ", ""), err) == 1;
}
```

---

## Modul 3: Audit-signering (append-only, tamper-evident)

**Fil:** `audit_signer.c` / `audit_signer.h`  
**Kompilering:** `gcc -O2 -shared -fPIC -o libauditsigner.so audit_signer.c -lssl -lcrypto`

### Format

Varje loggrad: `TIMESTAMP|USER_ID|ACTION|ENTITY_ID|DESCRIPTION|PREV_HASH|HMAC`

- `PREV_HASH`: SHA256 av föregående rads hela innehåll (hex)
- `HMAC`: HMAC-SHA256(rad_utan_hmac, secret_key) (hex)
- Första radens `PREV_HASH` = `0000...0000` (64 nollor)

### API

```c
// Lägg till en loggrad. Hämtar föregående hash från log_path automatiskt.
// Returnerar 0 vid lyckat skrivande, -1 vid fel.
int audit_append(
    const char* log_path,
    const char* secret_key,
    int user_id,
    const char* action,
    int entity_id,
    const char* description
);

// Verifiera hela loggfilen. Returnerar -1 om ok, annars radnummer för
// första trasiga posten (1-indexerat).
int audit_verify(const char* log_path, const char* secret_key);
```

### Java JNA-wrapper (ska implementeras)

```java
import com.sun.jna.*;

public interface AuditSignerLib extends Library {
    AuditSignerLib INSTANCE = Native.load("auditsigner", AuditSignerLib.class);
    int audit_append(String logPath, String secretKey, int userId,
                     String action, int entityId, String description);
    int audit_verify(String logPath, String secretKey);
}
// secret_key läses från miljövariabel AUDIT_SIGNING_KEY (aldrig i källkod) —
// i Spring t.ex. via @Value("${audit.signing-key}") bundet mot env.
```

### Säkerhetskrav
- `secret_key` ska aldrig lagras i källkod — läses från miljövariabel `AUDIT_SIGNING_KEY`
- Filen ska öppnas med `O_APPEND | O_SYNC` för att förhindra partiella skrivningar
- Verifiering ska köras vid applikationsstart (t.ex. `@PostConstruct` / `ApplicationRunner`) och rapporteras i healthcheck (Spring Actuator `HealthIndicator`)

---

## Bygga alla moduler

```bash
cd native/
make all   # kompilerar alla tre .so-filer
make test  # kör enhetstester (kräver check.h eller cmocka)
```

`.so`-filerna måste ligga på `jna.library.path` (eller `java.library.path`) i runtime — t.ex. starta JVM med `-Djna.library.path=native/` eller kopiera in dem i Docker-imagen.

Makefile (ska skapas i v2):
```makefile
CC = gcc
CFLAGS = -O2 -Wall -fPIC
LDFLAGS = -shared

all: libcsvparser.so libiban.so libauditsigner.so

libcsvparser.so: csv_parser.c
	$(CC) $(CFLAGS) -fopenmp $(LDFLAGS) -o $@ $<

libiban.so: iban_validator.c
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $<

libauditsigner.so: audit_signer.c
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< -lssl -lcrypto

clean:
	rm -f *.so
```
