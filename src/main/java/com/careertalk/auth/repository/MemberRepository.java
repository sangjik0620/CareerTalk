package com.careertalk.auth.repository;

import com.careertalk.auth.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    // 이메일로 회원 찾기 (로그인 및 중복 확인용)
    Optional<Member> findByEmail(String email);

    // 닉네임 중복 여부 확인
    Optional<Member> findByNickname(String nickname);


    List<Member> findAllByEmail(String email);
}