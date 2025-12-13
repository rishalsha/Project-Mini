package com.portfolio.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentParserService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        try {
            // If it's a PDF, use PDFBox for pure text extraction (ignores images)
            if (file.getContentType() != null && file.getContentType().contains("pdf")) {
                return extractPDFText(file.getInputStream());
            }

            // For other formats, use Tika
            try (InputStream inputStream = file.getInputStream()) {
                String text = tika.parseToString(inputStream);
                System.out
                        .println("Extracted " + text.length() + " characters from file: " + file.getOriginalFilename());
                return text;
            }
        } catch (Exception e) {
            System.err.println("Error extracting text from file: " + e.getMessage());
            throw new RuntimeException("Failed to extract text from document", e);
        }
    }

    private String extractPDFText(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            System.out.println("Extracted " + text.length() + " characters from PDF using PDFBox");
            return text;
        }
    }

    public String extractTextFromBytes(byte[] fileBytes, String mimeType) {
        try {
            // If it's a PDF, use PDFBox for pure text extraction
            if (mimeType != null && mimeType.contains("pdf")) {
                return extractPDFText(new java.io.ByteArrayInputStream(fileBytes));
            }

            String text = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
            System.out.println("Extracted " + text.length() + " characters from byte array");
            return text;
        } catch (Exception e) {
            System.err.println("Error extracting text from bytes: " + e.getMessage());
            throw new RuntimeException("Failed to extract text from document", e);
        }
    }
}
