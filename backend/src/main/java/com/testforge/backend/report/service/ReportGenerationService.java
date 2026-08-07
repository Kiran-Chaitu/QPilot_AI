package com.testforge.backend.report.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.testforge.backend.analysis.dto.AnalysisResultResponse;
import com.testforge.backend.analysis.dto.GeneratedTestResponse;
import com.testforge.backend.analysis.dto.RiskAssessmentResponse;
import com.testforge.backend.analysis.dto.SecurityFindingResponse;
import com.testforge.backend.analysis.service.AnalysisService;
import com.testforge.backend.auth.entity.User;
import com.testforge.backend.common.storage.FileStorageService;
import com.testforge.backend.project.entity.Project;
import com.testforge.backend.project.service.ProjectService;
import com.testforge.backend.report.entity.Report;
import com.testforge.backend.report.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the final AI Quality Report as a downloadable PDF using OpenPDF:
 * project summary, code understanding, generated tests, security findings
 * and the overall risk/coverage score.
 */
@Service
public class ReportGenerationService {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.DARK_GRAY);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(30, 60, 114));
    private static final Font SUBSECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

    private final ProjectService projectService;
    private final AnalysisService analysisService;
    private final FileStorageService fileStorageService;
    private final ReportRepository reportRepository;

    public ReportGenerationService(ProjectService projectService, AnalysisService analysisService,
                                    FileStorageService fileStorageService, ReportRepository reportRepository) {
        this.projectService = projectService;
        this.analysisService = analysisService;
        this.fileStorageService = fileStorageService;
        this.reportRepository = reportRepository;
    }

    public Path generate(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        AnalysisResultResponse result = analysisService.getLatestResult(user, projectId);

        byte[] pdfBytes = renderPdf(project, result);

        String fileName = "ai-testpilot-report-" + projectId + ".pdf";
        Path reportPath = fileStorageService.reportPath(projectId, fileName);
        try {
            Files.write(reportPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write report PDF to disk: " + e.getMessage(), e);
        }

        Report report = new Report();
        report.setProject(project);
        report.setAnalysisRunId(result.run().id());
        report.setStoragePath(reportPath.toString());
        reportRepository.save(report);

        return reportPath;
    }

    private byte[] renderPdf(Project project, AnalysisResultResponse result) {
        Document document = new Document(PageSize.A4, 45, 45, 55, 45);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("AI TestPilot — Quality Report", TITLE_FONT));
            document.add(new Paragraph("Generated " + java.time.Instant.now(), SMALL_FONT));
            document.add(Chunk.NEWLINE);

            addProjectSummary(document, project, result);
            addCodeSummary(document, result);
            addRiskSection(document, result.risk());
            addTestsSection(document, result.tests());
            addSecuritySection(document, result.securityFindings());

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF report: " + e.getMessage(), e);
        }
    }

    private void addProjectSummary(Document document, Project project, AnalysisResultResponse result) throws DocumentException {
        document.add(new Paragraph("Project Summary", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        addKeyValueRow(table, "Name", project.getName());
        addKeyValueRow(table, "Primary language", nullSafe(project.getPrimaryLanguage()));
        addKeyValueRow(table, "Files analyzed", project.getFileCount() != null ? project.getFileCount().toString() : "n/a");
        addKeyValueRow(table, "Status", project.getStatus().name());
        addKeyValueRow(table, "AI provider used", result.run().aiProvider());
        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addCodeSummary(Document document, AnalysisResultResponse result) throws DocumentException {
        document.add(new Paragraph("AI Code Understanding", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(nullSafe(result.run().codeSummary()), BODY_FONT));
        if (result.run().keyResponsibilities() != null && !result.run().keyResponsibilities().isEmpty()) {
            document.add(new Paragraph("Key responsibilities:", SUBSECTION_FONT));
            addBulletList(document, result.run().keyResponsibilities());
        }
        document.add(Chunk.NEWLINE);
    }

    private void addRiskSection(Document document, RiskAssessmentResponse risk) throws DocumentException {
        document.add(new Paragraph("Risk & Coverage", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (risk == null) {
            document.add(new Paragraph("No risk assessment available.", BODY_FONT));
            document.add(Chunk.NEWLINE);
            return;
        }
        Font scoreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, riskColor(risk.score()));
        document.add(new Paragraph("Overall risk score: " + risk.score() + " / 100", scoreFont));
        document.add(new Paragraph("Estimated coverage: " + risk.coverageEstimatePercent() + "%", BODY_FONT));
        document.add(Chunk.NEWLINE);
        if (!risk.reasons().isEmpty()) {
            document.add(new Paragraph("Reasons:", SUBSECTION_FONT));
            addBulletList(document, risk.reasons());
        }
        if (!risk.coverageGaps().isEmpty()) {
            document.add(new Paragraph("Coverage gaps:", SUBSECTION_FONT));
            addBulletList(document, risk.coverageGaps());
        }
        document.add(Chunk.NEWLINE);
    }

    private void addTestsSection(Document document, List<GeneratedTestResponse> tests) throws DocumentException {
        document.add(new Paragraph("Generated Tests (" + tests.size() + ")", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (tests.isEmpty()) {
            document.add(new Paragraph("No tests were generated.", BODY_FONT));
            document.add(Chunk.NEWLINE);
            return;
        }
        Map<String, Long> byType = tests.stream()
                .collect(Collectors.groupingBy(t -> t.type().name(), Collectors.counting()));
        document.add(new Paragraph("By type: " + byType.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")), BODY_FONT));
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1.3f, 3f, 2f, 3.5f});
        table.setWidthPercentage(100);
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Title");
        addHeaderCell(table, "Framework");
        addHeaderCell(table, "Target");
        for (GeneratedTestResponse t : tests) {
            table.addCell(new PdfPCell(new Phrase(t.type().name(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(t.title()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(t.framework()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(t.targetName()), BODY_FONT)));
        }
        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addSecuritySection(Document document, List<SecurityFindingResponse> findings) throws DocumentException {
        document.add(new Paragraph("Security Findings (" + findings.size() + ")", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (findings.isEmpty()) {
            document.add(new Paragraph("No security findings were reported.", BODY_FONT));
            return;
        }
        PdfPTable table = new PdfPTable(new float[]{2f, 1.2f, 4f, 4f});
        table.setWidthPercentage(100);
        addHeaderCell(table, "Category");
        addHeaderCell(table, "Severity");
        addHeaderCell(table, "Description");
        addHeaderCell(table, "Recommendation");
        for (SecurityFindingResponse f : findings) {
            table.addCell(new PdfPCell(new Phrase(nullSafe(f.category()), BODY_FONT)));
            PdfPCell severityCell = new PdfPCell(new Phrase(f.severity().name(), BODY_FONT));
            severityCell.setBackgroundColor(severityColor(f.severity().name()));
            table.addCell(severityCell);
            table.addCell(new PdfPCell(new Phrase(nullSafe(f.description()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(f.recommendation()), BODY_FONT)));
        }
        document.add(table);
    }

    private void addKeyValueRow(PdfPTable table, String key, String value) {
        PdfPCell keyCell = new PdfPCell(new Phrase(key, SUBSECTION_FONT));
        keyCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(nullSafe(value), BODY_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(keyCell);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, TABLE_HEADER_FONT));
        cell.setBackgroundColor(new Color(30, 60, 114));
        table.addCell(cell);
    }

    private void addBulletList(Document document, List<String> items) throws DocumentException {
        for (String item : items) {
            document.add(new Paragraph("• " + item, BODY_FONT));
        }
    }

    private Color riskColor(int score) {
        if (score >= 70) return new Color(198, 40, 40);
        if (score >= 40) return new Color(230, 145, 20);
        return new Color(46, 125, 50);
    }

    private Color severityColor(String severity) {
        return switch (severity) {
            case "CRITICAL" -> new Color(198, 40, 40);
            case "HIGH" -> new Color(230, 90, 60);
            case "MEDIUM" -> new Color(230, 175, 60);
            default -> new Color(180, 200, 180);
        };
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
