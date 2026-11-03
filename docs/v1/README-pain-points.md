# Pain Points — SEB Företagsbetalningar v1 (Java)

## Vad som faktiskt fungerar

### Grundläggande betalningsflöde
- Inloggning med e-post och lösenord — fungerar för seeded-användare
- Kontoöversikt på Dashboard — visar saldo och senaste betalningar
- Skapa betalning ≤ 50 000 SEK — godkänns direkt, visas i historiken
- Skapa betalning > 50 000 SEK — hamnar i attestkorgen hos Johan Berg

### Attestflödet (lyckliga vägen)
- Johan loggar in och ser betalningen i attestkorgen
- Kan godkänna med valfri kommentar
- Betalning markeras som completed, saldo dras
- Kan avvisa — betalning markeras rejected

### Rollnavigering
- Initiator (Lisa) ser inte attestkorgen i navbaren
- Attestant (Johan) och Admin (Sara) ser den

---

## Vad som går sönder

### Batch-uppladdning med specialtecken
Testa med en CSV-fil där referensfältet innehåller kommatecken:
```
1,SE8550000000054910000003,5000.00,"Malmö Bygg, faktura 99"
```
Resultat: parsningsfel eller avtrunkerad referens — `String.split(",")` i `BatchController` delar fältet. Se [BUG-004](known-bugs.md#bug-004).

### Betalningar 200 001 – 500 000 SEK
Dessa hamnar i ett limbo-tillstånd. `PaymentController` skapar ett atteststeg (räcker per dess logik, tröskel 500 000), men `ApprovalController` lägger till ett extra (tröskel 200 000) och väntar på att det ska godkännas. Om det bara finns en attestant i tenanten godkänner de sig själva i steg 2. Se [BUG-006](known-bugs.md#bug-006).

### Notifieringar
SMTP-servern `smtp.malmobygg.local` existerar inte i Docker-nätverket. Alla `JavaMailSender.send`-försök misslyckas tyst i `catch (Exception e) {}`. Attestanter hittar sina uppgifter enbart via att logga in och kolla attestkorgen. Se [BUG-007](known-bugs.md#bug-007).

### Granskningsloggen är ofullständig
Batch-betalningar och delvisa atteststeg loggas enbart till `/tmp/audit.log` (inuti Docker-containern, via `FileWriter`). `AuditController` visar bara DB-poster. En finansiell revision skulle missa hälften av händelserna. Se [BUG-008](known-bugs.md#bug-008).

### Höga belopp
Direkta betalningar (≤ 50 000 SEK) drar aldrig saldot. Skapar du 100 direktbetalningar på 50 000 SEK var förblir saldot oförändrat. Se [BUG-009](known-bugs.md#bug-009).

### Skalbarhet
- Inget caching — varje sidvisning kör flera separata `JdbcTemplate`-queries
- Ingen explicit connection pooling utöver HikariCP-defaults
- `DashboardController` hämtar upp till 20 betalningar utan index på `created_at`
- `AuditController` hämtar 200 rader utan cursor-paginering

### Säkerhet
- SQL-injektionsbar inloggning (se [BUG-001](known-bugs.md#bug-001))
- MD5-lösenord (se [BUG-002](known-bugs.md#bug-002))
- IDOR i attestkorgen (se [BUG-011](known-bugs.md#bug-011))
- Inga CSRF-tokens på formulär (ingen Spring Security konfigurerad)
- Sessionscookies utan Secure-flagga i produktion

---

## Vad som saknas helt

- Glömt lösenord / kontoreaktivering
- 2FA
- Betalningshistorik per konto (filtrerbar)
- Exportfunktion (PDF/Excel)
- Valutaomvandling (allt är SEK)
- Revisionsspårbarhet (GDPR-krav på datautplåning saknas)
