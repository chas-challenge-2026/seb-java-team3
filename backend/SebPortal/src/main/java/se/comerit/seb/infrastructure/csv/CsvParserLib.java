package se.comerit.seb.infrastructure.csv;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

public interface CsvParserLib extends Library {
    CsvParserLib INSTANCE = Native.load("csvparser", CsvParserLib.class);

    Pointer parse_csv(byte[] content, int contentLength, IntByReference rowsOut);

    void free_csv_rows(Pointer rows);

    @Structure.FieldOrder({
            "fromAccountId", "toIban", "amount", "reference", "valid", "error"
    })
    class CsvRow extends Structure {
        public int fromAccountId;
        public byte[] toIban = new byte[35];
        public double amount;
        public byte[] reference = new byte[101];
        public int valid;
        public byte[] error = new byte[256];

        public CsvRow() {
            super();
        }

        public CsvRow(Pointer pointer) {
            super(pointer);
        }
    }
}