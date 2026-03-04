package com.careertalk.analysis.portfolio.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class FileParserUtil {

    // 텍스트 추출 로직
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

    private String extractTextFromPdf(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromPptx(MultipartFile file) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(file.getInputStream());
             SlideShowExtractor<XSLFShape, XSLFTextParagraph> extractor = new SlideShowExtractor<>(ppt)) {
            extractor.setNotesByDefault(true);
            extractor.setMasterByDefault(true);
            return extractor.getText();
        }
    }

    //  몇 장이 들어오든 전부 변환하도록 수정
    public List<String> extractImagesAsBase64(MultipartFile file) {
        List<String> base64Images = new ArrayList<>();
        String fileName = file.getOriginalFilename();
        if (fileName == null) return base64Images;

        fileName = fileName.toLowerCase();

        try {
            if (fileName.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(file.getInputStream())) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    int pages = document.getNumberOfPages(); //  파일의 전체 페이지 수를 끝까지 다 돕니다.
                    for (int i = 0; i < pages; i++) {
                        // 50 DPI로 아주 흐릿하고 용량 작게 캡처 (비용/속도 최적화)
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 50);
                        base64Images.add(convertToBase64(bim));
                    }
                }
            } else if (fileName.endsWith(".pptx")) {
                try (XMLSlideShow ppt = new XMLSlideShow(file.getInputStream())) {
                    Dimension pgsize = ppt.getPageSize();
                    double scale = 0.3; // 크기를 30%로 축소해서 렌더링
                    int width = (int) (pgsize.width * scale);
                    int height = (int) (pgsize.height * scale);

                    int pages = ppt.getSlides().size();
                    for (int i = 0; i < pages; i++) {
                        XSLFSlide slide = ppt.getSlides().get(i);
                        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = img.createGraphics();
                        graphics.scale(scale, scale);
                        slide.draw(graphics);
                        base64Images.add(convertToBase64(img));
                    }
                }
            }
        } catch (Exception e) {
            log.error("이미지 썸네일 추출 중 에러 발생: {}", e.getMessage());
        }
        return base64Images;
    }

    private String convertToBase64(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
    //  S3에서 다운받은 InputStream용 이미지 추출 로직 (재분석용)
    public List<String> extractImagesAsBase64FromStream(java.io.InputStream inputStream, String fileName) {
        List<String> base64Images = new ArrayList<>();
        if (fileName == null) return base64Images;

        fileName = fileName.toLowerCase();

        try {
            if (fileName.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(inputStream)) {
                    PDFRenderer pdfRenderer = new PDFRenderer(document);
                    int pages = document.getNumberOfPages();
                    for (int i = 0; i < pages; i++) {
                        BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 50);
                        base64Images.add(convertToBase64(bim));
                    }
                }
            } else if (fileName.endsWith(".pptx")) {
                try (XMLSlideShow ppt = new XMLSlideShow(inputStream)) {
                    Dimension pgsize = ppt.getPageSize();
                    double scale = 0.3;
                    int width = (int) (pgsize.width * scale);
                    int height = (int) (pgsize.height * scale);

                    int pages = ppt.getSlides().size();
                    for (int i = 0; i < pages; i++) {
                        XSLFSlide slide = ppt.getSlides().get(i);
                        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics = img.createGraphics();
                        graphics.scale(scale, scale);
                        slide.draw(graphics);
                        base64Images.add(convertToBase64(img));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Stream으로부터 이미지 썸네일 추출 중 에러 발생: {}", e.getMessage());
        } finally {
            try { if (inputStream != null) inputStream.close(); } catch (Exception ignore) {}
        }
        return base64Images;
    }
}