// Läser CSV-filen i webbläsaren så att användaren kan se betalningarna innan de skickas.
// Det är bara en förhandsvisning: backend parsar filen igen med den native parsern och
// avgör om den godkänns (CsvPaymentImportService).

export const CSV_HEADER = "from_account_id,to_iban,amount,reference";
export const MAX_ROWS = 500;

export interface CsvPreviewRow {
  line: number;
  fromAccountId: number;
  toIban: string;
  amount: number;
  reference: string;
}

export type CsvPreviewResult =
  | { ok: true; rows: CsvPreviewRow[] }
  | { ok: false; error: string };

interface CsvRecord {
  line: number;
  fields: string[];
}

// RFC 4180: fält inom citattecken får innehålla komma, radbrytning och "" som citattecken
function splitRecords(text: string): CsvRecord[] {
  const records: CsvRecord[] = [];
  let fields: string[] = [];
  let field = "";
  let inQuotes = false;
  let line = 1;
  let recordLine = 1;

  for (let i = 0; i < text.length; i++) {
    const char = text[i];

    if (inQuotes) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        inQuotes = false;
      } else {
        if (char === "\n") line++;
        field += char;
      }
      continue;
    }

    if (char === '"') {
      inQuotes = true;
    } else if (char === ",") {
      fields.push(field);
      field = "";
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && text[i + 1] === "\n") i++;
      fields.push(field);
      records.push({ line: recordLine, fields });
      fields = [];
      field = "";
      line++;
      recordLine = line;
    } else {
      field += char;
    }
  }

  if (inQuotes) {
    throw new Error(`Rad ${recordLine}: ett citattecken stängs aldrig.`);
  }
  if (field !== "" || fields.length > 0) {
    fields.push(field);
    records.push({ line: recordLine, fields });
  }

  // Tomma rader räknas inte som betalningar
  return records.filter((record) => !(record.fields.length === 1 && record.fields[0].trim() === ""));
}

export function parseCsvPreview(content: string): CsvPreviewResult {
  const text = content.startsWith("\uFEFF") ? content.slice(1) : content;

  let records: CsvRecord[];
  try {
    records = splitRecords(text);
  } catch (error) {
    return { ok: false, error: error instanceof Error ? error.message : "Filen kunde inte läsas." };
  }

  const [header, ...dataRecords] = records;
  if (!header || header.fields.map((field) => field.trim()).join(",") !== CSV_HEADER) {
    return { ok: false, error: `Filen måste börja med rubrikraden: ${CSV_HEADER}` };
  }
  if (dataRecords.length === 0) {
    return { ok: false, error: "Filen innehåller inga betalningar." };
  }
  if (dataRecords.length > MAX_ROWS) {
    return { ok: false, error: `Filen får innehålla högst ${MAX_ROWS} betalningar.` };
  }

  const rows: CsvPreviewRow[] = [];
  for (const record of dataRecords) {
    if (record.fields.length !== 4) {
      return { ok: false, error: `Rad ${record.line}: raden ska ha fyra kolumner.` };
    }
    const [fromAccountId, toIban, amount, reference] = record.fields.map((field) => field.trim());

    if (!/^\d+$/.test(fromAccountId)) {
      return { ok: false, error: `Rad ${record.line}: konto-id måste vara ett heltal.` };
    }
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount) <= 0) {
      return {
        ok: false,
        error: `Rad ${record.line}: beloppet ska vara större än 0, med punkt och högst två decimaler.`,
      };
    }
    if (toIban === "") {
      return { ok: false, error: `Rad ${record.line}: mottagarens IBAN saknas.` };
    }

    rows.push({
      line: record.line,
      fromAccountId: Number(fromAccountId),
      toIban: toIban.replace(/\s+/g, "").toUpperCase(),
      amount: Number(amount),
      reference,
    });
  }

  return { ok: true, rows };
}
