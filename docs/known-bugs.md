# Kända buggar — SEB Företagsbetalningar v1 (Java)

Dessa buggar är **avsiktliga** och utgör refaktoreringsmål i v2-uppgiften.

Alla sökvägar är relativa till `backend/SebPortal/src/main/java/se/comerit/seb/`.

---

## BUG-001 — SQL-injektion i inloggning

**Fil:** `controller/AuthController.java`  
**Beskrivning:** E-post och lösenordshash konkateneras direkt in i SQL-strängen och körs via `JdbcTemplate.queryForList` utan parameterisering. En angripare kan logga in som vilken användare som helst via `' OR '1'='1`.  
**Exempel:** `email = ' OR '1'='1' --`  
**Severity:** Kritisk

---

## BUG-002 — MD5 lösenordshashning

**Fil:** `controller/AuthController.java`  
**Beskrivning:** Lösenord hashas med `MessageDigest.getInstance("MD5")`. MD5 är kryptografiskt trasigt. Rainbow tables existerar för de flesta vanliga lösenord. Lösenordet `password123` är trivialt att knäcka.  
**Severity:** Kritisk

---

## BUG-003 — IBAN-validering missar kontrollsiffror

**Filer:** `controller/PaymentController.java`, `controller/BatchController.java`  
**Beskrivning:** `Pattern` `^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$` kontrollerar enbart formatet, inte MOD97-kontrollsumman (ISO 13616). Regexen är dessutom copy-pastad i båda controllers i stället för att delas. Felaktiga IBAN:er med rätt format godkänns och betalningar skapas mot icke-existerande mottagarkonton.  
**Severity:** Hög

---

## BUG-004 — CSV-parser bryter vid komma i fält

**Fil:** `controller/BatchController.java`  
**Beskrivning:** `String.split(",")` hanterar inte citerade fält. En referens som `"Malmö Bygg, projektfaktura"` splittras i tre delar, vilket orsakar parsningsfel eller felaktig referens. Fält med ledande/avslutande mellanslag trimmas inte konsekvent.  
**Severity:** Medel

---

## BUG-005 — Partiell batch-insert utan transaktion

**Fil:** `controller/BatchController.java`  
**Beskrivning:** Varje rad i CSV-filen insertas med ett separat `jdbcTemplate.update`-anrop utan övergripande transaktion. Om rad 3 av 10 misslyckas är raderna 1–2 redan committade. Resultatsidan rapporterar "3 av 10 behandlade" men det är oklart vilka som faktiskt insertats.  
**Severity:** Medel

---

## BUG-006 — Inkonsekvent attestationströskel

**Filer:** `controller/PaymentController.java`, `controller/ApprovalController.java`  
**Beskrivning:** `PaymentController` skapar ett extra atteststeg för betalningar > 500 000 SEK (`DOUBLE_APPROVAL_THRESHOLD`). `ApprovalController` förväntar sig dubbel attest vid > 200 000 SEK (`REQUIRES_DOUBLE_APPROVAL_THRESHOLD`) och försöker retroaktivt lägga till ett andra steg. En betalning på 300 000 SEK fastnar i "pending_approval" permanent eftersom `PaymentController` skapar ett steg men `ApprovalController` alltid lägger till ytterligare ett.  
**Severity:** Hög (race condition / dead-lock i flödet)

---

## BUG-007 — Notifieringar sväljs tyst

**Filer:** `controller/PaymentController.java`, `controller/ApprovalController.java`  
**Beskrivning:** `catch (Exception e) {}` med tom kropp kring `JavaMailSender.send(...)`. Om SMTP-servern inte svarar vet varken systemet eller användaren att notifieringen misslyckades. Attestanter kan missa betalningar som väntar på deras godkännande.  
**Severity:** Medel

---

## BUG-008 — Dubbel och inkonsekvent audit-logg

**Filer:** Samtliga controllers  
**Beskrivning:** Granskningsloggen skrivs till två platser: PostgreSQL-tabellen `audit_entries` och textfilen `/tmp/audit.log` (via `FileWriter`). Vilken destination som används varierar per händelse:

| Händelse | DB | Fil |
|---|---|---|
| CREATE_PAYMENT | ✅ | ✅ |
| APPROVE_PAYMENT (slutlig) | ✅ | ❌ |
| PARTIAL_APPROVE (delsteg) | ❌ | ✅ |
| REJECT_PAYMENT | ✅ | ✅ |
| BATCH_PAYMENT | ❌ | ✅ |

`AuditController` läser bara DB-poster. Batch-betalningar och delvisa atteststeg syns aldrig i UI:t.  
**Severity:** Medel

---

## BUG-009 — Kontobalans dras inte vid direktbetalning

**Filer:** `controller/PaymentController.java`, `controller/ApprovalController.java`  
**Beskrivning:** I `PaymentController` sätts status direkt till `completed` för betalningar ≤ 50 000 SEK, men kontobalansen uppdateras aldrig. Balansavdrag sker enbart i `ApprovalController` (godkännandeflödet). Driftkontot kan visa samma saldo oavsett hur många direktbetalningar som görs.  
**Severity:** Hög

---

## BUG-010 — Hårdkodad uppkopplingssträng på sex ställen

**Beskrivning:** `Host=localhost;Port=5432;Database=seb;Username=seb;Password=seb123` är hårdkodad som fallback i varje controller (AuthController, DashboardController, PaymentController, ApprovalController, BatchController, AuditController). Den faktiska anslutningen sker via `JdbcTemplate` konfigurerad i `application.properties`, men credential-strängen ligger ändå kvar duplicerad i koden. Credential rotation kräver omkompilering.  
**Severity:** Låg (men ett underhållsproblem)

---

## BUG-011 — IDOR i attestkorgen

**Fil:** `controller/ApprovalController.java`  
**Beskrivning:** `approvalStepId` kommer från ett dolt formulärfält (`@RequestParam Integer approvalStepId`). En attestant kan manipulera detta värde och godkänna/avvisa steg som tillhör en annan attestant. `stepAttestantId` hämtas men jämförs aldrig med sessionens `userId`.  
**Severity:** Hög

---

## BUG-012 — Ingen filstorleksgräns för batch-uppladdning

**Fil:** `controller/BatchController.java`  
**Beskrivning:** Kommentaren i UI:t säger "Max 1 MB" men ingen kod enforcar detta. `spring.servlet.multipart.max-file-size=-1` i `application.properties` stänger av Springs inbyggda gräns. En fil på 100 MB läses in i minnet, vilket kan orsaka OOM.  
**Severity:** Låg
