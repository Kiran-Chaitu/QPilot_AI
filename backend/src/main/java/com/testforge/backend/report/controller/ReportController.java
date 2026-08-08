package com.testforge.backend.report.controller;

import com.testforge.backend.auth.entity.User;
import com.testforge.backend.report.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/projects/{id}/report")
@Tag(name = "Reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportGenerationService reportGenerationService;

    public ReportController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @GetMapping("/download")
    public ResponseEntity<FileSystemResource> download(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Path pdfPath = reportGenerationService.generate(user, id);
        FileSystemResource resource = new FileSystemResource(pdfPath);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(pdfPath.getFileName().toString())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @GetMapping("/download/md")
    public ResponseEntity<byte[]> downloadMarkdown(@AuthenticationPrincipal User user, @PathVariable Long id) {
        String markdown = reportGenerationService.generateMarkdown(user, id);
        String filename = "QPilot-AI-Report-" + id + ".md";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/download/html")
    public ResponseEntity<byte[]> downloadHtml(@AuthenticationPrincipal User user, @PathVariable Long id) {
        String html = reportGenerationService.generateHtml(user, id);
        String filename = "QPilot-AI-Report-" + id + ".html";
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(html.getBytes(StandardCharsets.UTF_8));
    }
}
