import { describe, expect, it } from "vitest";
import { CSV_HEADER, parseCsvPreview } from "./csvPreview";

describe("parseCsvPreview", () => {
  it("reads rows, quoted fields, CRLF and a BOM", () => {
    const result = parseCsvPreview(
      `\uFEFF${CSV_HEADER}\r\n1,SE35 5000 0000 0549 1000 0003,1250.50,"Lön, ""oktober"""\r\n2,se7850000000054910000005,8000,\r\n`,
    );

    expect(result).toEqual({
      ok: true,
      rows: [
        { line: 2, fromAccountId: 1, toIban: "SE3550000000054910000003", amount: 1250.5, reference: 'Lön, "oktober"' },
        { line: 3, fromAccountId: 2, toIban: "SE7850000000054910000005", amount: 8000, reference: "" },
      ],
    });
  });

  it("keeps a line break inside quotes in the same row", () => {
    const result = parseCsvPreview(`${CSV_HEADER}\n1,SE3550000000054910000003,10.00,"rad ett\nrad två"\n2,SE3550000000054910000003,20.00,B\n`);

    expect(result.ok && result.rows.map((row) => [row.line, row.reference])).toEqual([
      [2, "rad ett\nrad två"],
      [4, "B"],
    ]);
  });

  it("rejects a wrong header", () => {
    expect(parseCsvPreview("account,iban,amount,reference\n1,SE35,10,A")).toEqual({
      ok: false,
      error: `Filen måste börja med rubrikraden: ${CSV_HEADER}`,
    });
  });

  it("rejects a file without payments", () => {
    expect(parseCsvPreview(`${CSV_HEADER}\n\n`)).toEqual({ ok: false, error: "Filen innehåller inga betalningar." });
  });

  it.each([
    ["abc,SE3550000000054910000003,10.00,A", "Rad 2: konto-id måste vara ett heltal."],
    ["1,SE3550000000054910000003,10.123,A", "Rad 2: beloppet ska vara större än 0, med punkt och högst två decimaler."],
    ["1,SE3550000000054910000003,10,50,A", "Rad 2: raden ska ha fyra kolumner."],
    ["1,SE3550000000054910000003,0,A", "Rad 2: beloppet ska vara större än 0, med punkt och högst två decimaler."],
    ["1,,10.00,A", "Rad 2: mottagarens IBAN saknas."],
    ['1,SE3550000000054910000003,10.00,"aldrig stängd', "Rad 2: ett citattecken stängs aldrig."],
  ])("rejects the row %s", (row, error) => {
    expect(parseCsvPreview(`${CSV_HEADER}\n${row}\n`)).toEqual({ ok: false, error });
  });

  it("rejects more than 500 payments", () => {
    const rows = Array.from({ length: 501 }, () => "1,SE3550000000054910000003,1.00,A").join("\n");

    expect(parseCsvPreview(`${CSV_HEADER}\n${rows}`)).toEqual({
      ok: false,
      error: "Filen får innehålla högst 500 betalningar.",
    });
  });
});
