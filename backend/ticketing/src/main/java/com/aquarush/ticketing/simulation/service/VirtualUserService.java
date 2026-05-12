package com.aquarush.ticketing.simulation.service;

import com.aquarush.ticketing.simulation.entity.VirtualUser;
import com.aquarush.ticketing.simulation.repository.VirtualUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 가상 사용자 관리 서비스
 *
 * ⭐ 개선 사항:
 * - 봇 삭제 메서드 추가 (deleteBots)
 * - 모든 봇 삭제 메서드 추가 (deleteAllBots)
 * - 만료된 사용자 자동 정리 스케줄러
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VirtualUserService {

    private final VirtualUserRepository virtualUserRepository;

    /**
     * 실제 참가자 생성 (닉네임만)
     */
    @Transactional
    public VirtualUser createVirtualUser(String nickname) {
        String sessionId = UUID.randomUUID().toString();
        return createVirtualUser(sessionId, nickname);
    }

    /**
     * 실제 참가자 생성 (세션 ID + 닉네임)
     */
    @Transactional
    public VirtualUser createVirtualUser(String sessionId, String nickname) {
        log.debug("가상 사용자 생성: sessionId={}, nickname={}", sessionId, nickname);

        String finalNickname = (nickname != null && !nickname.isBlank())
                ? nickname
                : generateRandomNickname();

        VirtualUser user = VirtualUser.builder()
                .sessionId(sessionId)
                .nickname(finalNickname)
                .isBot(false)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        VirtualUser savedUser = virtualUserRepository.save(user);

        log.info("가상 사용자 생성 완료: id={}, sessionId={}, nickname={}",
                savedUser.getId(), savedUser.getSessionId(), savedUser.getNickname());

        return savedUser;
    }

    /**
     * 봇 생성
     */
    @Transactional
    public VirtualUser createBot(int botNumber) {
        String sessionId = UUID.randomUUID().toString();
        String botNickname = "bot-" + botNumber;

        VirtualUser bot = VirtualUser.builder()
                .sessionId(sessionId)
                .nickname(botNickname)
                .isBot(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        return virtualUserRepository.save(bot);
    }

    /**
     * ⭐ 봇 삭제 (시뮬레이션 종료 시)
     *
     * @param bots 삭제할 봇 리스트
     *
     * 사용 시점:
     * - 시뮬레이션 완료 후
     * - finally 블록에서 호출
     * - 성공/실패 관계없이 항상 실행
     *
     * 특징:
     * - 배치 삭제 (한 번의 쿼리)
     * - null 체크
     * - 에러 발생 시에도 로그만 출력
     */
    @Transactional
    public void deleteBots(List<VirtualUser> bots) {
        if (bots == null || bots.isEmpty()) {
            log.warn("삭제할 봇이 없습니다");
            return;
        }

        try {
            log.info("🗑️ 봇 삭제 시작: count={}", bots.size());

            // ID 리스트 추출 (null 필터링)
            List<Long> botIds = bots.stream()
                    .map(VirtualUser::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());

            if (botIds.isEmpty()) {
                log.warn("유효한 봇 ID가 없습니다");
                return;
            }

            // 배치 삭제 (한 번의 DELETE 쿼리)
            // DELETE FROM virtual_users WHERE id IN (1, 2, 3, ...)
            virtualUserRepository.deleteAllByIdInBatch(botIds);

            log.info("✅ 봇 삭제 완료: count={}", botIds.size());

        } catch (Exception e) {
            log.error("❌ 봇 삭제 중 오류 발생", e);
            // 에러 발생해도 예외를 던지지 않음
            // 시뮬레이션은 계속 진행
        }
    }

    /**
     * ⭐ 모든 봇 삭제 (관리자 기능)
     *
     * @return 삭제된 봇 개수
     *
     * 사용 시점:
     * - 관리자가 수동으로 정리할 때
     * - DB를 깔끔하게 유지하고 싶을 때
     * - 테스트 후 정리
     *
     * 특징:
     * - isBot = true인 모든 레코드 삭제
     * - 실제 사용자는 유지
     * - 삭제된 개수 반환
     */
    @Transactional
    public int deleteAllBots() {
        log.info("🗑️ 모든 봇 삭제 시작");

        try {
            // DELETE FROM virtual_users WHERE is_bot = true
            int deletedCount = virtualUserRepository.deleteByIsBot(true);

            log.info("✅ 모든 봇 삭제 완료: count={}", deletedCount);

            return deletedCount;

        } catch (Exception e) {
            log.error("❌ 봇 삭제 중 오류 발생", e);
            return 0;
        }
    }

    /**
     * ⭐ 만료된 사용자 자동 정리 (스케줄러)
     *
     * 실행 주기: 매 시간 정각 (예: 1시, 2시, 3시...)
     *
     * 목적:
     * - 1시간 이상 지난 사용자 자동 삭제
     * - DB 용량 관리
     * - 예외 상황 대비 (서버 종료 등)
     *
     * 삭제 대상:
     * - expiresAt < 현재 시간
     * - 봇 + 실제 사용자 모두
     *
     * 안전장치:
     * - 시뮬레이션 종료 시 즉시 삭제
     * - 스케줄러는 혹시 남아있을 경우 대비
     */
    @Scheduled(cron = "0 0 * * * *")  // 매 시간 정각
    @Transactional
    public void deleteExpiredUsers() {
        log.info("🧹 만료된 가상 사용자 정리 시작");

        try {
            LocalDateTime now = LocalDateTime.now();

            // 만료된 사용자 조회
            List<VirtualUser> expiredUsers =
                    virtualUserRepository.findByExpiresAtBefore(now);

            if (expiredUsers.isEmpty()) {
                log.debug("만료된 사용자 없음");
                return;
            }

            // 삭제
            virtualUserRepository.deleteAll(expiredUsers);

            log.info("✅ 만료된 가상 사용자 정리 완료: count={}", expiredUsers.size());

        } catch (Exception e) {
            log.error("❌ 가상 사용자 정리 중 오류 발생", e);
        }
    }

    /**
     * 랜덤 닉네임 생성
     *
     * 형식: "익명유저_0123"
     */
    private String generateRandomNickname() {
        int random = (int) (Math.random() * 10000);
        return String.format("익명유저_%04d", random);
    }

    /**
     * 세션 ID로 조회
     */
    public VirtualUser getBySessionId(String sessionId) {
        return virtualUserRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "세션을 찾을 수 없습니다: " + sessionId));
    }

    /**
     * 현재 활성 사용자 수 조회
     */
    public long countActiveUsers() {
        return virtualUserRepository.count();
    }

    /**
     * 현재 활성 봇 수 조회
     */
    public long countActiveBots() {
        return virtualUserRepository.countByIsBot(true);
    }

    /**
     * 현재 활성 실제 사용자 수 조회
     */
    public long countActiveRealUsers() {
        return countActiveUsers() - countActiveBots();
    }
}