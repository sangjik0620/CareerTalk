package com.careertalk.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_num")
    private Long userNum;

    @Column(name="user_id", unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(unique = true, length = 50, nullable = false)
    private String nickname;

    @Column(unique = false, length = 255, nullable = false)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "target_job", length = 100)
    private String targetJob;

    @Builder.Default
    @Column(length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public void updateInfo(String name, String nickname, String email) {
        this.name = name;
        this.nickname = nickname;
        this.email = email;
    }
}