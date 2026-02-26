package com.careertalk.file.repository;

import com.careertalk.file.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    // 이메일로 회원 찾기 (로그인 및 중복 확인용)
    Optional<Member> findByEmail(String email);

    // 이메일 중복 여부 확인
    boolean existsByEmail(String email);

    // 닉네임 중복 여부 확인
    boolean existsByNickname(String nickname);

    Optional<Member> findByLoginId(String loginId);
}