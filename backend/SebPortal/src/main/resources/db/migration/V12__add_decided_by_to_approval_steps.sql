-- Dubbel attest (ADR 0016): vem som faktiskt fattade beslutet, som inte behöver vara den tilldelade
-- attestanten eftersom en admin får besluta om företagets steg. Utan kolumnen går det inte att se om samma
-- person försöker godkänna båda stegen. Äldre steg lämnas tomma: vem som beslutade går inte att veta i
-- efterhand, och att gissa på den tilldelade attestanten hade kunnat peka ut fel person.
ALTER TABLE approval_steps ADD COLUMN decided_by INT REFERENCES users(id);
