package com.aquarush.ticketing.simulation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 가상 사용자 엔티티
 *
 * 역할:
 * 1. 시뮬레이션용 임시 사용자 표현
 * 2. 실제 회원과 분리하여 관리
 * 3. 세션 기반 식별 (JWT 아님)
 */
@Entity
@Table(name = "virtual_users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VirtualUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 세션 ID (UUID)
     * 예: "550e8400-e29b-41d4-a716-446655440000"
     *
     * 왜 UUID를 사용하는가?
     * - 전 세계적으로 고유한 ID
     * - 충돌 가능성 거의 없음 (340조의 340조의 340조 개)
     * - 순차적이지 않아 예측 불가 (보안)
     */
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;

    /**
     * 닉네임
     * 예: "티켓팅마스터", "빠른손123", "bot-472"
     *
     * 랜덤 생성 규칙:
     * - 실제 유저: 사용자 입력 or "익명유저_{랜덤번호}"
     * - 봇: "bot-{번호}"
     */
    @Column(nullable = false, length = 50)
    private String nickname;

    /**
     * 봇 여부
     * true: 자동화된 봇
     * false: 실제 참가자
     */
    @Column(nullable = false)
    private boolean isBot;

    /**
     * 생성 시간
     * 자동으로 현재 시간 저장
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 만료 시간
     * 예: 생성 후 1시간 뒤
     *
     * 왜 만료 시간이 필요한가?
     * - 시뮬레이션 종료 후 자동 정리
     * - 메모리 낭비 방지
     * - 스케줄러가 주기적으로 삭제
     */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 생성 전 자동 실행
     *
     * @PrePersist란?
     * - JPA 라이프사이클 어노테이션
     * - INSERT 직전에 자동 실행
     * - 초기값 설정에 사용
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();

        // 만료 시간 설정 안 되어 있으면 기본값 (1시간 후)
        if (this.expiresAt == null) {
            this.expiresAt = LocalDateTime.now().plusHours(1);
        }
    }

    /**
     * 만료 여부 확인
     *
     * @return true: 만료됨, false: 유효함
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 남은 시간 (초)
     *
     * @return 양수: 남은 시간, 음수: 만료
     */
    public long getRemainingSeconds() {
        return java.time.Duration.between(
                LocalDateTime.now(),
                expiresAt
        ).getSeconds();
    }
}
