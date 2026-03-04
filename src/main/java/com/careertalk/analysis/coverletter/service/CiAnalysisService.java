package com.careertalk.analysis.coverletter.service;

import com.careertalk.analysis.coverletter.dto.CiAnalysisResponse;
import com.careertalk.analysis.coverletter.dto.CiAnalyzeTextRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CiAnalysisService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.api-url}")
    private String apiUrl;


    //직국 별 전문성 키워드 맵
    private static final Map<String, List<String>> ROLE_KEYWORDS = Map.ofEntries(
            Map.entry("기획∙전략", List.of("전략", "로드맵", "OKR", "KPI", "PRD", "요구사항", "가설", "검증", "인사이트", "지표")),
            Map.entry("마케팅∙홍보∙조사", List.of("브랜딩", "캠페인", "SEO", "SEM", "ROAS", "CAC", "LTV", "전환", "리드", "GA4", "UTM")),
            Map.entry("회계∙세무∙재무", List.of("재무제표", "결산", "전표", "원가", "예산", "IFRS", "부가세", "법인세", "자금", "내부통제", "ERP")),
            Map.entry("인사∙노무∙HRD", List.of("채용", "온보딩", "평가", "보상", "성과관리", "조직문화", "노무", "근로기준법", "급여", "4대보험", "HRD")),
            Map.entry("총무∙법무∙사무", List.of("계약", "법무", "컴플라이언스", "규정", "문서관리", "자산관리", "행정", "비용처리", "운영지원")),
            Map.entry("IT개발∙데이터", List.of("java", "spring", "jpa", "mysql", "postgres", "redis", "aws", "docker", "kubernetes", "api", "jwt", "oauth", "git", "ci/cd", "sql", "python")),
            Map.entry("디자인", List.of("ux", "ui", "디자인시스템", "프로토타입", "와이어프레임", "사용성", "figma", "타이포", "브랜딩", "컴포넌트")),
            Map.entry("영업∙판매∙무역", List.of("세일즈", "파이프라인", "리드", "제안", "견적", "계약", "crm", "매출", "수주", "업셀", "인코텀즈", "통관")),
            Map.entry("고객상담∙TM", List.of("cs", "voc", "클레임", "스크립트", "qa", "상담품질", "nps", "만족도", "sla", "이관")),
            Map.entry("구매∙자재∙물류", List.of("구매", "소싱", "단가", "발주", "협상", "재고", "wms", "tms", "scm", "리드타임", "입출고")),
            Map.entry("상품기획∙MD", List.of("md", "상품기획", "카테고리", "마진", "매입", "프로모션", "런칭", "트렌드", "상세페이지", "전환율")),
            Map.entry("운전∙운송∙배송", List.of("배송", "배차", "운행", "노선", "적재", "정시", "피킹", "패킹", "차량관리")),
            Map.entry("서비스", List.of("고객경험", "현장", "운영", "예약", "동선", "위생", "품질", "클레임", "재방문", "프로세스")),
            Map.entry("생산", List.of("생산", "공정", "라인", "품질", "불량", "표준작업", "가동률", "설비", "제조", "원가")),
            Map.entry("건설∙건축", List.of("시공", "공정", "현장", "안전", "도면", "견적", "물량", "감리", "bim", "인허가")),
            Map.entry("의료", List.of("진료", "환자", "차트", "처치", "간호", "emr", "검사", "투약", "감염관리", "보험청구")),
            Map.entry("연구∙R&D", List.of("실험", "연구", "논문", "특허", "프로토타입", "검증", "분석", "모델", "시뮬레이션", "측정")),
            Map.entry("교육", List.of("커리큘럼", "수업", "강의", "학습", "평가", "코칭", "멘토링", "교육기획", "lms", "학습성과")),
            Map.entry("미디어∙문화∙스포츠", List.of("콘텐츠", "제작", "편집", "촬영", "기획", "배급", "저작권", "공연", "전시", "sns")),
            Map.entry("금융∙보험", List.of("심사", "리스크", "신용", "채권", "포트폴리오", "수익률", "보험", "언더라이팅", "손해율", "kyc", "aml")),
            Map.entry("공공∙복지", List.of("민원", "행정", "정책", "사업", "예산", "성과", "평가", "복지", "사례관리", "기관"))
    );

    public CiAnalysisResponse analyzeFromText(CiAnalyzeTextRequest req) {
        try {
            // 룰 점수 40점
            int ruleScore = calcRuleScore(req.getJobRole(), req.getJobDetail(), req.getTitle(), req.getContent());

            //llm 점수 60점
            JsonNode resultNode = callOpenAi(req);
            return toResponse(req, resultNode, ruleScore);

        } catch (Exception e) {
            throw new RuntimeException("분석 실패: " + e.getMessage(), e);
        }
    }

    public CiAnalysisResponse analyzeFromPdf(MultipartFile file, String jobRole, String jobDetail) {
        validatePdf(file);

        try {
            String extracted = extractTextFromPdf(file);
            if (extracted == null) extracted = "";
            extracted = extracted.trim();

            // 너무 길면 LLM 토큰 폭발 방지(필요시 조절)
            extracted = limitLength(extracted, 12000);

            CiAnalyzeTextRequest req = new CiAnalyzeTextRequest();
            req.setJobRole(jobRole);
            req.setJobDetail(jobDetail);
            req.setTitle(safe(file.getOriginalFilename()));
            req.setContent(extracted.isBlank() ? "(PDF에서 텍스트를 추출하지 못했습니다.)" : extracted);

            return analyzeFromText(req);

        } catch (Exception e) {
            throw new RuntimeException("PDF 분석 실패: " + e.getMessage(), e);
        }
    }

   //open api 콜
    private JsonNode callOpenAi(CiAnalyzeTextRequest req) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("ai.api-key가 설정되지 않았습니다.");
        }

        String prompt = buildPrompt(req);
        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> body = new HashMap<>();
        body.put("model", "gpt-4.1");
        body.put("input", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity(apiUrl, entity, String.class);

        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("OpenAI 응답이 비어있습니다.");
        }

        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error") && !root.get("error").isNull()) {
            throw new RuntimeException("OpenAI ERROR: " + root.get("error").toString());
        }

        JsonNode outputNode = root.path("output");
        if (!outputNode.isArray() || outputNode.isEmpty()) {
            throw new RuntimeException("OpenAI 응답 구조 이상 (output 없음)");
        }

        JsonNode contentNode = outputNode.get(0).path("content");
        if (!contentNode.isArray() || contentNode.isEmpty()) {
            throw new RuntimeException("OpenAI 응답 구조 이상 (content 없음)");
        }

        String outputText = contentNode.get(0).path("text").asText();
        if (outputText == null || outputText.isBlank()) {
            throw new RuntimeException("GPT 응답 텍스트가 비어있습니다.");
        }

        return objectMapper.readTree(outputText);
    }

    private CiAnalysisResponse toResponse(CiAnalyzeTextRequest req, JsonNode result, int ruleScore) {

        // 질문
        List<String> questions = new ArrayList<>();
        JsonNode qNode = result.path("questions");
        if (qNode.isArray()) {
            for (JsonNode q : qNode) questions.add(q.asText());
        }

        //의도 없으면 빈배열
        List<String> intents = new ArrayList<>();
        JsonNode iNode = result.path("questionIntents");
        if (iNode.isArray()) {
            for (JsonNode it : iNode) intents.add(it.asText());
        }

        // (선택) 길이 맞추기: questions가 3개인데 intents가 0~2개면 빈값 채움
        while (intents.size() < questions.size()) intents.add("");
        if (intents.size() > questions.size()) intents = intents.subList(0, questions.size());

        // GPT 점수 읽기 (현재 "totalScore"를 LLM 점수 원천으로 사용)
        int gptScoreRaw = result.path("totalScore").asInt(0);

        // LLM 점수 0~60점 보정
        int llmScore = clamp((int) Math.round(gptScoreRaw * 0.60), 0, 60);

        // 총점 0~100점 보정
        int totalScore = clamp(ruleScore + llmScore, 0, 100);

        long fakeId = System.currentTimeMillis();

        return CiAnalysisResponse.builder()
                .analysisId(fakeId)
                .title(req.getTitle())
                .content(req.getContent())
                .ruleScore(ruleScore)
                .llmScore(llmScore)
                .totalScore(totalScore)
                .strengths(result.path("strengths").asText(""))
                .weaknesses(result.path("weaknesses").asText(""))
                .feedback(result.path("feedback").asText(""))
                .questions(questions)
                .questionIntents(intents)
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();
    }


    private String buildPrompt(CiAnalyzeTextRequest req) throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/ci-analysis.txt");

        String template = new String(
                resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );

        return template
                .replace("{{jobRole}}", safe(req.getJobRole()))
                .replace("{{jobDetail}}", safe(req.getJobDetail()))
                .replace("{{title}}", safe(req.getTitle()))
                .replace("{{content}}", safe(req.getContent()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    //룰 최대 40점
    private int calcRuleScore(String jobRole, String jobDetail, String title, String content) {
        String t = safe(title).trim();
        String c = safe(content).trim();
        String jr = safe(jobRole).trim();
        String jd = safe(jobDetail).trim();

        int score = 0;

        // 길이 (0~10)점
        int len = c.length();
        if (len >= 800) score += 10;
        else if (len >= 500) score += 8;
        else if (len >= 300) score += 6;
        else if (len >= 150) score += 4;
        else if (len >= 30) score += 2;

        // 문단 구조 (0~10)점
        int paragraphs = countParagraphs(c);
        if (paragraphs >= 4) score += 10;
        else if (paragraphs == 3) score += 8;
        else if (paragraphs == 2) score += 6;
        else if (paragraphs == 1) score += 3;

        // 수치 성과 포함 (0~8)점
        int numbers = countNumbers(c);
        if (numbers >= 4) score += 8;
        else if (numbers == 3) score += 6;
        else if (numbers == 2) score += 4;
        else if (numbers == 1) score += 2;

        // 직무 적합 키워드 (0~6)점
        int fit = 0;
        if (!jr.isBlank() && (containsAnyToken(c, jr) || containsAnyToken(t, jr))) fit += 3;
        if (!jd.isBlank() && (containsAnyToken(c, jd) || containsAnyToken(t, jd))) fit += 3;
        score += clamp(fit, 0, 6);

        // 직군 전문성 키워드 (0~6)점
        int expert = countRoleKeywords(c, jr);
        if (expert >= 6) score += 6;
        else if (expert >= 4) score += 5;
        else if (expert >= 2) score += 3;
        else if (expert >= 1) score += 1;

        // 액션/기여 표현 (0~6)점
        int action = countActionWords(c);
        if (action >= 6) score += 6;
        else if (action >= 4) score += 5;
        else if (action >= 2) score += 3;
        else if (action >= 1) score += 1;

        return clamp(score, 0, 40);
    }

    private int countParagraphs(String c) {
        if (c.isBlank()) return 0;
        String[] parts = c.split("\\n\\s*\\n");
        int count = 0;
        for (String p : parts) {
            if (!p.trim().isBlank()) count++;
        }
        return count;
    }

    private int countNumbers(String c) {
        if (c.isBlank()) return 0;
        // 숫자, %, ms, s, 건, 명 등 감지
        Pattern p = Pattern.compile("(\\d+\\.?\\d*)\\s*(%|ms|s|초|분|건|명|개|회|배|GB|MB)?");
        var m = p.matcher(c);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private boolean containsAnyToken(String text, String phrase) {
        if (text == null || text.isBlank() || phrase == null || phrase.isBlank()) return false;

        String lowerText = text.toLowerCase();
        String lowerPhrase = phrase.toLowerCase().trim();

        // 공백,슬래시,언더스코어,하이픈,쉼표,중점(∙) 기준으로 분리
        String[] tokens = lowerPhrase.split("[\\s/,_\\-∙]+");

        for (String tk : tokens) {
            tk = tk.trim();
            if (tk.length() < 2) continue;  // 너무 짧은 단어는 무시(오탐 방지)
            if (lowerText.contains(tk)) return true;
        }
        return false;
    }

    private int countActionWords(String c) {
        if (c.isBlank()) return 0;
        String[] words = {
                "개선", "최적화", "구현", "설계", "리팩토링", "분석", "해결", "도입", "운영", "배포",
                "테스트", "모니터링", "자동화", "협업", "리딩", "관리"
        };
        int hit = 0;
        for (String w : words) {
            if (c.contains(w)) hit++;
        }
        return hit;
    }

    /*  jobRole 입력의 구분자 문자 변형을 대비해서 정규화 UI,DB,프론트에서 서로 다른 문자로 올 수 있어서 안전장치 */
    private String normalizeRole(String jobRole) {
        if (jobRole == null) return "";
        return jobRole.trim()
                .replace("·", "∙")
                .replace("ㆍ", "∙")
                .replace("•", "∙")
                .replace("/", "∙")
                .replace(" ", "");
    }

   //직군별 키워드 포함 개수 카운트
    private int countRoleKeywords(String content, String jobRole) {
        if (content == null || content.isBlank()) return 0;

        String role = normalizeRole(jobRole);
        List<String> keywords = ROLE_KEYWORDS.getOrDefault(role, List.of());
        if (keywords.isEmpty()) return 0;

        String lower = content.toLowerCase();

        int hit = 0;
        for (String k : keywords) {
            String kk = k.toLowerCase();
            if (kk.length() < 2) continue; // ✅ 오탐 방지(너무 짧은 키워드 제외)
            if (lower.contains(kk)) hit++;
        }
        return hit;
    }


    // ----------------------------- PDF -----------------------
    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("PDF 파일이 비어있습니다.");
        }
        String name = safe(file.getOriginalFilename());
        String ct = file.getContentType();

        boolean isPdf = (ct != null && ct.equalsIgnoreCase("application/pdf")) || name.toLowerCase().endsWith(".pdf");
        if (!isPdf) throw new RuntimeException("PDF 파일만 업로드 가능합니다.");

        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) throw new RuntimeException("파일 용량 제한(50MB) 초과");
    }

    private String extractTextFromPdf(MultipartFile file) throws IOException {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return text == null ? "" : text;
        }
    }

    private String limitLength(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max);
    }


    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}