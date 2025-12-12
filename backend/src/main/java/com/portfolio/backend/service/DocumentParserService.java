package com.portfolio.backend.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentParserService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String text = tika.parseToString(inputStream);
            System.out.println("Extracted " + text.length() + " characters from file: " + file.getOriginalFilename());
            return text;
        } catch (Exception e) {
            System.err.println("Error extracting text from file: " + e.getMessage());
            throw new RuntimeException("Failed to extract text from document", e);
        }
    }

    public String extractTextFromBytes(byte[] fileBytes, String mimeType) {
        try {
            String text = tika.parseToString(new java.io.ByteArrayInputStream(fileBytes));
            System.out.println("Extracted " + text.length() + " characters from byte array");
            return text;
        } catch (Exception e) {
            System.err.println("Error extracting text from bytes: " + e.getMessage());
            throw new RuntimeException("Failed to extract text from document", e);
        }
    }
}
