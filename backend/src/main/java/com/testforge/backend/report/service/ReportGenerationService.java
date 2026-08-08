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

    public String generateMarkdown(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        AnalysisResultResponse result = analysisService.getLatestResult(user, projectId);
        StringBuilder sb = new StringBuilder();

        sb.append("# QPilot AI — Quality & Testing Executive Report\n\n");
        sb.append("**Project Name:** ").append(project.getName()).append("\n");
        sb.append("**Primary Language:** ").append(nullSafe(project.getPrimaryLanguage())).append("\n");
        sb.append("**Files Analyzed:** ").append(project.getFileCount() != null ? project.getFileCount() : "n/a").append("\n");
        sb.append("**Status:** ").append(project.getStatus().name()).append("\n");
        sb.append("**AI Provider:** ").append(result.run().aiProvider()).append("\n");
        sb.append("**Generated:** ").append(java.time.Instant.now()).append("\n\n");

        sb.append("## Architectural Summary\n\n");
        sb.append(nullSafe(result.run().codeSummary())).append("\n\n");

        if (result.run().keyResponsibilities() != null && !result.run().keyResponsibilities().isEmpty()) {
            sb.append("### Key Responsibilities\n\n");
            for (String r : result.run().keyResponsibilities()) {
                sb.append("- ").append(r).append("\n");
            }
            sb.append("\n");
        }

        if (result.run().notableObservations() != null && !result.run().notableObservations().isEmpty()) {
            sb.append("### Notable Observations\n\n");
            for (String o : result.run().notableObservations()) {
                sb.append("- ").append(o).append("\n");
            }
            sb.append("\n");
        }

        // Risk Section
        RiskAssessmentResponse risk = result.risk();
        if (risk != null) {
            sb.append("## Risk & Coverage\n\n");
            sb.append("**Risk Score:** ").append(risk.score()).append(" / 100\n");
            sb.append("**Estimated Coverage:** ").append(risk.coverageEstimatePercent()).append("%\n\n");
            if (!risk.reasons().isEmpty()) {
                sb.append("### Risk Reasons\n\n");
                for (String r : risk.reasons()) sb.append("- ").append(r).append("\n");
                sb.append("\n");
            }
            if (!risk.coverageGaps().isEmpty()) {
                sb.append("### Coverage Gaps\n\n");
                for (String g : risk.coverageGaps()) sb.append("- ").append(g).append("\n");
                sb.append("\n");
            }
        }

        // Tests Section
        List<GeneratedTestResponse> tests = result.tests();
        sb.append("## Generated Tests (").append(tests.size()).append(")\n\n");
        if (tests.isEmpty()) {
            sb.append("No tests were generated.\n\n");
        } else {
            sb.append("| Type | Title | Framework | Target |\n");
            sb.append("|------|-------|-----------|--------|\n");
            for (GeneratedTestResponse t : tests) {
                sb.append("| ").append(t.type().name())
                  .append(" | ").append(nullSafe(t.title()))
                  .append(" | ").append(nullSafe(t.framework()))
                  .append(" | ").append(nullSafe(t.targetName())).append(" |\n");
            }
            sb.append("\n");
        }

        // Security Section
        List<SecurityFindingResponse> findings = result.securityFindings();
        sb.append("## Security Findings (").append(findings.size()).append(")\n\n");
        if (findings.isEmpty()) {
            sb.append("No security findings were reported.\n");
        } else {
            sb.append("| Category | Severity | Description | Recommendation |\n");
            sb.append("|----------|----------|-------------|----------------|\n");
            for (SecurityFindingResponse f : findings) {
                sb.append("| ").append(nullSafe(f.category()))
                  .append(" | ").append(f.severity().name())
                  .append(" | ").append(nullSafe(f.description()))
                  .append(" | ").append(nullSafe(f.recommendation())).append(" |\n");
            }
        }

        return sb.toString();
    }

    public String generateHtml(User user, Long projectId) {
        String md = generateMarkdown(user, projectId);
        // Convert markdown to simple HTML
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>QPilot AI Quality Report</title>\n");
        html.append("<style>\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
        html.append("         max-width: 960px; margin: 0 auto; padding: 40px 20px; ");
        html.append("         background: #0f172a; color: #e2e8f0; line-height: 1.6; }\n");
        html.append("  h1 { color: #10b981; border-bottom: 2px solid #1e3a5f; padding-bottom: 12px; }\n");
        html.append("  h2 { color: #6366f1; margin-top: 32px; }\n");
        html.append("  h3 { color: #f59e0b; }\n");
        html.append("  table { border-collapse: collapse; width: 100%; margin: 16px 0; }\n");
        html.append("  th { background: #1e3a5f; color: white; padding: 10px 12px; text-align: left; }\n");
        html.append("  td { padding: 8px 12px; border-bottom: 1px solid #334155; }\n");
        html.append("  tr:hover td { background: rgba(99, 102, 241, 0.08); }\n");
        html.append("  strong { color: #10b981; }\n");
        html.append("  code { background: #1e293b; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }\n");
        html.append("  ul { padding-left: 24px; }\n");
        html.append("  li { margin: 4px 0; }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        // Simple markdown-to-html conversion
        for (String line : md.split("\n")) {
            if (line.startsWith("# ")) {
                html.append("<h1>").append(escapeHtml(line.substring(2))).append("</h1>\n");
            } else if (line.startsWith("## ")) {
                html.append("<h2>").append(escapeHtml(line.substring(3))).append("</h2>\n");
            } else if (line.startsWith("### ")) {
                html.append("<h3>").append(escapeHtml(line.substring(4))).append("</h3>\n");
            } else if (line.startsWith("| ") && line.contains("---")) {
                // Skip table separator rows
            } else if (line.startsWith("| ")) {
                // Table row
                String[] cells = line.split("\\|");
                boolean isHeader = false;
                // Peek if next conceptual row is separator (we handle inline)
                html.append("<tr>");
                for (int i = 1; i < cells.length; i++) {
                    String cell = cells[i].trim();
                    if (!cell.isEmpty()) {
                        html.append("<td>").append(escapeHtml(cell)).append("</td>");
                    }
                }
                html.append("</tr>\n");
            } else if (line.startsWith("- ")) {
                html.append("<li>").append(processInlineMarkdown(line.substring(2))).append("</li>\n");
            } else if (line.startsWith("**")) {
                html.append("<p>").append(processInlineMarkdown(line)).append("</p>\n");
            } else if (!line.isBlank()) {
                html.append("<p>").append(processInlineMarkdown(line)).append("</p>\n");
            }
        }

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String processInlineMarkdown(String text) {
        String escaped = escapeHtml(text);
        // Bold: **text**
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        // Code: `text`
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        return escaped;
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
