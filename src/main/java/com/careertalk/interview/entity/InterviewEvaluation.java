package com.careertalk.interview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity // 필수: Spring Data JPA가 관리하는 엔티티임을 명시
@Table(name = "interview_evaluation") // DB에 생성될 테이블 이름 지정
public class InterviewEvaluation {

    @Id // 필수: Primary Key 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT (DB에 따라 다를 수 있음)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "overall_score")
    private int overallScore;

    // Hibernate 6.x (Spring Boot 3.x)에서 JSON 컬럼을 매핑하는 방법
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "json")
    private JsonNode resultJson;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    // --- Getters & Setters ---
    // (Lombok의 @Getter, @Setter, @NoArgsConstructor 등을 사용하시면 아래 코드를 생략할 수 있습니다)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public JsonNode getResultJson() {
        return resultJson;
    }

    public void setResultJson(JsonNode resultJson) {
        this.resultJson = resultJson;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}