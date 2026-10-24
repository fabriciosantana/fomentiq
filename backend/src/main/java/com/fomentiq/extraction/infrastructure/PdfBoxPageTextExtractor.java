package com.fomentiq.extraction.infrastructure;

import com.fomentiq.extraction.application.DocumentExcerptSelector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxPageTextExtractor implements DocumentExcerptSelector.PdfPageTextExtractor {
    @Override
    public List<DocumentExcerptSelector.PageText> pages(byte[] bytes) {
        try (var pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<DocumentExcerptSelector.PageText> result = new ArrayList<>();
            int maxPages = Math.min(pdf.getNumberOfPages(), 100);
            for (int page = 1; page <= maxPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                result.add(new DocumentExcerptSelector.PageText(page, stripper.getText(pdf)));
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to extract text from preserved PDF", exception);
        }
    }
}
