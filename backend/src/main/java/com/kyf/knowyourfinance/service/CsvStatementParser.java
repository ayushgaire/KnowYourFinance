package com.kyf.knowyourfinance.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the raw bytes of an uploaded CSV bank statement into a list of
 * ParsedRow objects - plain (date, description, amount) triples with no
 * knowledge of Spring, the database, or categories. Keeping this class
 * "dumb" (it only knows CSV syntax, nothing about banking or our data
 * model) is deliberate: it's the easiest layer to unit-test, and if we
 * ever need to parse OFX or QFX files instead, we write a different
 * parser with the same ParsedRow output instead of touching this one.
 *
 * We hand-write this parser instead of pulling in a CSV library
 * (Apache Commons CSV, OpenCSV, etc.) for two reasons: (1) this sandbox
 * can't resolve new Maven dependencies anyway (see build-log 01), and
 * (2) bank statement CSVs are simple enough - three predictable columns,
 * no exotic quoting - that a ~40-line parser is easier to reason about
 * than pulling in an entire library for it. If statement formats get
 * messier later (quoted commas, multi-line fields), that's the moment to
 * reach for a real library instead of growing this by hand.
 *
 * Text formats are still read by hand. Excel (.xlsx) is a zipped XML format,
 * which is not sensible to parse by hand, so StatementTableReader uses Apache
 * POI for that one format only. Besides CSV this parser therefore reads TSV,
 * TXT and XLSX, and accepts several common date and amount styles.
 *
 * Expected layout (header row required):
 *   Date,Description,Amount
 *   2026-09-01,STARBUCKS #4521,-5.75
 *   2026-09-01,PAYROLL DEPOSIT,2450.00
 *
 * Convention: negative amount = money out (expense), positive = money
 * in (income). This matches how most banks actually export CSVs.
 */
@Component
public class CsvStatementParser {

    // The date layouts banks commonly use. They are tried in this order.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,          // 2026-09-01
            DateTimeFormatter.ofPattern("M/d/yyyy"),   // 09/01/2026 or 9/1/2026
            DateTimeFormatter.ofPattern("M-d-yyyy"),   // 09-01-2026
            DateTimeFormatter.ofPattern("M/d/yy"));    // 9/1/26

    /** Reads a comma separated file. Kept for callers that have only a stream. */
    public ParseResult parse(InputStream csvInputStream) throws IOException {
        return parseTable(StatementTableReader.readDelimited(csvInputStream, ','), true);
    }

    /**
     * Reads a statement in any supported format (CSV, TSV, TXT, XLSX),
     * chosen by the file name's extension.
     */
    public ParseResult parse(InputStream input, String fileName) throws IOException {
        return parseTable(StatementTableReader.read(input, fileName),
                fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv"));
    }

    /**
     * Turns table rows (header first, then Date, Description, Amount per
     * row) into parsed rows. A row that cannot be read is skipped and
     * counted, so one bad line never blocks the rest of the file (FR-103).
     */
    ParseResult parseTable(List<String[]> table) {
        return parseTable(table, false);
    }

    private ParseResult parseTable(List<String[]> table, boolean detectHeaders) {
        ColumnMapping mapping = detectHeaders && !table.isEmpty()
                ? ColumnMapping.detect(table.getFirst()) : ColumnMapping.LEGACY;
        List<ParsedRow> rows = new ArrayList<>();
        int skippedRowCount = 0;

        // Start after the header, retaining the legacy positional layout for unfamiliar headers.
        for (int i = 1; i < table.size(); i++) {
            String[] columns = table.get(i);
            if (columns.length == 0 || isBlankRow(columns)) {
                continue; // blank lines are normal, not a parse failure
            }
            if (columns.length <= mapping.lastColumn()) {
                skippedRowCount++;
                continue;
            }
            try {
                LocalDate date = parseDate(columns[mapping.date()].trim());
                String description = columns[mapping.description()].trim();
                BigDecimal amount = mapping.readAmount(columns);
                rows.add(new ParsedRow(date, description, amount));
            } catch (DateTimeParseException | NumberFormatException e) {
                skippedRowCount++;
            }
        }
        return new ParseResult(rows, skippedRowCount);
    }

    private record ColumnMapping(int date, int description, int amount, int debit, int credit) {
        private static final ColumnMapping LEGACY = new ColumnMapping(0, 1, 2, -1, -1);

        static ColumnMapping detect(String[] header) {
            int date = find(header, "date", "transaction date", "posting date", "posted date", "post date");
            int description = find(header, "description", "transaction description", "merchant", "payee", "memo");
            int amount = find(header, "amount", "transaction amount", "signed amount");
            int debit = find(header, "debit", "debit amount", "withdrawal", "withdrawals", "money out");
            int credit = find(header, "credit", "credit amount", "deposit", "deposits", "money in");

            if (date < 0 || description < 0) {
                return LEGACY;
            }
            if (debit >= 0 && credit >= 0) {
                return new ColumnMapping(date, description, -1, debit, credit);
            }
            return amount >= 0 ? new ColumnMapping(date, description, amount, -1, -1) : LEGACY;
        }

        private static int find(String[] header, String... aliases) {
            for (String alias : aliases) {
                for (int i = 0; i < header.length; i++) {
                    String name = header[i].strip().toLowerCase(Locale.ROOT)
                            .replaceAll("[ _-]+", " ");
                    if (name.equals(alias)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        int lastColumn() {
            return Math.max(Math.max(date, description), Math.max(amount, Math.max(debit, credit)));
        }

        BigDecimal readAmount(String[] columns) {
            if (amount >= 0) {
                return parseAmount(columns[amount]);
            }
            String debitText = columns[debit];
            String creditText = columns[credit];
            BigDecimal out = debitText.isBlank() ? BigDecimal.ZERO : parseAmount(debitText).abs();
            BigDecimal in = creditText.isBlank() ? BigDecimal.ZERO : parseAmount(creditText).abs();
            // Exactly one side must hold money. A row where both are blank or
            // zero is not a transaction, so it is skipped and counted.
            boolean neitherSide = out.signum() == 0 && in.signum() == 0;
            boolean bothSides = out.signum() > 0 && in.signum() > 0;
            if (neitherSide || bothSides) {
                throw new NumberFormatException("Expected one debit or credit");
            }
            return in.subtract(out);
        }
    }

    private static boolean isBlankRow(String[] columns) {
        for (String c : columns) {
            if (c != null && !c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static LocalDate parseDate(String text) {
        DateTimeParseException last = null;
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * Understands the amount styles banks use: -5.75, $5.75, 1,234.50 and
     * (5.75), where brackets mean money out.
     */
    private static BigDecimal parseAmount(String text) {
        String t = text.trim();
        boolean bracketed = t.startsWith("(") && t.endsWith(")");
        if (bracketed) {
            t = t.substring(1, t.length() - 1);
        }
        t = t.replace("$", "").replace(",", "").replace(" ", "");
        BigDecimal value = new BigDecimal(t);
        return bracketed ? value.negate() : value;
    }

    /**
     * What parse() hands back: the rows that parsed successfully, plus
     * how many didn't (FR-104). Bundling both in one object instead of
     * just returning the row list means a caller can't accidentally
     * forget to report the skipped count - it's sitting right there
     * next to the rows.
     */
    public static class ParseResult {
        private final List<ParsedRow> rows;
        private final int skippedRowCount;

        public ParseResult(List<ParsedRow> rows, int skippedRowCount) {
            this.rows = rows;
            this.skippedRowCount = skippedRowCount;
        }

        public List<ParsedRow> getRows() {
            return rows;
        }

        public int getSkippedRowCount() {
            return skippedRowCount;
        }
    }

    /**
     * The plain, pre-redaction, pre-categorization output of parsing one
     * CSV row. Note this is NOT a Transaction yet - redaction and
     * categorization both still need to happen before this becomes
     * something we save.
     */
    public static class ParsedRow {
        private final LocalDate date;
        private final String description;
        private final BigDecimal amount;

        public ParsedRow(LocalDate date, String description, BigDecimal amount) {
            this.date = date;
            this.description = description;
            this.amount = amount;
        }

        public LocalDate getDate() {
            return date;
        }

        public String getDescription() {
            return description;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
