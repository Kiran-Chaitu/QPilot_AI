package com.testforge.backend.report.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.testforge.backend.analysis.dto.AnalysisResultResponse;
import com.testforge.backend.analysis.dto.GeneratedTestResponse;
import com.testforge.backend.analysis.dto.RiskAssessmentResponse;
import com.testforge.backend.analysis.dto.SecurityFindingResponse;
import com.testforge.backend.analysis.entity.ResultOrigin;
import com.testforge.backend.analysis.entity.TestExecutionStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the quality report in PDF, Markdown and HTML.
 *
 * <p>The report is a compliance artifact — people forward it to stakeholders who will not re-check the
 * app — so it carries the same distinctions the UI does, rather than flattening them into a tidier
 * summary. Specifically: measured static-analysis findings are separated from AI suggestions and
 * labelled; test counts are broken out by real execution status so "generated" is never read as
 * "passing"; the risk score is printed with the arithmetic that produced it; and the checks QPilot could
 * not perform are listed explicitly so a clean section is not mistaken for a clean bill of health.
 */
@Service
public class ReportGenerationService {

    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.DARK_GRAY);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(16, 94, 74));
    private static final Font SUBSECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font MONO_FONT = FontFactory.getFont(FontFactory.COURIER, 8, Color.DARK_GRAY);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
    private static final Font TABLE_HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);

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

        String fileName = "qpilot-quality-report-" + projectId + ".pdf";
        Path reportPath = fileStorageService.reportPath(projectId, fileName);
        try {
            Files.write(reportPath, pdfBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write the report PDF to disk: " + e.getMessage(), e);
        }

        Report report = new Report();
        report.setProject(project);
        report.setAnalysisRunId(result.run().id());
        report.setStoragePath(reportPath.toString());
        reportRepository.save(report);

        return reportPath;
    }

    // ─── PDF ─────────────────────────────────────────────────────────────────────

    private byte[] renderPdf(Project project, AnalysisResultResponse result) {
        Document document = new Document(PageSize.A4, 45, 45, 55, 45);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("QPilot AI — Quality & Testing Report", TITLE_FONT));
            document.add(new Paragraph("Generated " + java.time.Instant.now(), SMALL_FONT));
            document.add(new Paragraph("Static analysis results are measured from the project's real files and "
                    + "cite file:line evidence. AI-contributed items are labelled as suggestions and have not "
                    + "been verified.", SMALL_FONT));
            document.add(Chunk.NEWLINE);

            addProjectSummary(document, project, result);
            addStaticSummary(document, result);
            addRiskSection(document, result.risk());
            addTestsSection(document, result.tests());
            addSecuritySection(document, result.securityFindings());
            addAiSection(document, result);
            addUnavailableChecks(document, result.risk());

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render the PDF report: " + e.getMessage(), e);
        }
    }

    private void addProjectSummary(Document document, Project project, AnalysisResultResponse result)
            throws DocumentException {
        document.add(new Paragraph("Project", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        addKeyValueRow(table, "Name", project.getName());
        addKeyValueRow(table, "Source type", project.getSourceType().name());
        addKeyValueRow(table, "Primary language", nullSafe(project.getPrimaryLanguage()));
        addKeyValueRow(table, "Files indexed", project.getFileCount() != null
                ? project.getFileCount().toString() : "not measured");
        addKeyValueRow(table, "Status", project.getStatus().name());
        addKeyValueRow(table, "Analysis status", result.run().status().name());
        addKeyValueRow(table, "AI enrichment", result.run().aiEnabled()
                ? "enabled (" + nullSafe(result.run().aiProvider()) + ")"
                : "not applied — " + nullSafe(result.run().aiStatus()));
        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addStaticSummary(Document document, AnalysisResultResponse result) throws DocumentException {
        document.add(new Paragraph("Measured Summary (static analysis)", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(nullSafe(result.run().staticSummary()), BODY_FONT));
        if (result.run().observations() != null && !result.run().observations().isEmpty()) {
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Observations:", SUBSECTION_FONT));
            addBulletList(document, result.run().observations());
        }
        document.add(Chunk.NEWLINE);
    }

    private void addRiskSection(Document document, RiskAssessmentResponse risk) throws DocumentException {
        document.add(new Paragraph("Risk & Test Surface", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (risk == null) {
            document.add(new Paragraph("No risk assessment is available for this project.", BODY_FONT));
            document.add(Chunk.NEWLINE);
            return;
        }
        Font scoreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, riskColor(risk.score()));
        document.add(new Paragraph("Computed risk score: " + risk.score() + " / 100", scoreFont));
        document.add(new Paragraph("Tested surface: " + risk.testedSurfacePercent() + "%", BODY_FONT));
        document.add(new Paragraph("What that percentage measures: " + nullSafe(risk.testedSurfaceBasis()),
                SMALL_FONT));
        document.add(Chunk.NEWLINE);

        if (risk.measured() != null) {
            RiskAssessmentResponse.MeasuredCounts m = risk.measured();
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            addKeyValueRow(table, "Source files", String.valueOf(m.sourceFileCount()));
            addKeyValueRow(table, "Test files", String.valueOf(m.testFileCount()));
            addKeyValueRow(table, "Non-blank lines of code", String.valueOf(m.totalLinesOfCode()));
            addKeyValueRow(table, "HTTP endpoints discovered", String.valueOf(m.endpointCount()));
            addKeyValueRow(table, "Endpoints referenced by tests", String.valueOf(m.endpointsReferencedByTests()));
            addKeyValueRow(table, "Findings by severity",
                    m.criticalFindingCount() + " critical, " + m.highFindingCount() + " high, "
                            + m.mediumFindingCount() + " medium, " + m.lowFindingCount() + " low");
            document.add(table);
            document.add(Chunk.NEWLINE);
        }

        if (risk.scoreBreakdown() != null && !risk.scoreBreakdown().isEmpty()) {
            document.add(new Paragraph("How the score was calculated:", SUBSECTION_FONT));
            addBulletList(document, risk.scoreBreakdown());
            document.add(Chunk.NEWLINE);
        }
        if (risk.reasons() != null && !risk.reasons().isEmpty()) {
            document.add(new Paragraph("Risk drivers:", SUBSECTION_FONT));
            addBulletList(document, risk.reasons());
            document.add(Chunk.NEWLINE);
        }
        if (risk.coverageGaps() != null && !risk.coverageGaps().isEmpty()) {
            document.add(new Paragraph("Untested endpoints / areas:", SUBSECTION_FONT));
            addBulletList(document, risk.coverageGaps());
            document.add(Chunk.NEWLINE);
        }
    }

    private void addTestsSection(Document document, List<GeneratedTestResponse> tests) throws DocumentException {
        document.add(new Paragraph("Tests (" + tests.size() + ")", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (tests.isEmpty()) {
            document.add(new Paragraph("No tests were generated.", BODY_FONT));
            document.add(Chunk.NEWLINE);
            return;
        }

        // Execution breakdown comes first, because it is the number a reader is most likely to
        // misinterpret: "generated" is not "passing", and the report has to make that impossible to miss.
        Map<TestExecutionStatus, Long> byStatus = tests.stream()
                .collect(Collectors.groupingBy(GeneratedTestResponse::executionStatus,
                        LinkedHashMap::new, Collectors.counting()));
        document.add(new Paragraph("Execution status breakdown:", SUBSECTION_FONT));
        for (Map.Entry<TestExecutionStatus, Long> entry : byStatus.entrySet()) {
            document.add(new Paragraph("• " + describeStatus(entry.getKey()) + ": " + entry.getValue(), BODY_FONT));
        }
        document.add(new Paragraph("Only EXECUTED_* statuses represent a test that QPilot actually ran against a "
                + "live target; every other status means the test exists but produced no observed result.",
                SMALL_FONT));
        document.add(Chunk.NEWLINE);

        Map<String, Long> byType = tests.stream()
                .collect(Collectors.groupingBy(t -> t.type().name(), Collectors.counting()));
        document.add(new Paragraph("By type: " + byType.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")), BODY_FONT));
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(new float[]{1.1f, 2.6f, 1.9f, 1.6f, 1.9f});
        table.setWidthPercentage(100);
        addHeaderCell(table, "Type");
        addHeaderCell(table, "Title");
        addHeaderCell(table, "Target");
        addHeaderCell(table, "Origin");
        addHeaderCell(table, "Execution result");
        for (GeneratedTestResponse test : tests) {
            table.addCell(new PdfPCell(new Phrase(test.type().name(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(test.title()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(nullSafe(test.targetName()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(describeOrigin(test.origin()), BODY_FONT)));

            PdfPCell resultCell = new PdfPCell(new Phrase(describeExecution(test), BODY_FONT));
            resultCell.setBackgroundColor(executionColor(test.executionStatus()));
            table.addCell(resultCell);
        }
        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private void addSecuritySection(Document document, List<SecurityFindingResponse> findings) throws DocumentException {
        List<SecurityFindingResponse> measured = findings.stream()
                .filter(f -> f.origin() == ResultOrigin.STATIC_ANALYSIS).toList();
        List<SecurityFindingResponse> suggested = findings.stream()
                .filter(f -> f.origin() == ResultOrigin.AI_SUGGESTION).toList();

        document.add(new Paragraph("Security Findings — Measured (" + measured.size() + ")", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (measured.isEmpty()) {
            document.add(new Paragraph("No configured static-analysis pattern matched the scanned source. Note "
                    + "that this covers the rule set QPilot ships and is not equivalent to a full security "
                    + "audit.", BODY_FONT));
        } else {
            document.add(new Paragraph("Each row below was produced by a rule matching real source, and cites "
                    + "the file and line where it was observed.", SMALL_FONT));
            document.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(new float[]{1.7f, 1f, 2.4f, 3.2f, 3.2f});
            table.setWidthPercentage(100);
            addHeaderCell(table, "Category");
            addHeaderCell(table, "Severity");
            addHeaderCell(table, "Evidence (file:line)");
            addHeaderCell(table, "Description");
            addHeaderCell(table, "Recommendation");
            for (SecurityFindingResponse finding : measured) {
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.category()), BODY_FONT)));
                PdfPCell severityCell = new PdfPCell(new Phrase(finding.severity().name(), BODY_FONT));
                severityCell.setBackgroundColor(severityColor(finding.severity().name()));
                table.addCell(severityCell);

                String evidence = nullSafe(finding.location())
                        + (finding.lineNumber() != null ? ":" + finding.lineNumber() : "")
                        + (finding.occurrenceCount() != null && finding.occurrenceCount() > 1
                            ? "\n(" + finding.occurrenceCount() + " occurrences)" : "")
                        + (finding.evidence() != null ? "\n" + finding.evidence() : "");
                table.addCell(new PdfPCell(new Phrase(evidence, MONO_FONT)));
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.description()), BODY_FONT)));
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.recommendation()), BODY_FONT)));
            }
            document.add(table);
        }
        document.add(Chunk.NEWLINE);

        if (!suggested.isEmpty()) {
            document.add(new Paragraph("Security Findings — AI Suggestions (" + suggested.size() + ")",
                    SECTION_FONT));
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("The items below were proposed by a language model from the project "
                    + "context. They carry no file/line evidence, have not been verified, and require human "
                    + "review before being treated as real findings.", SMALL_FONT));
            document.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(new float[]{1.7f, 1f, 3.4f, 3.4f});
            table.setWidthPercentage(100);
            addHeaderCell(table, "Category");
            addHeaderCell(table, "Suggested severity");
            addHeaderCell(table, "Description");
            addHeaderCell(table, "Recommendation");
            for (SecurityFindingResponse finding : suggested) {
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.category()), BODY_FONT)));
                table.addCell(new PdfPCell(new Phrase(finding.severity().name(), BODY_FONT)));
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.description()), BODY_FONT)));
                table.addCell(new PdfPCell(new Phrase(nullSafe(finding.recommendation()), BODY_FONT)));
            }
            document.add(table);
            document.add(Chunk.NEWLINE);
        }
    }

    private void addAiSection(Document document, AnalysisResultResponse result) throws DocumentException {
        document.add(new Paragraph("AI Narrative & Recommendations", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        if (!result.run().aiEnabled()) {
            document.add(new Paragraph("Not applied. " + nullSafe(result.run().aiStatus()), BODY_FONT));
            document.add(new Paragraph("All measured results above were produced without AI involvement.",
                    SMALL_FONT));
            document.add(Chunk.NEWLINE);
            return;
        }
        document.add(new Paragraph("Produced by " + nullSafe(result.run().aiProvider())
                + ". Advisory only — not a measurement.", SMALL_FONT));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph(nullSafe(result.run().aiSummary()), BODY_FONT));
        if (result.run().aiKeyResponsibilities() != null && !result.run().aiKeyResponsibilities().isEmpty()) {
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Key responsibilities (AI):", SUBSECTION_FONT));
            addBulletList(document, result.run().aiKeyResponsibilities());
        }
        if (result.run().aiNotableObservations() != null && !result.run().aiNotableObservations().isEmpty()) {
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("Notable observations (AI):", SUBSECTION_FONT));
            addBulletList(document, result.run().aiNotableObservations());
        }
        document.add(Chunk.NEWLINE);
    }

    /**
     * Lists what could not be assessed. Without this, a report showing no findings in a category reads as
     * a clean result, when the truthful statement is that the category was never measured.
     */
    private void addUnavailableChecks(Document document, RiskAssessmentResponse risk) throws DocumentException {
        if (risk == null || risk.unavailableChecks() == null || risk.unavailableChecks().isEmpty()) {
            return;
        }
        document.add(new Paragraph("Checks Not Performed", SECTION_FONT));
        document.add(Chunk.NEWLINE);
        document.add(new Paragraph("The following were not assessed. Their absence from the findings above is "
                + "not evidence that no issues exist in these areas.", SMALL_FONT));
        document.add(Chunk.NEWLINE);
        addBulletList(document, risk.unavailableChecks());
    }

    // ─── Markdown ────────────────────────────────────────────────────────────────

    public String generateMarkdown(User user, Long projectId) {
        Project project = projectService.getOwnedProject(user, projectId);
        AnalysisResultResponse result = analysisService.getLatestResult(user, projectId);
        StringBuilder sb = new StringBuilder();

        sb.append("# QPilot AI — Quality & Testing Report\n\n");
        sb.append("> Measured results come from static analysis of the project's real files and cite ")
                .append("file:line evidence. AI-contributed items are labelled as unverified suggestions.\n\n");

        sb.append("**Project:** ").append(project.getName()).append("\n\n");
        sb.append("**Source type:** ").append(project.getSourceType().name()).append("\n\n");
        sb.append("**Primary language:** ").append(nullSafe(project.getPrimaryLanguage())).append("\n\n");
        sb.append("**Files indexed:** ").append(project.getFileCount() != null
                ? project.getFileCount().toString() : "not measured").append("\n\n");
        sb.append("**Analysis status:** ").append(result.run().status().name()).append("\n\n");
        sb.append("**AI enrichment:** ").append(result.run().aiEnabled()
                ? "enabled (" + nullSafe(result.run().aiProvider()) + ")"
                : "not applied — " + nullSafe(result.run().aiStatus())).append("\n\n");
        sb.append("**Generated:** ").append(java.time.Instant.now()).append("\n\n");

        sb.append("## Measured Summary\n\n").append(nullSafe(result.run().staticSummary())).append("\n\n");
        if (result.run().observations() != null && !result.run().observations().isEmpty()) {
            sb.append("### Observations\n\n");
            result.run().observations().forEach(o -> sb.append("- ").append(o).append('\n'));
            sb.append('\n');
        }

        RiskAssessmentResponse risk = result.risk();
        if (risk != null) {
            sb.append("## Risk & Test Surface\n\n");
            sb.append("**Computed risk score:** ").append(risk.score()).append(" / 100\n\n");
            sb.append("**Tested surface:** ").append(risk.testedSurfacePercent()).append("%\n\n");
            sb.append("*What that measures:* ").append(nullSafe(risk.testedSurfaceBasis())).append("\n\n");

            if (risk.measured() != null) {
                RiskAssessmentResponse.MeasuredCounts m = risk.measured();
                sb.append("### Measured inputs\n\n");
                sb.append("| Metric | Value |\n|---|---|\n");
                sb.append("| Source files | ").append(m.sourceFileCount()).append(" |\n");
                sb.append("| Test files | ").append(m.testFileCount()).append(" |\n");
                sb.append("| Non-blank lines of code | ").append(m.totalLinesOfCode()).append(" |\n");
                sb.append("| HTTP endpoints discovered | ").append(m.endpointCount()).append(" |\n");
                sb.append("| Endpoints referenced by tests | ").append(m.endpointsReferencedByTests()).append(" |\n");
                sb.append("| Critical / High / Medium / Low findings | ")
                        .append(m.criticalFindingCount()).append(" / ").append(m.highFindingCount())
                        .append(" / ").append(m.mediumFindingCount()).append(" / ")
                        .append(m.lowFindingCount()).append(" |\n\n");
            }
            if (risk.scoreBreakdown() != null && !risk.scoreBreakdown().isEmpty()) {
                sb.append("### How the score was calculated\n\n");
                risk.scoreBreakdown().forEach(line -> sb.append("- ").append(line).append('\n'));
                sb.append('\n');
            }
            if (risk.reasons() != null && !risk.reasons().isEmpty()) {
                sb.append("### Risk drivers\n\n");
                risk.reasons().forEach(reason -> sb.append("- ").append(reason).append('\n'));
                sb.append('\n');
            }
            if (risk.coverageGaps() != null && !risk.coverageGaps().isEmpty()) {
                sb.append("### Untested endpoints / areas\n\n");
                risk.coverageGaps().forEach(gap -> sb.append("- ").append(gap).append('\n'));
                sb.append('\n');
            }
        }

        List<GeneratedTestResponse> tests = result.tests();
        sb.append("## Tests (").append(tests.size()).append(")\n\n");
        if (tests.isEmpty()) {
            sb.append("No tests were generated.\n\n");
        } else {
            Map<TestExecutionStatus, Long> byStatus = tests.stream()
                    .collect(Collectors.groupingBy(GeneratedTestResponse::executionStatus,
                            LinkedHashMap::new, Collectors.counting()));
            sb.append("### Execution status\n\n");
            byStatus.forEach((status, count) ->
                    sb.append("- **").append(describeStatus(status)).append("**: ").append(count).append('\n'));
            sb.append("\n*Only `EXECUTED_*` statuses represent a test QPilot actually ran against a live ")
                    .append("target. Every other status means the test exists but produced no observed result.*\n\n");

            sb.append("| Type | Title | Target | Origin | Execution result |\n|---|---|---|---|---|\n");
            for (GeneratedTestResponse test : tests) {
                sb.append("| ").append(test.type().name())
                        .append(" | ").append(nullSafe(test.title()))
                        .append(" | ").append(nullSafe(test.targetName()))
                        .append(" | ").append(describeOrigin(test.origin()))
                        .append(" | ").append(describeExecution(test)).append(" |\n");
            }
            sb.append('\n');
        }

        List<SecurityFindingResponse> measured = result.securityFindings().stream()
                .filter(f -> f.origin() == ResultOrigin.STATIC_ANALYSIS).toList();
        List<SecurityFindingResponse> suggested = result.securityFindings().stream()
                .filter(f -> f.origin() == ResultOrigin.AI_SUGGESTION).toList();

        sb.append("## Security Findings — Measured (").append(measured.size()).append(")\n\n");
        if (measured.isEmpty()) {
            sb.append("No configured static-analysis pattern matched the scanned source. This covers the rule ")
                    .append("set QPilot ships and is not equivalent to a full security audit.\n\n");
        } else {
            sb.append("| Category | Severity | Evidence | Description | Recommendation |\n|---|---|---|---|---|\n");
            for (SecurityFindingResponse finding : measured) {
                String evidence = nullSafe(finding.location())
                        + (finding.lineNumber() != null ? ":" + finding.lineNumber() : "")
                        + (finding.evidence() != null ? " — `" + finding.evidence().replace("|", "\\|") + "`" : "");
                sb.append("| ").append(nullSafe(finding.category()))
                        .append(" | ").append(finding.severity().name())
                        .append(" | ").append(evidence)
                        .append(" | ").append(escapePipes(finding.description()))
                        .append(" | ").append(escapePipes(finding.recommendation())).append(" |\n");
            }
            sb.append('\n');
        }

        if (!suggested.isEmpty()) {
            sb.append("## Security Findings — AI Suggestions (").append(suggested.size()).append(")\n\n");
            sb.append("> These were proposed by a language model and carry no file/line evidence. ")
                    .append("They are unverified and require human review.\n\n");
            sb.append("| Category | Suggested severity | Description | Recommendation |\n|---|---|---|---|\n");
            for (SecurityFindingResponse finding : suggested) {
                sb.append("| ").append(nullSafe(finding.category()))
                        .append(" | ").append(finding.severity().name())
                        .append(" | ").append(escapePipes(finding.description()))
                        .append(" | ").append(escapePipes(finding.recommendation())).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## AI Narrative & Recommendations\n\n");
        if (!result.run().aiEnabled()) {
            sb.append("Not applied. ").append(nullSafe(result.run().aiStatus())).append("\n\n");
            sb.append("All measured results above were produced without AI involvement.\n\n");
        } else {
            sb.append("*Produced by ").append(nullSafe(result.run().aiProvider()))
                    .append(". Advisory only — not a measurement.*\n\n");
            sb.append(nullSafe(result.run().aiSummary())).append("\n\n");
            if (result.run().aiKeyResponsibilities() != null && !result.run().aiKeyResponsibilities().isEmpty()) {
                sb.append("### Key responsibilities (AI)\n\n");
                result.run().aiKeyResponsibilities().forEach(r -> sb.append("- ").append(r).append('\n'));
                sb.append('\n');
            }
            if (result.run().aiNotableObservations() != null && !result.run().aiNotableObservations().isEmpty()) {
                sb.append("### Notable observations (AI)\n\n");
                result.run().aiNotableObservations().forEach(o -> sb.append("- ").append(o).append('\n'));
                sb.append('\n');
            }
        }

        if (risk != null && risk.unavailableChecks() != null && !risk.unavailableChecks().isEmpty()) {
            sb.append("## Checks Not Performed\n\n");
            sb.append("> The absence of findings in these areas is not evidence that no issues exist.\n\n");
            risk.unavailableChecks().forEach(check -> sb.append("- ").append(check).append('\n'));
            sb.append('\n');
        }

        return sb.toString();
    }

    // ─── HTML ────────────────────────────────────────────────────────────────────

    public String generateHtml(User user, Long projectId) {
        String markdown = generateMarkdown(user, projectId);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>QPilot AI Quality Report</title>\n");
        html.append("<style>\n");
        html.append("  :root { color-scheme: light dark; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n");
        html.append("         max-width: 1040px; margin: 0 auto; padding: 40px 24px;\n");
        html.append("         background: #0b1220; color: #e6edf6; line-height: 1.65; }\n");
        html.append("  h1 { color: #34d399; border-bottom: 2px solid #1f3b52; padding-bottom: 12px; }\n");
        html.append("  h2 { color: #7dd3fc; margin-top: 36px; }\n");
        html.append("  h3 { color: #fbbf24; }\n");
        html.append("  blockquote { border-left: 3px solid #34d399; margin: 16px 0; padding: 8px 16px;\n");
        html.append("               background: rgba(52,211,153,0.08); color: #b9c7d9; }\n");
        html.append("  table { border-collapse: collapse; width: 100%; margin: 16px 0; display: block;\n");
        html.append("          overflow-x: auto; }\n");
        html.append("  th { background: #16324a; color: #fff; padding: 10px 12px; text-align: left;\n");
        html.append("       font-size: 0.85rem; }\n");
        html.append("  td { padding: 8px 12px; border-bottom: 1px solid #22364d; vertical-align: top;\n");
        html.append("       font-size: 0.9rem; }\n");
        html.append("  tr:hover td { background: rgba(125,211,252,0.06); }\n");
        html.append("  strong { color: #34d399; }\n");
        html.append("  code { background: #16243a; padding: 2px 6px; border-radius: 4px; font-size: 0.85em; }\n");
        html.append("  ul { padding-left: 22px; } li { margin: 4px 0; }\n");
        html.append("  em { color: #9fb3c8; }\n");
        html.append("</style>\n</head>\n<body>\n");

        boolean inTable = false;
        boolean inList = false;
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();

            boolean isTableRow = trimmed.startsWith("|");
            boolean isSeparatorRow = isTableRow && trimmed.matches("\\|[-:| ]+\\|");

            if (inTable && !isTableRow) {
                html.append("</table>\n");
                inTable = false;
            }
            if (inList && !trimmed.startsWith("- ")) {
                html.append("</ul>\n");
                inList = false;
            }

            if (isSeparatorRow) {
                continue; // the header/body divider carries no content
            }
            if (isTableRow) {
                if (!inTable) {
                    html.append("<table>\n");
                    inTable = true;
                    // The first row of a Markdown table is its header, so it is emitted as <th> cells —
                    // otherwise every table would render as an unlabelled grid of values.
                    appendTableRow(html, trimmed, true);
                    continue;
                }
                appendTableRow(html, trimmed, false);
                continue;
            }
            if (trimmed.startsWith("### ")) {
                html.append("<h3>").append(inline(trimmed.substring(4))).append("</h3>\n");
            } else if (trimmed.startsWith("## ")) {
                html.append("<h2>").append(inline(trimmed.substring(3))).append("</h2>\n");
            } else if (trimmed.startsWith("# ")) {
                html.append("<h1>").append(inline(trimmed.substring(2))).append("</h1>\n");
            } else if (trimmed.startsWith("> ")) {
                html.append("<blockquote>").append(inline(trimmed.substring(2))).append("</blockquote>\n");
            } else if (trimmed.startsWith("- ")) {
                if (!inList) {
                    html.append("<ul>\n");
                    inList = true;
                }
                html.append("<li>").append(inline(trimmed.substring(2))).append("</li>\n");
            } else if (!trimmed.isEmpty()) {
                html.append("<p>").append(inline(trimmed)).append("</p>\n");
            }
        }
        if (inTable) {
            html.append("</table>\n");
        }
        if (inList) {
            html.append("</ul>\n");
        }

        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private void appendTableRow(StringBuilder html, String row, boolean header) {
        String[] cells = row.split("\\|");
        html.append("<tr>");
        for (int i = 1; i < cells.length; i++) {
            String cell = cells[i].trim();
            html.append(header ? "<th>" : "<td>").append(inline(cell)).append(header ? "</th>" : "</td>");
        }
        html.append("</tr>\n");
    }

    // ─── Shared helpers ──────────────────────────────────────────────────────────

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
        cell.setBackgroundColor(new Color(16, 94, 74));
        table.addCell(cell);
    }

    private void addBulletList(Document document, List<String> items) throws DocumentException {
        for (String item : items) {
            document.add(new Paragraph("• " + item, BODY_FONT));
        }
    }

    private String describeOrigin(ResultOrigin origin) {
        return origin == ResultOrigin.AI_SUGGESTION ? "AI (unverified)" : "Static analysis";
    }

    private String describeStatus(TestExecutionStatus status) {
        return switch (status) {
            case GENERATED -> "GENERATED (not run)";
            case NOT_EXECUTABLE -> "NOT EXECUTABLE by QPilot";
            case SKIPPED -> "SKIPPED (prerequisite missing)";
            case EXECUTED_PASSED -> "EXECUTED — PASSED";
            case EXECUTED_FAILED -> "EXECUTED — FAILED";
            case EXECUTION_ERROR -> "EXECUTION ERROR (no response)";
        };
    }

    private String describeExecution(GeneratedTestResponse test) {
        return switch (test.executionStatus()) {
            case EXECUTED_PASSED -> "PASSED — HTTP " + test.observedHttpStatus()
                    + " in " + test.executionLatencyMs() + "ms";
            case EXECUTED_FAILED -> "FAILED — HTTP " + test.observedHttpStatus()
                    + ", expected " + nullSafe(test.expectedStatusCodes());
            case EXECUTION_ERROR -> "ERROR — no response received";
            case SKIPPED -> "SKIPPED";
            case NOT_EXECUTABLE -> "NOT EXECUTABLE";
            case GENERATED -> "GENERATED (not run)";
        };
    }

    private Color executionColor(TestExecutionStatus status) {
        return switch (status) {
            case EXECUTED_PASSED -> new Color(214, 240, 226);
            case EXECUTED_FAILED, EXECUTION_ERROR -> new Color(250, 220, 220);
            case SKIPPED, NOT_EXECUTABLE -> new Color(238, 238, 240);
            case GENERATED -> new Color(226, 236, 248);
        };
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
            default -> new Color(200, 214, 200);
        };
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String inline(String text) {
        String escaped = escapeHtml(text);
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
        escaped = escaped.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<em>$1</em>");
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        return escaped;
    }

    private String escapePipes(String value) {
        return value == null ? "n/a" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "n/a" : value;
    }
}
