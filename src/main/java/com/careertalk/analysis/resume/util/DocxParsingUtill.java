package com.careertalk.analysis.resume.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Component
public class DocxParsingUtill {

    /** 업로드 MultipartFile -> 텍스트 */
    public String parseDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            return parseDocx(is);
        }
    }

    /** S3 InputStream -> 텍스트 (핵심) */
    public String parseDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {

            StringBuilder sb = new StringBuilder();

            // 1) 문단
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\n");
                }
            }

            // 2) 표
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText();
                        if (cellText != null && !cellText.isBlank()) {
                            sb.append(cellText.trim()).append(" ");
                        }
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }

            return sb.toString().trim();
        }
    }
}