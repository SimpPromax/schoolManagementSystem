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

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class BankStatementParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> DATE_COLUMNS = Arrays.asList("date", "transaction date", "txn date");
    private static final List<String> AMOUNT_COLUMNS = Arrays.asList("amount", "transaction amount", "debit", "credit");
    private static final List<String> DESC_COLUMNS = Arrays.asList("description", "narration", "particulars", "remarks");
    private static final List<String> REF_COLUMNS = Arrays.asList("reference", "ref no", "transaction id", "cheque no");
    private static final List<String> STATUS_COLUMNS = Arrays.asList("status", "transaction status");

    public static List<BankTransaction> parseCsv(MultipartFile file, String bankAccount) throws IOException {
        List<BankTransaction> transactions = new ArrayList<>();
        String importBatchId = UUID.randomUUID().toString();

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            log.info("CSV Headers: {}", headerMap);

            for (CSVRecord record : csvParser) {
                try {
                    BankTransaction transaction = parseRecord(record, headerMap, bankAccount, importBatchId);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    log.error("Error parsing CSV record: {}", record, e);
                }
            }
        }

        return transactions;
    }

    public static List<BankTransaction> parseExcel(MultipartFile file, String bankAccount) throws IOException {
        List<BankTransaction> transactions = new ArrayList<>();
        String importBatchId = UUID.randomUUID().toString();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();

            if (!rowIterator.hasNext()) {
                return transactions;
            }

            // Read headers from first row
            Row headerRow = rowIterator.next();
            Map<String, Integer> headerMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String headerName = cell.getStringCellValue().toLowerCase().trim();
                headerMap.put(headerName, cell.getColumnIndex());
            }

            log.info("Excel Headers: {}", headerMap);

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                try {
                    BankTransaction transaction = parseExcelRow(row, headerMap, bankAccount, importBatchId);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                } catch (Exception e) {
                    log.error("Error parsing Excel row: {}", row.getRowNum(), e);
                }
            }
        }

        return transactions;
    }

    private static BankTransaction parseRecord(CSVRecord record, Map<String, Integer> headerMap,
                                               String bankAccount, String importBatchId) {
        String dateStr = getValueFromRecord(record, headerMap, DATE_COLUMNS);
        String amountStr = getValueFromRecord(record, headerMap, AMOUNT_COLUMNS);
        String description = getValueFromRecord(record, headerMap, DESC_COLUMNS);
        String reference = getValueFromRecord(record, headerMap, REF_COLUMNS);
        String statusStr = getValueFromRecord(record, headerMap, STATUS_COLUMNS);

        if (dateStr == null || amountStr == null || description == null) {
            return null;
        }

        try {
            LocalDate date = parseDate(dateStr);
            Double amount = parseAmount(amountStr);
            PaymentMethod paymentMethod = detectPaymentMethod(description);

            // Parse status if provided in CSV, otherwise default to UNVERIFIED
            TransactionStatus status = parseTransactionStatus(statusStr);

            log.debug("Creating transaction with status: {}", status);

            return BankTransaction.builder()
                    .bankReference(reference != null ? reference : generateReference())
                    .transactionDate(date)
                    .description(description.trim())
                    .amount(amount)
                    .bankAccount(bankAccount)
                    .status(status) // Explicitly set status
                    .paymentMethod(paymentMethod)
                    .fileName("CSV Import")
                    .importBatchId(importBatchId)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing values: date={}, amount={}, desc={}", dateStr, amountStr, description, e);
            return null;
        }
    }

    private static BankTransaction parseExcelRow(Row row, Map<String, Integer> headerMap,
                                                 String bankAccount, String importBatchId) {
        String dateStr = getValueFromRow(row, headerMap, DATE_COLUMNS);
        String amountStr = getValueFromRow(row, headerMap, AMOUNT_COLUMNS);
        String description = getValueFromRow(row, headerMap, DESC_COLUMNS);
        String reference = getValueFromRow(row, headerMap, REF_COLUMNS);
        String statusStr = getValueFromRow(row, headerMap, STATUS_COLUMNS);

        if (dateStr == null || amountStr == null || description == null) {
            return null;
        }

        try {
            LocalDate date = parseDate(dateStr);
            Double amount = parseAmount(amountStr);
            PaymentMethod paymentMethod = detectPaymentMethod(description);

            // Parse status if provided in Excel, otherwise default to UNVERIFIED
            TransactionStatus status = parseTransactionStatus(statusStr);

            log.debug("Creating transaction with status: {}", status);

            return BankTransaction.builder()
                    .bankReference(reference != null ? reference : generateReference())
                    .transactionDate(date)
                    .description(description.trim())
                    .amount(amount)
                    .bankAccount(bankAccount)
                    .status(status) // Explicitly set status
                    .paymentMethod(paymentMethod)
                    .fileName("Excel Import")
                    .importBatchId(importBatchId)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing Excel row values", e);
            return null;
        }
    }

    private static String getValueFromRecord(CSVRecord record, Map<String, Integer> headerMap, List<String> possibleKeys) {
        for (String key : possibleKeys) {
            if (headerMap.containsKey(key)) {
                String value = record.get(key);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private static String getValueFromRow(Row row, Map<String, Integer> headerMap, List<String> possibleKeys) {
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

    private static String getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("dd/MM/yyyy").format(cell.getDateCellValue());
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private static LocalDate parseDate(String dateStr) {
        try {
            // Try common date formats
            String[] formats = {"dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "MM/dd/yyyy"};
            for (String format : formats) {
                try {
                    return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(format));
                } catch (Exception e) {
                    // Try next format
                }
            }

            // If all fail, try parsing as Excel date number
            try {
                double excelDate = Double.parseDouble(dateStr);
                return LocalDate.of(1900, 1, 1).plusDays((long) excelDate - 2);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid date format: " + dateStr);
            }
        } catch (Exception e) {
            log.error("Failed to parse date: {}", dateStr, e);
            return LocalDate.now();
        }
    }

    private static Double parseAmount(String amountStr) {
        try {
            // Remove currency symbols and commas
            String cleaned = amountStr.replaceAll("[₹$,]", "").trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            log.error("Failed to parse amount: {}", amountStr, e);
            return 0.0;
        }
    }

    private static PaymentMethod detectPaymentMethod(String description) {
        String desc = description.toLowerCase();
        if (desc.contains("upi")) return PaymentMethod.UPI;
        if (desc.contains("neft")) return PaymentMethod.NEFT;
        if (desc.contains("rtgs")) return PaymentMethod.RTGS;
        if (desc.contains("imps")) return PaymentMethod.IMPS;
        if (desc.contains("card")) return PaymentMethod.CREDIT_CARD;
        if (desc.contains("cash")) return PaymentMethod.CASH;
        if (desc.contains("cheque") || desc.contains("chq")) return PaymentMethod.CHEQUE;
        if (desc.contains("online")) return PaymentMethod.ONLINE_BANKING;
        return PaymentMethod.BANK_TRANSFER;
    }

    private static TransactionStatus parseTransactionStatus(String statusStr) {
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return TransactionStatus.UNVERIFIED; // Default status
        }

        try {
            String upperStatus = statusStr.trim().toUpperCase();
            // Handle various status string formats
            switch (upperStatus) {
                case "UNVERIFIED":
                case "PENDING":
                case "NEW":
                    return TransactionStatus.UNVERIFIED;
                case "VERIFIED":
                case "COMPLETED":
                case "PAID":
                    return TransactionStatus.VERIFIED;
                case "MATCHED":
                case "PROCESSED":
                    return TransactionStatus.MATCHED;
                case "UNMATCHED":
                    return TransactionStatus.UNMATCHED;
                case "CANCELLED":
                case "FAILED":
                case "REJECTED":
                    return TransactionStatus.CANCELLED;
                default:
                    log.warn("Unknown status value '{}', defaulting to UNVERIFIED", statusStr);
                    return TransactionStatus.UNVERIFIED;
            }
        } catch (Exception e) {
            log.error("Error parsing transaction status: {}", statusStr, e);
            return TransactionStatus.UNVERIFIED;
        }
    }

    private static String generateReference() {
        return "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}