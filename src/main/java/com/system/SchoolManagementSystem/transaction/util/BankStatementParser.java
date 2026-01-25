package com.system.SchoolManagementSystem.transaction.util;

import com.system.SchoolManagementSystem.transaction.entity.BankTransaction;
import com.system.SchoolManagementSystem.transaction.enums.PaymentMethod;
import com.system.SchoolManagementSystem.transaction.enums.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Component
public class BankStatementParser {

    // ========== FLEXIBLE HEADER MAPPINGS ==========
    // Add more variations to handle different bank formats
    private static final List<String> DATE_COLUMNS = Arrays.asList(
            "date", "transaction date", "txn date", "txndate", "transaction_date",
            "value date", "value_date", "posting date", "posting_date",
            "transactiondate", "tx date", "trandate"
    );

    private static final List<String> AMOUNT_COLUMNS = Arrays.asList(
            "amount", "transaction amount", "debit", "credit", "amt", "transaction_amount",
            "debit amount", "credit amount", "dr", "cr", "transaction_amt",
            "withdrawal", "deposit", "txn amount"
    );

    private static final List<String> DESC_COLUMNS = Arrays.asList(
            "description", "narration", "particulars", "remarks", "details", "desc",
            "transaction details", "transaction_detail", "narration details",
            "particular", "payment details", "transaction_particulars"
    );

    private static final List<String> REF_COLUMNS = Arrays.asList(
            "reference", "ref no", "transaction id", "cheque no", "ref", "reference no",
            "cheque number", "transaction ref", "transaction_ref", "refno",
            "transaction reference", "bank ref", "bank_ref", "transaction id"
    );

    private static final List<String> STATUS_COLUMNS = Arrays.asList(
            "status", "transaction status", "txn status", "payment status"
    );

    // ========== DATE FORMATS ==========
    private static final List<DateTimeFormatter> DATE_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy")
    );

    // ========== PUBLIC PARSING METHODS ==========

    public List<BankTransaction> parseCsv(MultipartFile file, String bankAccount) throws IOException {
        log.info("=== CSV PARSER START ===");
        log.info("File: {} ({} bytes, type: {})",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        List<BankTransaction> transactions = new ArrayList<>();
        String importBatchId = UUID.randomUUID().toString();

        // Check if file is empty
        if (file.isEmpty() || file.getSize() == 0) {
            log.error("❌ File is empty!");
            return transactions;
        }

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()    // Make headers case-insensitive
                     .withTrim()                // Trim whitespace
                     .withIgnoreEmptyLines()    // Skip empty lines
                     .withAllowMissingColumnNames())) {  // Allow missing columns

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            log.info("✅ CSV Headers detected: {}", headerMap.keySet());

            if (headerMap.isEmpty()) {
                log.warn("⚠️ No headers found. Trying without headers...");
                return parseCsvWithoutHeaders(file, bankAccount, importBatchId);
            }

            int recordCount = 0;
            int successCount = 0;

            for (CSVRecord record : csvParser) {
                recordCount++;
                try {
                    log.debug("Parsing record {}: {}", recordCount, record);

                    BankTransaction transaction = parseRecord(record, headerMap, bankAccount, importBatchId);
                    if (transaction != null) {
                        transactions.add(transaction);
                        successCount++;
                        log.debug("✅ Added transaction: Ref={}, Amount={}, Desc={}",
                                transaction.getBankReference(),
                                transaction.getAmount(),
                                transaction.getDescription());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to parse record {}: {}", recordCount, e.getMessage());
                    // Continue with next record
                }
            }

            log.info("📊 CSV Parsing complete: {} records processed, {} transactions created",
                    recordCount, successCount);

        } catch (Exception e) {
            log.error("❌ Failed to parse CSV file", e);
            // Try alternative parsing method
            return parseCsvWithAlternativeMethod(file, bankAccount, importBatchId);
        }

        log.info("✅ CSV Parser returning {} transactions", transactions.size());
        return transactions;
    }

    public List<BankTransaction> parseExcel(MultipartFile file, String bankAccount) throws IOException {
        log.info("=== EXCEL PARSER START ===");
        log.info("File: {} ({} bytes, type: {})",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        List<BankTransaction> transactions = new ArrayList<>();
        String importBatchId = UUID.randomUUID().toString();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                log.warn("⚠️ Excel sheet is empty");
                return transactions;
            }

            // Read headers from first row
            Row headerRow = rowIterator.next();
            Map<String, Integer> headerMap = new HashMap<>();

            for (Cell cell : headerRow) {
                String headerName = getCellValue(cell).toLowerCase().trim();
                if (!headerName.isEmpty()) {
                    headerMap.put(headerName, cell.getColumnIndex());
                }
            }

            log.info("✅ Excel Headers detected: {}", headerMap.keySet());

            if (headerMap.isEmpty()) {
                log.warn("⚠️ No headers found in Excel. Using column positions...");
                return parseExcelWithoutHeaders(sheet, bankAccount, importBatchId);
            }

            int rowCount = 0;
            int successCount = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowCount++;

                try {
                    BankTransaction transaction = parseExcelRow(row, headerMap, bankAccount, importBatchId);
                    if (transaction != null) {
                        transactions.add(transaction);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Failed to parse Excel row {}: {}", rowCount, e.getMessage());
                }
            }

            log.info("📊 Excel Parsing complete: {} rows processed, {} transactions created",
                    rowCount, successCount);

        } catch (Exception e) {
            log.error("❌ Failed to parse Excel file", e);
            throw new IOException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        log.info("✅ Excel Parser returning {} transactions", transactions.size());
        return transactions;
    }

    // ========== PRIVATE PARSING METHODS ==========

    private BankTransaction parseRecord(CSVRecord record, Map<String, Integer> headerMap,
                                        String bankAccount, String importBatchId) {
        // Debug: Show what we're trying to parse
        if (log.isDebugEnabled()) {
            log.debug("Record size: {}, Values: {}", record.size(), record.toString());
        }

        String dateStr = getValueFromRecord(record, headerMap, DATE_COLUMNS);
        String amountStr = getValueFromRecord(record, headerMap, AMOUNT_COLUMNS);
        String description = getValueFromRecord(record, headerMap, DESC_COLUMNS);
        String reference = getValueFromRecord(record, headerMap, REF_COLUMNS);
        String statusStr = getValueFromRecord(record, headerMap, STATUS_COLUMNS);

        // Log what we found for debugging
        log.debug("Parsed values - Date: [{}], Amount: [{}], Desc: [{}], Ref: [{}], Status: [{}]",
                dateStr, amountStr, description, reference, statusStr);

        // Validate required fields
        if (dateStr == null || dateStr.trim().isEmpty()) {
            log.warn("⚠️ Skipping record - Missing date");
            return null;
        }

        if (amountStr == null || amountStr.trim().isEmpty()) {
            log.warn("⚠️ Skipping record - Missing amount");
            return null;
        }

        if (description == null || description.trim().isEmpty()) {
            log.warn("⚠️ Skipping record - Missing description");
            return null;
        }

        try {
            LocalDate date = parseDate(dateStr);
            Double amount = parseAmount(amountStr);

            if (amount == null || amount == 0.0) {
                log.warn("⚠️ Skipping record - Invalid amount: {}", amountStr);
                return null;
            }

            PaymentMethod paymentMethod = detectPaymentMethod(description);
            TransactionStatus status = parseTransactionStatus(statusStr);

            // Generate reference if not provided
            if (reference == null || reference.trim().isEmpty()) {
                reference = "IMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            // Clean and truncate description if too long
            description = description.trim();
            if (description.length() > 500) {
                description = description.substring(0, 497) + "...";
            }

            BankTransaction transaction = BankTransaction.builder()
                    .bankReference(reference)
                    .transactionDate(date)
                    .description(description)
                    .amount(amount)
                    .bankAccount(bankAccount != null ? bankAccount : "DEFAULT_ACCOUNT")
                    .status(status)
                    .paymentMethod(paymentMethod)
                    .fileName("CSV Import")
                    .importBatchId(importBatchId)
                    .smsSent(false)
                    .build();

            log.debug("✅ Created transaction: Ref={}, Date={}, Amount={}, Desc={}",
                    transaction.getBankReference(), transaction.getTransactionDate(),
                    transaction.getAmount(), transaction.getDescription());

            return transaction;

        } catch (Exception e) {
            log.error("❌ Error parsing transaction values. Date: {}, Amount: {}, Desc: {}",
                    dateStr, amountStr, description, e);
            return null;
        }
    }

    private BankTransaction parseExcelRow(Row row, Map<String, Integer> headerMap,
                                          String bankAccount, String importBatchId) {
        String dateStr = getValueFromRow(row, headerMap, DATE_COLUMNS);
        String amountStr = getValueFromRow(row, headerMap, AMOUNT_COLUMNS);
        String description = getValueFromRow(row, headerMap, DESC_COLUMNS);
        String reference = getValueFromRow(row, headerMap, REF_COLUMNS);
        String statusStr = getValueFromRow(row, headerMap, STATUS_COLUMNS);

        // Validate required fields
        if (dateStr == null || dateStr.trim().isEmpty() ||
                amountStr == null || amountStr.trim().isEmpty() ||
                description == null || description.trim().isEmpty()) {
            return null;
        }

        try {
            LocalDate date = parseDate(dateStr);
            Double amount = parseAmount(amountStr);

            if (amount == null || amount == 0.0) {
                return null;
            }

            PaymentMethod paymentMethod = detectPaymentMethod(description);
            TransactionStatus status = parseTransactionStatus(statusStr);

            // Generate reference if not provided
            if (reference == null || reference.trim().isEmpty()) {
                reference = "IMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            // Clean description
            description = description.trim();
            if (description.length() > 500) {
                description = description.substring(0, 497) + "...";
            }

            return BankTransaction.builder()
                    .bankReference(reference)
                    .transactionDate(date)
                    .description(description)
                    .amount(amount)
                    .bankAccount(bankAccount != null ? bankAccount : "DEFAULT_ACCOUNT")
                    .status(status)
                    .paymentMethod(paymentMethod)
                    .fileName("Excel Import")
                    .importBatchId(importBatchId)
                    .smsSent(false)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing Excel row values", e);
            return null;
        }
    }

    // ========== FALLBACK PARSING METHODS ==========

    private List<BankTransaction> parseCsvWithoutHeaders(MultipartFile file, String bankAccount, String importBatchId) throws IOException {
        log.info("🔄 Trying CSV parsing without headers...");

        List<BankTransaction> transactions = new ArrayList<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withTrim()
                     .withIgnoreEmptyLines())) {

            int recordCount = 0;
            for (CSVRecord record : csvParser) {
                recordCount++;

                // Skip empty records
                if (record.size() < 3) {
                    continue;
                }

                try {
                    // Assume columns: 0=Date, 1=Description, 2=Amount, 3=Reference (optional)
                    String dateStr = record.get(0);
                    String description = record.get(1);
                    String amountStr = record.get(2);
                    String reference = record.size() > 3 ? record.get(3) : null;

                    LocalDate date = parseDate(dateStr);
                    Double amount = parseAmount(amountStr);

                    if (date != null && amount != null && amount > 0 && description != null) {
                        BankTransaction transaction = BankTransaction.builder()
                                .bankReference(reference != null ? reference :
                                        "NO-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                                .transactionDate(date)
                                .description(description.trim())
                                .amount(amount)
                                .bankAccount(bankAccount != null ? bankAccount : "DEFAULT_ACCOUNT")
                                .status(TransactionStatus.UNVERIFIED)
                                .paymentMethod(detectPaymentMethod(description))
                                .fileName("CSV Import (No Headers)")
                                .importBatchId(importBatchId)
                                .smsSent(false)
                                .build();

                        transactions.add(transaction);
                        log.debug("✅ Added transaction without headers: {}", transaction.getDescription());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse headerless record {}: {}", recordCount, e.getMessage());
                }
            }

            log.info("📊 Headerless parsing: {} transactions created", transactions.size());

        } catch (Exception e) {
            log.error("Failed headerless CSV parsing", e);
        }

        return transactions;
    }

    private List<BankTransaction> parseExcelWithoutHeaders(Sheet sheet, String bankAccount, String importBatchId) {
        log.info("🔄 Trying Excel parsing without headers...");

        List<BankTransaction> transactions = new ArrayList<>();
        Iterator<Row> rowIterator = sheet.iterator();

        // Skip first row if it might be headers
        if (rowIterator.hasNext()) {
            rowIterator.next();
        }

        int rowCount = 0;
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();
            rowCount++;

            try {
                // Assume columns: 0=Date, 1=Description, 2=Amount, 3=Reference (optional)
                String dateStr = getCellValue(row.getCell(0));
                String description = getCellValue(row.getCell(1));
                String amountStr = getCellValue(row.getCell(2));
                String reference = row.getCell(3) != null ? getCellValue(row.getCell(3)) : null;

                if ((dateStr == null || dateStr.trim().isEmpty()) ||
                        (description == null || description.trim().isEmpty()) ||
                        (amountStr == null || amountStr.trim().isEmpty())) {
                    continue;
                }

                LocalDate date = parseDate(dateStr);
                Double amount = parseAmount(amountStr);

                if (date != null && amount != null && amount > 0) {
                    BankTransaction transaction = BankTransaction.builder()
                            .bankReference(reference != null ? reference :
                                    "NO-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .transactionDate(date)
                            .description(description.trim())
                            .amount(amount)
                            .bankAccount(bankAccount != null ? bankAccount : "DEFAULT_ACCOUNT")
                            .status(TransactionStatus.UNVERIFIED)
                            .paymentMethod(detectPaymentMethod(description))
                            .fileName("Excel Import (No Headers)")
                            .importBatchId(importBatchId)
                            .smsSent(false)
                            .build();

                    transactions.add(transaction);
                }
            } catch (Exception e) {
                log.warn("Failed to parse headerless Excel row {}: {}", rowCount, e.getMessage());
            }
        }

        log.info("📊 Headerless Excel parsing: {} transactions created", transactions.size());
        return transactions;
    }

    private List<BankTransaction> parseCsvWithAlternativeMethod(MultipartFile file, String bankAccount, String importBatchId) {
        log.info("🔄 Trying alternative CSV parsing method...");

        List<BankTransaction> transactions = new ArrayList<>();

        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String[] lines = content.split("\n");

            log.info("File has {} lines", lines.length);

            // Try to detect headers from first line
            String firstLine = lines.length > 0 ? lines[0].trim() : "";
            String[] firstLineParts = firstLine.split(",");

            log.info("First line parts: {}", Arrays.toString(firstLineParts));

            // Skip header line if it looks like headers
            int startLine = firstLineParts.length > 0 &&
                    (firstLine.toLowerCase().contains("date") ||
                            firstLine.toLowerCase().contains("amount") ||
                            firstLine.toLowerCase().contains("desc")) ? 1 : 0;

            for (int i = startLine; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                try {
                    // Try comma separator first, then semicolon
                    String[] parts = line.contains(";") ? line.split(";") : line.split(",");

                    if (parts.length >= 3) {
                        String dateStr = parts[0].trim().replace("\"", "");
                        String description = parts.length > 1 ? parts[1].trim().replace("\"", "") : "";
                        String amountStr = parts.length > 2 ? parts[2].trim().replace("\"", "") : "";
                        String reference = parts.length > 3 ? parts[3].trim().replace("\"", "") : null;

                        LocalDate date = parseDate(dateStr);
                        Double amount = parseAmount(amountStr);

                        if (date != null && amount != null && amount > 0 && !description.isEmpty()) {
                            BankTransaction transaction = BankTransaction.builder()
                                    .bankReference(reference != null ? reference :
                                            "ALT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                                    .transactionDate(date)
                                    .description(description)
                                    .amount(amount)
                                    .bankAccount(bankAccount != null ? bankAccount : "DEFAULT_ACCOUNT")
                                    .status(TransactionStatus.UNVERIFIED)
                                    .paymentMethod(detectPaymentMethod(description))
                                    .fileName("CSV Import (Alternative)")
                                    .importBatchId(importBatchId)
                                    .smsSent(false)
                                    .build();

                            transactions.add(transaction);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse line {}: {}", i + 1, e.getMessage());
                }
            }

            log.info("📊 Alternative parsing: {} transactions created", transactions.size());

        } catch (Exception e) {
            log.error("Alternative parsing failed", e);
        }

        return transactions;
    }

    // ========== HELPER METHODS ==========

    private String getValueFromRecord(CSVRecord record, Map<String, Integer> headerMap, List<String> possibleKeys) {
        for (String key : possibleKeys) {
            if (headerMap.containsKey(key)) {
                try {
                    String value = record.get(key);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                } catch (IllegalArgumentException e) {
                    // Column not found, try next key
                }
            }
        }
        return null;
    }

    private String getValueFromRow(Row row, Map<String, Integer> headerMap, List<String> possibleKeys) {
        for (String key : possibleKeys) {
            if (headerMap.containsKey(key)) {
                Cell cell = row.getCell(headerMap.get(key));
                if (cell != null) {
                    String value = getCellValue(cell);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        }
        return null;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        return new SimpleDateFormat("dd/MM/yyyy").format(cell.getDateCellValue());
                    } catch (Exception e) {
                        return String.valueOf(cell.getNumericCellValue());
                    }
                } else {
                    // Remove .0 from whole numbers
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num)) {
                        return String.valueOf((long) num);
                    } else {
                        return String.valueOf(num);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDate.now();
        }

        dateStr = dateStr.trim();

        // Try all known date formats
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }

        // Try Excel serial date format (e.g., 44927 = 2023-01-01)
        try {
            double excelDate = Double.parseDouble(dateStr);
            // Excel dates start from 1899-12-30 (or 1900-01-01 with bug)
            return LocalDate.of(1899, 12, 30).plusDays((long) excelDate);
        } catch (NumberFormatException e) {
            // Not an Excel date
        }

        // Last resort: try to extract date with regex
        try {
            // Look for patterns like DD/MM/YYYY or DD-MM-YYYY
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})");
            java.util.regex.Matcher matcher = pattern.matcher(dateStr);
            if (matcher.find()) {
                int day = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                // Handle 2-digit years
                if (year < 100) {
                    year += 2000;
                }
                return LocalDate.of(year, month, day);
            }
        } catch (Exception e) {
            log.warn("Regex date parsing failed for: {}", dateStr);
        }

        log.warn("⚠️ Could not parse date: {}, using current date", dateStr);
        return LocalDate.now();
    }

    private Double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return 0.0;
        }

        try {
            // Remove all non-numeric characters except decimal point and minus sign
            String cleaned = amountStr.replaceAll("[^\\d.-]", "").trim();

            // Handle empty result
            if (cleaned.isEmpty()) {
                return 0.0;
            }

            // Parse as double
            return Double.parseDouble(cleaned);

        } catch (NumberFormatException e) {
            log.warn("⚠️ Failed to parse amount: {}, using 0.0", amountStr);
            return 0.0;
        }
    }

    private PaymentMethod detectPaymentMethod(String description) {
        if (description == null) {
            return PaymentMethod.BANK_TRANSFER;
        }

        String desc = description.toLowerCase();

        if (desc.contains("upi") || desc.contains("qr code") || desc.contains("qrcode")) {
            return PaymentMethod.UPI;
        } else if (desc.contains("neft")) {
            return PaymentMethod.NEFT;
        } else if (desc.contains("rtgs")) {
            return PaymentMethod.RTGS;
        } else if (desc.contains("imps")) {
            return PaymentMethod.IMPS;
        } else if (desc.contains("credit card") || desc.contains("creditcard")) {
            return PaymentMethod.CREDIT_CARD;
        } else if (desc.contains("debit card") || desc.contains("debitcard")) {
            return PaymentMethod.DEBIT_CARD;
        } else if (desc.contains("cash")) {
            return PaymentMethod.CASH;
        } else if (desc.contains("cheque") || desc.contains("chq") || desc.contains("check")) {
            return PaymentMethod.CHEQUE;
        } else if (desc.contains("online") || desc.contains("internet banking") || desc.contains("netbanking")) {
            return PaymentMethod.ONLINE_BANKING;
        } else if (desc.contains("card")) {
            return PaymentMethod.CREDIT_CARD; // Default to credit card if just "card"
        } else {
            return PaymentMethod.BANK_TRANSFER;
        }
    }

    private TransactionStatus parseTransactionStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return TransactionStatus.UNVERIFIED;
        }

        String status = statusStr.trim().toUpperCase();

        switch (status) {
            case "VERIFIED":
            case "COMPLETED":
            case "SUCCESS":
            case "SUCCESSFUL":
            case "PAID":
            case "SETTLED":
                return TransactionStatus.VERIFIED;
            case "MATCHED":
            case "PROCESSED":
            case "RECONCILED":
                return TransactionStatus.MATCHED;
            case "CANCELLED":
            case "FAILED":
            case "REJECTED":
            case "DECLINED":
                return TransactionStatus.CANCELLED;
            case "PENDING":
            case "IN PROCESS":
            case "PROCESSING":
                return TransactionStatus.PENDING;
            case "UNVERIFIED":
            case "NEW":
            case "UNMATCHED":
            default:
                return TransactionStatus.UNVERIFIED;
        }
    }
}