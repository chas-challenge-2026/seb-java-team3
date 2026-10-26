# Native C/C++ Moduler — v2

Denna katalog innehåller (i v2) tre prestandakritiska och säkerhetskritiska moduler implementerade i C/C++. De anropas från Java via **JNA** (Java Native Access — rekommenderat, ingen boilerplate) eller **JNI** (om finkornig kontroll behövs).

## Varför native?

1. **CSV-parsern** ska hantera batchfiler på 10 000+ rader med parallell körning. En ren Java-implementation är tillräcklig för v1:s 500-radersgräns men skalas inte till produktionsvolymer.

2. **IBAN-validatorn** implementerar ISO 13616 MOD97-algoritmen. En C-implementation är trivial att formellt verifiera och enkel att porta till mobilklienter (iOS/Android via FFI).

3. **Audit-signeringen** kräver kryptografisk integritet. En HMAC-SHA256-kedja i C är lättare att granska och auditeras utan JVM-runtime-beroenden.

> **JNA vs JNI:** JNA laddar `.so`-filen direkt och mappar funktioner mot ett Java-interface i runtime — inget C-limlager, inget eget bygg-steg. JNI ger lägre overhead men kräver genererade headers (`javac -h`) och en kompilerad bryggfil. Exemplen nedan använder JNA. Beroende: `net.java.dev.jna:jna:5.x` (Maven).

---

## Modul 1: CSV-batchparser

**Fil:** `csv/csv_parser.c` / `csv/csv_parser.h`  
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

**Filer (i `native/iban/`):** 
- `iban_validator.h` — API-definition
- `iban_validator.c` — Implementering (ISO 13616 MOD97 + ISO 9362 BIC-validering)
- `iban_validator_test.c` — Enhetstester (Check framework)
- `test_simple.c` — Fristående testprogram utan Check
- `Makefile` — Bygg- och testskript för modulen (anropas från `native/Makefile`)

### Status
- ✅ **IMPLEMENTERAD** — MOD97-algoritm, BIC-validering, enhetstester
- ✅ **IMPLEMENTERAD** — JNA-integrering mot betalningsformuläret
- **Issue #73:** MOD97-algoritm implementering
- **Issue #74:** Enhetstester (denna fil)
- **Issue #75:** JNA-integrering mot betalningsformuläret (`backend/SebPortal/src/main/java/se/comerit/seb/infrastructure/iban/`)

### API

```c
// Returnerar 1 om IBAN är giltig (format + MOD97), annars 0
// error_out: om 0 returneras, sätts till felkod
//   1 = för kort/lång (längd < 15 eller > 34)
//   2 = ogiltigt landskod (första 2 tecken måste vara A-Z)
//   3 = felaktigt tecken (endast A-Z, 0-9, mellanslag, bindestrecks är tillåtna)
//   4 = MOD97-fel (fel kontrollsiffror eller ogiltig checksumma)
int validate_iban(const char* iban, int* error_out);

// Returnerar 1 om BIC är giltig (ISO 9362), annars 0
// Format: AAAA BB CC [DDD]
//   - 4 bokstäver: bankkod
//   - 2 bokstäver: landskod (ISO 3166-1 alpha-2)
//   - 2 tecken (bokstäver eller siffror): ortskod
//   - 3 tecken valfritt (bokstäver eller siffror): filialskod
int validate_bic(const char* bic);
```

### Algoritm (ISO 13616 MOD97)

1. Normalisering: ta bort mellanslag och bindestreck, konvertera till versaler
2. Flytta de första 4 tecknen (landskod + kontrollsiffror) till slutet
3. Ersätt varje bokstav med dess numeriska värde (A=10, B=11, ..., Z=35)
4. Beräkna MOD 97 på den resulterande heltalssträngen
5. Om MOD 97 == 1 är IBAN giltig; annars felaktig kontrollsumma

### Testning

```bash
# Installera Check-ramverk (om det inte redan är installerat)
# Ubuntu/Debian:
sudo apt-get install check

# macOS:
brew install check

# Bygga och köra tester:
cd native/
make check-deps    # Verifiera beroenden
make test          # Kör alla enhetstester
make lib           # Bygga endast bibliotek
make clean         # Rensa upp
```

Testsviten (`iban_validator_test.c`) täcker:
- ✅ Giltiga IBAN:er från Sverige, Tyskland, Frankrike, UK, Spanien
- ✅ Normalisering: mellanslag, bindestrecks, versaler/gemener
- ✅ Felaktig längd: för korta, för långa, tomma
- ✅ Felaktig landskod: siffror i stället för bokstäver
- ✅ Felaktiga kontrollsiffror: måste vara siffror
- ✅ Felaktiga tecken: specialtecken avvisas
- ✅ MOD97-felkontroll: ändring av någon siffra descobert
- ✅ Giltiga BIC:er (8 och 11 tecken)
- ✅ Felaktiga BIC:er (längd, format, tecken)
- ✅ NULL-pekare och felhantering

### Kompilering & bibliotek

```bash
# Bygga delad bibliotek (.so):
gcc -O2 -shared -fPIC -o native/build/lib/libiban.so native/iban/iban_validator.c

# Länka test (kräver Check-ramverk):
gcc -std=c99 -Wall -Wextra native/iban/iban_validator_test.c native/iban/iban_validator.c \
    $(pkg-config --cflags --libs check) -o native/build/test/iban_validator_test
./native/build/test/iban_validator_test
```

### Java JNA-wrapper (implementerad)

Se `backend/SebPortal/src/main/java/se/comerit/seb/infrastructure/iban/IbanLib.java` och `IbanValidatorService.java`.

```java
import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

public interface IbanLib extends Library {
    IbanLib INSTANCE = Native.load("iban", IbanLib.class);
    
    int validate_iban(String iban, IntByReference errorOut);
    int validate_bic(String bic);
}

// Anrop med graceful fallback om biblioteket saknas — till skillnad från v1
// (BUG-003) kör fallbacken en riktig Java MOD97-kontroll, inte bara ett
// formatcheck, så ogiltiga kontrollsiffror stoppas även utan .so-filen:
public boolean validateIban(String iban) {
    if (!nativeLibAvailable) {
        return validateIbanMod97(iban); // Java MOD97-fallback
    }
    IntByReference err = new IntByReference();
    return IbanLib.INSTANCE.validate_iban(iban, err) == 1;
}
```

---

## Modul 3: Audit-signering (append-only, tamper-evident)

**Filer:** `audit/audit_signer.h`, `audit/audit_signer.c`, `audit/audit_signer_test.c` (39 Check-tester), `audit/Makefile`  
**Bibliotek:** `libauditsigner.so` (länkar mot OpenSSL: `libssl-dev` vid bygge, `libssl3` i runtime)

### Status
- ✅ **IMPLEMENTERAD** — C-biblioteket med enhetstester
- ✅ **IMPLEMENTERAD** — JNA-wrapper, koppling mot `AuditService`, verifiering vid start och hälsokontroll

### Format

Varje loggrad slutar med `\n`: `TIMESTAMP|USER_ID|ACTION|ENTITY_ID|DESCRIPTION|PREV_HASH|HMAC`

- `TIMESTAMP`: sätts av biblioteket, UTC, `YYYY-MM-DDTHH:MM:SSZ`
- `PREV_HASH`: SHA-256 (gemena hex) av hela föregående rad, inklusive dess HMAC och utan radbrytningen. Första radens `PREV_HASH` = 64 nollor
- `HMAC`: HMAC-SHA256 (gemena hex) med `secret_key` över raden fram till, men utan, `|` framför HMAC-fältet
- `ACTION` och `DESCRIPTION` escapas: `|` blir `\|` och `\` blir `\\`. CR och LF är inte tillåtna (anropet misslyckas)
- Gränser: `ACTION` högst 64 tecken, `DESCRIPTION` högst 1024 tecken (före escapning). För långa värden avvisas, de kapas inte
- Filen skapas med rättigheterna 0600 och öppnas med `O_APPEND | O_SYNC`
- Samtidiga anrop (trådar och processer) serialiseras med `flock`, så kedjan kan inte grenas

### API

```c
// Lägg till en signerad loggrad. Hämtar föregående hash från log_path automatiskt.
// Returnerar 0 (AUDIT_OK) vid lyckat skrivande, -1 (AUDIT_ERROR) vid fel. Vid fel skrivs ingenting.
// Fel: NULL-argument, tom nyckel eller tom action, för långa fält, CR/LF i fält,
// filen kan inte öppnas, eller sista raden i filen saknar radbrytning (avbruten skrivning).
int audit_append(
    const char* log_path,
    const char* secret_key,
    int user_id,
    const char* action,
    int entity_id,
    const char* description
);

// Verifiera hela loggfilen. Returnerar:
//   -1 (AUDIT_VERIFY_OK)       hela kedjan stämmer. Saknad eller tom fil räknas som en intakt, tom logg
//   n >= 1                     radnummer (1-indexerat) för första trasiga posten: fel hash/HMAC,
//                              felformaterad rad, blank rad eller sista raden utan radbrytning
//   0 (AUDIT_VERIFY_IO_ERROR)  NULL-argument eller filen kan inte läsas (t.ex. en katalog)
int audit_verify(const char* log_path, const char* secret_key);
```

### Begränsningar
- Rader som tas bort **i slutet** av loggen upptäcks inte av en hashkedja ensam (inget senare finns som märker det). Skydda genom att spara senaste hashen på annat ställe, t.ex. i databasen
- Den som känner till `secret_key` kan skapa en giltig kedja

### Java-integration (implementerad)

Se `backend/SebPortal/src/main/java/se/comerit/seb/infrastructure/audit/`:

- `AuditSignerLib` — JNA-interfacet. Till skillnad från `IbanLib` finns ingen statisk `INSTANCE`: ett misslyckat statiskt laddningsförsök hade förstört klassen för resten av JVM:en. Ladda med `AuditSignerLib.load()`
- `AuditSignerService` — läser `audit.signing-key`, `audit.log-path` och `audit.signing-required`, skriver rader (`append`) och verifierar kedjan (`verify`)
- `AuditSignerHealthIndicator` — visar kedjans skick i `/actuator/health`

Så fungerar kopplingen:
- `AuditService.record()` sparar databasraden som vanligt och skriver den signerade raden **efter commit**. En händelse som rullas tillbaka hamnar alltså aldrig i den signerade loggen
- Ett signeringsfel stoppar inte betalningen. Det loggas, räknas, och hälsokontrollen blir DOWN tills appen startas om
- Kedjan verifieras vid appstart och därefter högst en gång i minuten när hälsokontrollen frågar. DOWN betyder trasig kedja, oläsbar fil eller saknade rader. UNKNOWN betyder att signering är avstängd (inget bibliotek eller ingen nyckel)
- Javasidan kapar `action` till 64 och `description` till 1024 byte (UTF-8) och byter CR/LF/NUL mot mellanslag, eftersom C-biblioteket annars avvisar raden
- `audit.signing-required=true` (`AUDIT_SIGNING_REQUIRED=true`) gör att appen vägrar starta utan biblioteket, utan nyckel eller med dev-nyckeln. Sätt det i stage och produktion
- Den signerade raden innehåller användare, action, entitets-id och beskrivning, men inte tenant eller entitetstyp (C-API:t har inga fält för dem). Dessa finns kvar i databastabellen `audit_entries`

### Säkerhetskrav
- `secret_key` lagras aldrig i källkod i produktion — läses från miljövariabel `AUDIT_SIGNING_KEY` (dev-defaulten i `application.properties` är bara för lokal körning)
- Filen öppnas med `O_APPEND | O_SYNC` för att förhindra partiella skrivningar
- Verifiering körs vid applikationsstart och rapporteras i healthcheck

---

## Bygga alla moduler

```bash
cd native/
make lib          # bygger alla .so-filer till native/build/lib/
make test         # kör enhetstester för alla moduler (kräver Check)
make test-iban    # kör bara en moduls tester
make iban         # bygger bara en modul
```

Varje modul ligger i en egen mapp (`native/iban/`, `native/csv/`, `native/audit/`) med en egen `Makefile`. Toppnivå-`Makefile` loopar över modulerna i `MODULES` — lägg till nya moduler där. Mellanfiler hamnar i `native/build/<modul>/` och alla bibliotek i `native/build/lib/`.

`.so`-filerna måste ligga på `jna.library.path` (eller `java.library.path`) i runtime — t.ex. starta JVM med `-Djna.library.path=native/build/lib` eller kopiera in dem i Docker-imagen.
