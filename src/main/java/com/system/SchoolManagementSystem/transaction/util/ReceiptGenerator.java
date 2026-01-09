package com.system.SchoolManagementSystem.transaction.util;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class ReceiptGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a");

    // Color definitions
    private static final Color LIGHT_GRAY = new Color(240, 240, 240);
    private static final Color LIGHT_YELLOW = new Color(255, 255, 200);
    private static final Color BLUE = new Color(0, 102, 204);
    private static final Color GREEN = new Color(34, 139, 34);
    private static final Color DARK_BLUE = new Color(0, 0, 139);
    private static final Color DARK_GRAY = new Color(64, 64, 64);
    private static final Color GRAY = new Color(128, 128, 128);
    private static final Color RED = new Color(255, 0, 0);
    private static final Color DISCOUNT_GREEN = new Color(0, 128, 0);

    public byte[] generateReceiptPdf(PaymentTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Student student = transaction.getStudent();
            if (student == null) {
                throw new IllegalArgumentException("Student information is missing in transaction");
            }

            // School Header
            Font schoolTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            schoolTitleFont.setColor(BLUE);
            addCenteredParagraph(document, "SCHOOL MANAGEMENT SYSTEM", schoolTitleFont);

            Font addressFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            addressFont.setColor(DARK_GRAY);
            addCenteredParagraph(document, "Fee Payment Receipt", addressFont);

            document.add(new Paragraph(" ")); // Empty line

            // Title
            Font receiptTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            receiptTitleFont.setColor(GREEN);
            addCenteredParagraph(document, "OFFICIAL FEE PAYMENT RECEIPT", receiptTitleFont);

            addCenteredParagraph(document, "____________________________", FontFactory.getFont(FontFactory.HELVETICA, 10));

            document.add(new Paragraph(" ")); // Empty line

            // Receipt Details Table
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setSpacingBefore(10f);
            detailsTable.setSpacingAfter(10f);

            float[] columnWidths = {30f, 70f};
            detailsTable.setWidths(columnWidths);

            addTableHeaderCell(detailsTable, "RECEIPT DETAILS", 2, LIGHT_GRAY);
            addTableRow(detailsTable, "Receipt Number:", transaction.getReceiptNumber());
            addTableRow(detailsTable, "Receipt Date:", DATE_FORMATTER.format(transaction.getPaymentDate()));
            addTableRow(detailsTable, "Student Name:", getStudentFullName(student));
            addTableRow(detailsTable, "Grade/Class:", getStudentGrade(student));
            addTableRow(detailsTable, "Student ID:", getStudentId(student));
            addTableRow(detailsTable, "Roll Number:", getStudentRollNumber(student));

            document.add(detailsTable);

            // Payment Details Table
            PdfPTable paymentTable = new PdfPTable(2);
            paymentTable.setWidthPercentage(100);
            paymentTable.setSpacingBefore(10f);
            paymentTable.setSpacingAfter(10f);
            paymentTable.setWidths(columnWidths);

            addTableHeaderCell(paymentTable, "PAYMENT DETAILS", 2, LIGHT_GRAY);

            // Amount row
            addTableRow(paymentTable, "Payment Amount:", formatCurrency(transaction.getAmount()));

            // Discount if applied
            Double discountApplied = transaction.getDiscountApplied();
            if (discountApplied != null && discountApplied > 0) {
                Font discountFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
                discountFont.setColor(DISCOUNT_GREEN);
                addTableRowWithCustomFont(paymentTable, "Discount Applied:",
                        "- " + formatCurrency(discountApplied), discountFont);
            }

            // Late fee if applied
            Double lateFeePaid = transaction.getLateFeePaid();
            if (lateFeePaid != null && lateFeePaid > 0) {
                Font lateFeeFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
                lateFeeFont.setColor(RED);
                addTableRowWithCustomFont(paymentTable, "Late Fee Paid:",
                        "+ " + formatCurrency(lateFeePaid), lateFeeFont);
            }

            // Convenience fee if applied
            Double convenienceFee = transaction.getConvenienceFee();
            if (convenienceFee != null && convenienceFee > 0) {
                addTableRow(paymentTable, "Convenience Fee:", "+ " + formatCurrency(convenienceFee));
            }

            // Add empty row before total
            addEmptyTableRow(paymentTable, 2);

            // Total row with special styling
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            totalFont.setColor(DARK_BLUE);

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL PAID AMOUNT:", totalFont));
            totalLabelCell.setBorder(0);
            totalLabelCell.setPadding(8);
            totalLabelCell.setBackgroundColor(LIGHT_YELLOW);
            paymentTable.addCell(totalLabelCell);

            Double totalPaid = calculateTotalPaid(transaction);
            PdfPCell totalValueCell = new PdfPCell(new Phrase(formatCurrency(totalPaid), totalFont));
            totalValueCell.setBorder(0);
            totalValueCell.setPadding(8);
            totalValueCell.setBackgroundColor(LIGHT_YELLOW);
            paymentTable.addCell(totalValueCell);

            // Payment method
            String paymentMethod = transaction.getPaymentMethod() != null ?
                    formatPaymentMethod(transaction.getPaymentMethod()) : "Not specified";
            addTableRow(paymentTable, "Payment Method:", paymentMethod);

            // Payment for
            String paymentFor = transaction.getPaymentFor();
            if (paymentFor != null && !paymentFor.trim().isEmpty()) {
                addTableRow(paymentTable, "Payment For:", paymentFor);
            }

            // Bank reference if available
            String bankReference = transaction.getBankReference();
            if (bankReference != null && !bankReference.trim().isEmpty()) {
                addTableRow(paymentTable, "Bank Reference:", bankReference);
            }

            // Notes if available
            String notes = transaction.getNotes();
            if (notes != null && !notes.trim().isEmpty()) {
                addTableRow(paymentTable, "Notes:", notes);
            }

            document.add(paymentTable);

            // SMS Status
            Boolean smsSent = transaction.getSmsSent();
            if (smsSent != null && smsSent) {
                Font smsFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);
                smsFont.setColor(DISCOUNT_GREEN);
                Paragraph smsPara = new Paragraph("✓ SMS notification sent to parent/guardian", smsFont);
                smsPara.setAlignment(Element.ALIGN_LEFT);
                document.add(smsPara);
            }

            document.add(new Paragraph(" ")); // Empty line

            // Footer
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            footerFont.setColor(GRAY);
            addCenteredParagraph(document, "This is a computer-generated receipt.", footerFont);
            addCenteredParagraph(document, "Valid without signature", footerFont);

            document.add(new Paragraph(" ")); // Empty line

            Font thankYouFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            thankYouFont.setColor(GREEN);
            addCenteredParagraph(document, "Thank you for your payment!", thankYouFont);

            log.info("Generated receipt PDF for transaction: {}", transaction.getReceiptNumber());

        } catch (DocumentException e) {
            log.error("Document error while generating receipt for transaction: {}",
                    transaction.getReceiptNumber(), e);
            throw new RuntimeException("Failed to generate receipt", e);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    public byte[] generateSimpleReceipt(PaymentTransaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Student student = transaction.getStudent();

            // Simple receipt
            addCenteredParagraph(document, "PAYMENT RECEIPT",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);

            addTableRow(table, "Receipt:", transaction.getReceiptNumber());
            addTableRow(table, "Date:", transaction.getPaymentDate().toLocalDate().toString());
            addTableRow(table, "Student:", getStudentFullName(student));
            addTableRow(table, "Class:", getStudentGrade(student));
            addTableRow(table, "Amount:", formatCurrency(transaction.getAmount()));

            String paymentMethod = transaction.getPaymentMethod() != null ?
                    transaction.getPaymentMethod().toString() : "Not specified";
            addTableRow(table, "Method:", paymentMethod);

            document.add(table);

        } catch (DocumentException e) {
            log.error("Error generating simple receipt", e);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    // Helper methods for student data (with null safety)
    private String getStudentFullName(Student student) {
        if (student == null) return "N/A";

        try {
            // Try the getter method first
            String fullName = student.getFullName();
            if (fullName != null && !fullName.trim().isEmpty()) {
                return fullName.trim();
            }
        } catch (Exception e) {
            // If getter fails, use direct field access via reflection
            log.debug("getFullName() getter not available, using reflection");
            try {
                Field fullNameField = Student.class.getDeclaredField("fullName");
                fullNameField.setAccessible(true);
                String fullName = (String) fullNameField.get(student);
                return fullName != null && !fullName.trim().isEmpty() ? fullName.trim() : "N/A";
            } catch (Exception ex) {
                log.warn("Could not access fullName field for student", ex);
            }
        }

        return "Student Name Not Available";
    }

    private String getStudentGrade(Student student) {
        if (student == null) return "N/A";

        try {
            String grade = student.getGrade();
            return grade != null && !grade.trim().isEmpty() ? grade.trim() : "N/A";
        } catch (Exception e) {
            log.debug("getGrade() method not available");
            return "N/A";
        }
    }

    private String getStudentId(Student student) {
        if (student == null) return "N/A";

        try {
            String studentId = student.getStudentId();
            return studentId != null && !studentId.trim().isEmpty() ? studentId.trim() : "N/A";
        } catch (Exception e) {
            log.debug("getStudentId() method not available");
            return "N/A";
        }
    }

    private String getStudentRollNumber(Student student) {
        if (student == null) return "N/A";

        try {
            String rollNumber = student.getRollNumber();
            return rollNumber != null && !rollNumber.trim().isEmpty() ? rollNumber.trim() : "N/A";
        } catch (Exception e) {
            log.debug("getRollNumber() method not available");
            return "N/A";
        }
    }

    private Double calculateTotalPaid(PaymentTransaction transaction) {
        if (transaction.getTotalPaid() != null) {
            return transaction.getTotalPaid();
        }

        double amount = transaction.getAmount() != null ? transaction.getAmount() : 0.0;
        double discount = transaction.getDiscountApplied() != null ? transaction.getDiscountApplied() : 0.0;
        double lateFee = transaction.getLateFeePaid() != null ? transaction.getLateFeePaid() : 0.0;
        double convenience = transaction.getConvenienceFee() != null ? transaction.getConvenienceFee() : 0.0;

        return amount - discount + lateFee + convenience;
    }

    // Helper methods for PDF creation
    private void addCenteredParagraph(Document document, String text, Font font) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        document.add(paragraph);
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont));
        labelCell.setBorder(0);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", normalFont));
        valueCell.setBorder(0);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(valueCell);
    }

    private void addTableRowWithCustomFont(PdfPTable table, String label, String value, Font valueFont) {
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont));
        labelCell.setBorder(0);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(0);
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(valueCell);
    }

    private void addTableHeaderCell(PdfPTable table, String text, int colspan, Color backgroundColor) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        headerFont.setColor(Color.BLACK);

        PdfPCell headerCell = new PdfPCell(new Phrase(text, headerFont));
        headerCell.setColspan(colspan);
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setPadding(8);
        headerCell.setBackgroundColor(backgroundColor);
        headerCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(headerCell);
    }

    private void addEmptyTableRow(PdfPTable table, int colspan) {
        PdfPCell emptyCell = new PdfPCell(new Phrase(" "));
        emptyCell.setColspan(colspan);
        emptyCell.setBorder(0);
        emptyCell.setFixedHeight(5f);
        table.addCell(emptyCell);
    }

    private String formatCurrency(Double amount) {
        if (amount == null) return "₹ 0.00";
        return String.format("₹ %,.2f", amount);
    }

    private String formatPaymentMethod(com.system.SchoolManagementSystem.transaction.enums.PaymentMethod method) {
        if (method == null) return "Not specified";

        switch (method) {
            case UPI: return "UPI Payment";
            case CASH: return "Cash Payment";
            case CHEQUE: return "Cheque Payment";
            case CREDIT_CARD: return "Credit Card";
            case DEBIT_CARD: return "Debit Card";
            case BANK_TRANSFER: return "Bank Transfer";
            case NEFT: return "NEFT";
            case RTGS: return "RTGS";
            case IMPS: return "IMPS";
            case ONLINE_BANKING: return "Online Banking";
            default: return method.toString();
        }
    }
}