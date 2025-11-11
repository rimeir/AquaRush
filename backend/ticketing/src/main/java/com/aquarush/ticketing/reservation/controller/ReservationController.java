package com.aquarush.ticketing.reservation.controller;

import com.aquarush.ticketing.global.dto.ApiResponse;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationDetailResponse;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservation", description = "예약 API")
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 예약 생성
     */
    @Operation(
            summary = "예약 생성",
            description = "새로운 예약을 생성합니다. 강좌 예약 가능 여부와 중복 예약을 확인합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        log.info("POST /api/v1/reservations - 예약 생성 요청");
        log.info("courseId: {}, userId: {}, userName: {}",
                request.getCourseId(), request.getUserId(), request.getUserName());

        ReservationResponse reservation = reservationService.createReservation(request);

        return ResponseEntity.ok(
                ApiResponse.success(reservation, "예약이 생성되었습니다.")
        );
    }

    /**
     * 예약 상세 조회
     */
    @Operation(
            summary = "예약 상세 조회",
            description = "예약 ID로 예약 상세 정보를 조회합니다. 강좌 정보와 예약자 정보를 함께 반환합니다."
    )
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationDetailResponse>> getReservationDetail(
            @PathVariable Long reservationId
    ) {
        log.info("GET /api/v1/reservations/{} - 예약 상세 조회", reservationId);

        ReservationDetailResponse reservation =
                reservationService.getReservationDetail(reservationId);

        return ResponseEntity.ok(ApiResponse.success(reservation));
    }

    /**
     * 내 예약 목록 조회
     */
    @Operation(
            summary = "내 예약 목록",
            description = "사용자의 모든 예약 목록을 조회합니다. 최근 예약 순으로 정렬됩니다."
    )
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Map<String, List<ReservationResponse>>>> getMyReservations(
            @Parameter(description = "사용자 ID", example = "1", required = true)
            @RequestParam Long userId
    ) {
        log.info("GET /api/v1/reservations/my - userId: {}", userId);

        List<ReservationResponse> reservations =
                reservationService.getMyReservations(userId);

        log.info("조회 결과: {}개", reservations.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("reservations", reservations))
        );
    }

    /**
     * 강좌별 예약 목록 조회
     */
    @Operation(
            summary = "강좌별 예약 목록",
            description = "특정 강좌의 모든 예약 목록을 조회합니다. 관리자가 강좌별 예약 현황을 파악하는데 사용합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<ReservationResponse>>>> getCourseReservations(
            @Parameter(description = "강좌 ID", example = "1", required = true)
            @RequestParam Long courseId
    ) {
        log.info("GET /api/v1/reservations?courseId={}", courseId);

        List<ReservationResponse> reservations =
                reservationService.getCourseReservations(courseId);

        log.info("조회 결과: {}개", reservations.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("reservations", reservations))
        );
    }

    /**
     * 예약 취소
     */
    @Operation(
            summary = "예약 취소",
            description = "예약을 취소합니다. 취소 사유를 선택적으로 입력할 수 있습니다."
    )
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<Void>> cancelReservation(
            @PathVariable Long reservationId,
            @Parameter(description = "취소 사유", example = "일정 변경")
            @RequestParam(required = false) String reason
    ) {
        log.info("DELETE /api/v1/reservations/{} - 예약 취소", reservationId);
        log.info("취소 사유: {}", reason);

        reservationService.cancelReservation(reservationId, reason);

        return ResponseEntity.ok(
                ApiResponse.success(null, "예약이 취소되었습니다.")
        );
    }
}
