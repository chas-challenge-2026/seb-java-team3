-- #228: attestantens meddelande vid godkännande eller avvisning står på själva auditraden. Det skrivs i
-- samma transaktion som beslutet, så historiken behåller meddelandet även om atteststeget ändras senare.
-- Meddelandet står kvar i description också, eftersom den signerade loggen bara får beskrivningen.
ALTER TABLE audit_entries ADD COLUMN comment VARCHAR(255);

-- Avvisningar från före V10 har meddelandet bara på atteststeget och inbakat i beskrivningstexten. Bara
-- steget som avvisade får en kommentar (övriga väntande steg avvisas utan), så det finns högst en per
-- betalning. Godkännanden hade inget meddelande före V10 och har inget att föra över. entity_type jämförs
-- utan hänsyn till skiftläge, som i AuditService (seed-datan i V5 skriver 'payment').
UPDATE audit_entries
SET comment = (
    SELECT step.comment
    FROM approval_steps step
    WHERE step.payment_id = audit_entries.entity_id
      AND step.status = 'rejected'
      AND step.comment IS NOT NULL
    ORDER BY step.step_number
    LIMIT 1
)
WHERE action = 'REJECT_PAYMENT'
  AND UPPER(entity_type) = 'PAYMENT';
