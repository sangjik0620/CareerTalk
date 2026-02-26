package com.careertalk.analysis.portfolio.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component // 스프링이 관리하는 빈으로 등록
public class FileParserUtil {

    /**
     * MultipartFile을 받아서 확장자에 따라 텍스트를 추출해주는 만능 메서드
     */
    public String extractText(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null) return "";

        fileName = fileName.toLowerCase();

        try {
            if (fileName.endsWith(".pdf")) {
                return extractTextFromPdf(file);
            } else if (fileName.endsWith(".pptx")) {
                return extractTextFromPptx(file);
            } else {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. PDF나 PPTX만 가능합니다.");
            }
        } catch (Exception e) {
            log.error("파일 텍스트 추출 중 에러 발생: {}", e.getMessage());
            throw new RuntimeException("파일을 읽는 중 문제가 발생했습니다.");
        }
    }

    // PDF에서 글자 뽑기
    private String extractTextFromPdf(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    // PPTX에서 글자 뽑기
    private String extractTextFromPptx(MultipartFile file) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(file.getInputStream());
             SlideShowExtractor<XSLFShape, XSLFTextParagraph> extractor = new SlideShowExtractor<>(ppt)) {
            // 슬라이드 노트, 마스터 슬라이드의 텍스트까지 모두 싹 긁어옵니다
            extractor.setNotesByDefault(true);
            extractor.setMasterByDefault(true);
            return extractor.getText();
        }
    }
}
