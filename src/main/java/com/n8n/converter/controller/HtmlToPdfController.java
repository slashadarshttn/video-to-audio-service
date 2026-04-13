package com.n8n.converter.controller;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@RestController
public class HtmlToPdfController {

    @PostMapping("/html-to-pdf")
    public ResponseEntity<Resource> convertHtmlToPdf(@RequestBody Map<String, String> body) {

        String html = body.get("html");

        if (html == null || html.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            byte[] pdfBytes = outputStream.toByteArray();

            InputStreamResource resource =
                    new InputStreamResource(new ByteArrayInputStream(pdfBytes));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=notes.pdf")
                    .contentLength(pdfBytes.length)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
