package com.aquarush.ticketing.reservation.controller;

import com.aquarush.ticketing.global.dto.ApiResponse;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 예약 관리 컨트롤러
 *
 * 제공 기능:
 * 1. 예약 생성 (POST /api/v1/reservations)
 * 2. 예약 상세 조회 (GET /api/v1/reservations/{id})
 * 3. 내 예약 목록 (GET /api/v1/reservations/my)
 * 4. 강좌별 예약 목록 (GET /api/v1/reservations/course/{courseId})
 * 5. 예약 취소 (DELETE /api/v1/reservations/{id})
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservation", description = "예약 API")
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 예약 생성
     *
     * POST /api/v1/reservations
     *
     * Request Body:
     * {
     *   "courseId": 1,
     *   "userId": 123,
     *   "userName": "홍길동",
     *   "userPhone": "010-1234-5678"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "data": {
     *     "id": 1,
     *     "courseId": 1,
     *     "courseName": "수영 초급반",
     *     "status": "CONFIRMED",
     *     ...
     *   },
     *   "message": "예약이 완료되었습니다."
     * }
     */
    @Operation(
            summary = "예약 생성",
            description = "강좌를 예약합니다. 분산 락을 사용하여 동시성을 제어합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody ReservationCreateRequest request) {

        log.info("POST /api/v1/reservations - 예약 생성");
        log.info("courseId={}, userId={}, userName={}",
                request.getCourseId(), request.getUserId(), request.getUserName());

        ReservationResponse response = reservationService.createReservation(request);

        return ResponseEntity.ok(
                ApiResponse.success(response, "예약이 완료되었습니다.")
        );
    }

    /**
     * 예약 상세 조회
     *
     * GET /api/v1/reservations/{reservationId}
     *
     * ⭐ 수정: getReservationDetail → getReservation
     */
    @Operation(
            summary = "예약 상세 조회",
            description = "예약 ID로 상세 정보를 조회합니다."
    )
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(
            @PathVariable Long reservationId) {

        log.info("GET /api/v1/reservations/{} - 예약 조회", reservationId);

        // ✅ 수정: getReservationDetail → getReservation
        ReservationResponse response = reservationService.getReservation(reservationId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 예약 목록 조회
     *
     * GET /api/v1/reservations/my?userId=123
     *
     * 실무에서는:
     * - @AuthenticationPrincipal로 현재 로그인 사용자 정보 가져오기
     * - userId를 파라미터로 받지 않음
     */
    @Operation(
            summary = "내 예약 목록",
            description = "로그인한 사용자의 예약 목록을 조회합니다."
    )
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getMyReservations(
            @RequestParam Long userId) {

        log.info("GET /api/v1/reservations/my?userId={} - 내 예약 목록", userId);

        List<ReservationResponse> responses = reservationService.getMyReservations(userId);

        return ResponseEntity.ok(
                ApiResponse.success(responses,
                        responses.size() + "건의 예약이 조회되었습니다.")
        );
    }

    /**
     * 강좌별 예약 목록 조회
     *
     * GET /api/v1/reservations/course/{courseId}
     *
     * 관리자 기능
     */
    @Operation(
            summary = "강좌별 예약 목록",
            description = "특정 강좌의 모든 예약을 조회합니다. (관리자 기능)"
    )
    @GetMapping("/course/{courseId}")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> getCourseReservations(
            @PathVariable Long courseId) {

        log.info("GET /api/v1/reservations/course/{} - 강좌별 예약 목록", courseId);

        List<ReservationResponse> responses =
                reservationService.getCourseReservations(courseId);

        return ResponseEntity.ok(
                ApiResponse.success(responses,
                        responses.size() + "건의 예약이 조회되었습니다.")
        );
    }

    /**
     * 예약 취소
     *
     * DELETE /api/v1/reservations/{reservationId}?reason=개인사정
     */
    @Operation(
            summary = "예약 취소",
            description = "예약을 취소합니다. 취소 사유는 선택사항입니다."
    )
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @PathVariable Long reservationId,
            @RequestParam(required = false) String reason) {

        log.info("DELETE /api/v1/reservations/{} - 예약 취소", reservationId);
        log.info("reason={}", reason);

        reservationService.cancelReservation(reservationId, reason);

        return ResponseEntity.ok(
                ApiResponse.success(null, "예약이 취소되었습니다.")
        );
    }
}