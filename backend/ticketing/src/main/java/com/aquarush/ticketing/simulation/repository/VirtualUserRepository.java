package com.aquarush.ticketing.simulation.repository;

import com.aquarush.ticketing.simulation.entity.VirtualUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * VirtualUser 리포지토리
 *
 * ⭐ 개선 사항:
 * - deleteByIsBot() 메서드 추가 (모든 봇 삭제)
 * - findByExpiresAtBefore() 메서드 (만료된 사용자 조회)
 * - countByIsBot() 메서드 (봇 개수 조회)
 */
public interface VirtualUserRepository extends JpaRepository<VirtualUser, Long> {

    /**
     * 세션 ID로 조회
     *
     * @param sessionId 세션 ID
     * @return VirtualUser (Optional)
     */
    Optional<VirtualUser> findBySessionId(String sessionId);

    /**
     * ⭐ 봇 삭제 (isBot = true인 모든 레코드)
     *
     * @param isBot true
     * @return 삭제된 개수
     *
     * 생성되는 SQL:
     * DELETE FROM virtual_users WHERE is_bot = ?
     *
     * 사용 예시:
     * int count = virtualUserRepository.deleteByIsBot(true);
     * // 모든 봇 삭제, count = 삭제된 개수
     */
    @Modifying
    @Query("DELETE FROM VirtualUser v WHERE v.isBot = :isBot")
    int deleteByIsBot(@Param("isBot") boolean isBot);

    /**
     * 만료 시간 이전 사용자 조회
     *
     * @param dateTime 기준 시간
     * @return 만료된 사용자 리스트
     *
     * 생성되는 SQL:
     * SELECT * FROM virtual_users WHERE expires_at < ?
     *
     * 사용 예시:
     * LocalDateTime now = LocalDateTime.now();
     * List<VirtualUser> expired = repository.findByExpiresAtBefore(now);
     */
    List<VirtualUser> findByExpiresAtBefore(LocalDateTime dateTime);

    /**
     * 봇 개수 조회
     *
     * @param isBot true (봇), false (실제 사용자)
     * @return 개수
     *
     * 생성되는 SQL:
     * SELECT COUNT(*) FROM virtual_users WHERE is_bot = ?
     *
     * 사용 예시:
     * long botCount = repository.countByIsBot(true);
     * long realUserCount = repository.countByIsBot(false);
     */
    long countByIsBot(boolean isBot);
}