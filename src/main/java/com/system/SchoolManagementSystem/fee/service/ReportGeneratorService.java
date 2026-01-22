package com.system.SchoolManagementSystem.fee.service;

import com.system.SchoolManagementSystem.fee.dto.request.ReportGenerationRequest;
import com.system.SchoolManagementSystem.fee.dto.response.ReportResponse;
import com.system.SchoolManagementSystem.fee.enums.ReportFormat;
import com.system.SchoolManagementSystem.fee.enums.ReportType;
import com.system.SchoolManagementSystem.student.entity.Student;
import com.system.SchoolManagementSystem.student.repository.StudentRepository;
import com.system.SchoolManagementSystem.transaction.entity.PaymentTransaction;
import com.system.SchoolManagementSystem.transaction.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGeneratorService {

    private final StudentRepository studentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public ReportResponse generateReport(ReportGenerationRequest request) {
        String reportId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s_%s_%s.%s",
                request.getReportType().name().toLowerCase(),
                timestamp,
                reportId,
                request.getFormat().name().toLowerCase());

        try {
            byte[] reportData;
            String filePath;

            switch (request.getFormat()) {
                case EXCEL:
                    reportData = generateExcelReport(request);
                    filePath = saveExcelReport(filename, reportData);
                    break;
                case PDF:
                    reportData = generatePdfReport(request);
                    filePath = savePdfReport(filename, reportData);
                    break;
                case CSV:
                    reportData = generateCsvReport(request);
                    filePath = saveCsvReport(filename, reportData);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
            }

            ReportResponse response = new ReportResponse();
            response.setReportId(reportId);
            response.setReportName(filename);
            response.setReportType(request.getReportType().name());
            response.setFormat(request.getFormat().name());
            response.setDownloadUrl("/api/fee-collection/reports/download/" + reportId);
            response.setFilePath(filePath);
            response.setFileSize((long) reportData.length);
            response.setGeneratedAt(LocalDateTime.now());
            response.setGeneratedBy("System"); // In real app, get from authentication

            // Set metadata
            ReportResponse.ReportMetadata metadata = new ReportResponse.ReportMetadata();
            metadata.setPeriodStart(request.getStartDate().atStartOfDay());
            metadata.setPeriodEnd(request.getEndDate().atTime(23, 59, 59));

            if (request.getGrades() != null) {
                metadata.setIncludedGrades(request.getGrades());
            }

            response.setMetadata(metadata);

            return response;

        } catch (Exception e) {
            log.error("Failed to generate report", e);
            throw new RuntimeException("Failed to generate report: " + e.getMessage());
        }
    }

    private byte[] generateExcelReport(ReportGenerationRequest request) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Fee Collection Report");

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Student ID", "Student Name", "Grade", "Total Fee",
                    "Paid Amount", "Pending Amount", "Fee Status",
                    "Last Payment Date", "Reminders Sent"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Get data based on report type
            List<Student> students = getStudentsForReport(request);

            // Fill data rows
            int rowNum = 1;
            for (Student student : students) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(student.getStudentId());
                row.createCell(1).setCellValue(student.getFullName());
                row.createCell(2).setCellValue(student.getGrade());
                row.createCell(3).setCellValue(student.getTotalFee() != null ? student.getTotalFee() : 0.0);
                row.createCell(4).setCellValue(student.getPaidAmount() != null ? student.getPaidAmount() : 0.0);
                row.createCell(5).setCellValue(student.getPendingAmount() != null ? student.getPendingAmount() : 0.0);
                row.createCell(6).setCellValue(student.getFeeStatus() != null ? student.getFeeStatus().name() : "PENDING");

                // Get last payment date
                List<PaymentTransaction> transactions = paymentTransactionRepository
                        .findByStudentId(student.getId());
                if (!transactions.isEmpty()) {
                    LocalDateTime lastPayment = transactions.stream()
                            .map(PaymentTransaction::getPaymentDate)
                            .max(LocalDateTime::compareTo)
                            .orElse(null);
                    if (lastPayment != null) {
                        row.createCell(7).setCellValue(
                                lastPayment.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] generatePdfReport(ReportGenerationRequest request) {
        // In real implementation, use a PDF library like iText or Apache PDFBox
        // For now, return a simple text representation
        List<Student> students = getStudentsForReport(request);

        StringBuilder sb = new StringBuilder();
        sb.append("FEE COLLECTION REPORT\n");
        sb.append("=====================\n\n");
        sb.append(String.format("Report Type: %s\n", request.getReportType()));
        sb.append(String.format("Period: %s to %s\n\n",
                request.getStartDate(), request.getEndDate()));

        sb.append(String.format("%-20s %-25s %-10s %-12s %-12s %-12s %-10s\n",
                "Student ID", "Student Name", "Grade", "Total Fee", "Paid", "Pending", "Status"));
        sb.append(String.format("%-20s %-25s %-10s %-12s %-12s %-12s %-10s\n",
                "----------", "------------", "-----", "---------", "----", "-------", "------"));

        double totalFee = 0, totalPaid = 0, totalPending = 0;

        for (Student student : students) {
            sb.append(String.format("%-20s %-25s %-10s %-12.2f %-12.2f %-12.2f %-10s\n",
                    student.getStudentId(),
                    student.getFullName(),
                    student.getGrade(),
                    student.getTotalFee() != null ? student.getTotalFee() : 0.0,
                    student.getPaidAmount() != null ? student.getPaidAmount() : 0.0,
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0,
                    student.getFeeStatus() != null ? student.getFeeStatus().name() : "PENDING"));

            totalFee += student.getTotalFee() != null ? student.getTotalFee() : 0.0;
            totalPaid += student.getPaidAmount() != null ? student.getPaidAmount() : 0.0;
            totalPending += student.getPendingAmount() != null ? student.getPendingAmount() : 0.0;
        }

        sb.append("\n\nSUMMARY\n");
        sb.append("=======\n");
        sb.append(String.format("Total Students: %d\n", students.size()));
        sb.append(String.format("Total Fee: ₹%.2f\n", totalFee));
        sb.append(String.format("Total Paid: ₹%.2f\n", totalPaid));
        sb.append(String.format("Total Pending: ₹%.2f\n", totalPending));
        sb.append(String.format("Collection Rate: %.1f%%\n", totalFee > 0 ? (totalPaid / totalFee) * 100 : 0));

        return sb.toString().getBytes();
    }

    private byte[] generateCsvReport(ReportGenerationRequest request) {
        List<Student> students = getStudentsForReport(request);

        StringBuilder csv = new StringBuilder();
        csv.append("Student ID,Student Name,Grade,Total Fee,Paid Amount,Pending Amount,Fee Status,Last Payment,Contact,Email\n");

        for (Student student : students) {
            csv.append(String.format("\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    student.getStudentId(),
                    student.getFullName(),
                    student.getGrade(),
                    student.getTotalFee() != null ? student.getTotalFee() : 0.0,
                    student.getPaidAmount() != null ? student.getPaidAmount() : 0.0,
                    student.getPendingAmount() != null ? student.getPendingAmount() : 0.0,
                    student.getFeeStatus() != null ? student.getFeeStatus().name() : "PENDING",
                    getLastPaymentDate(student.getId()),
                    student.getPhone() != null ? student.getPhone() : "",
                    student.getEmail() != null ? student.getEmail() : ""));
        }

        return csv.toString().getBytes();
    }

    private String getLastPaymentDate(Long studentId) {
        List<PaymentTransaction> transactions = paymentTransactionRepository
                .findByStudentId(studentId);

        if (!transactions.isEmpty()) {
            return transactions.stream()
                    .map(PaymentTransaction::getPaymentDate)
                    .max(LocalDateTime::compareTo)
                    .map(date -> date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .orElse("");
        }
        return "";
    }

    private List<Student> getStudentsForReport(ReportGenerationRequest request) {
        List<Student> allStudents = studentRepository.findAll();

        return allStudents.stream()
                .filter(s -> {
                    // Filter by date (admission date)
                    if (request.getStartDate() != null && s.getAdmissionDate() != null) {
                        if (s.getAdmissionDate().isBefore(request.getStartDate())) {
                            return false;
                        }
                    }
                    if (request.getEndDate() != null && s.getAdmissionDate() != null) {
                        if (s.getAdmissionDate().isAfter(request.getEndDate())) {
                            return false;
                        }
                    }

                    // Filter by grades if specified
                    if (request.getGrades() != null && !request.getGrades().isEmpty()) {
                        return request.getGrades().contains(s.getGrade());
                    }

                    return true;
                })
                .collect(Collectors.toList());
    }

    private String saveExcelReport(String filename, byte[] data) throws Exception {
        return saveReportFile(filename, data, "reports/excel");
    }

    private String savePdfReport(String filename, byte[] data) throws Exception {
        return saveReportFile(filename, data, "reports/pdf");
    }

    private String saveCsvReport(String filename, byte[] data) throws Exception {
        return saveReportFile(filename, data, "reports/csv");
    }

    private String saveReportFile(String filename, byte[] data, String directory) throws Exception {
        // Create directory if it doesn't exist
        Path reportsDir = Paths.get("uploads", directory);
        Files.createDirectories(reportsDir);

        // Save file
        Path filePath = reportsDir.resolve(filename);
        Files.write(filePath, data);

        return filePath.toString();
    }

    public byte[] downloadReport(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new RuntimeException("Report file not found: " + filePath);
    }
}