package com.aquarush.ticketing.center.controller;

import com.aquarush.ticketing.center.entity.Center;
import com.aquarush.ticketing.center.service.CenterService;
import com.aquarush.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/centers")
@RequiredArgsConstructor
@Tag(name = "Center", description = "센터 API")
public class CenterController {

    private final CenterService centerService;

    @Operation(
            summary = "센터 목록 조회",
            description = "센터 전체 목록을 조회합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "센터 목록 조회 성공")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<Center>>>> getCenters() {
        log.info("GET /centers - 센터 목록 조회 요청");

        List<Center> centers = centerService.findAllCenters();

        log.info("센터 목록 조회 완료 - 총 {}개", centers.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("centers", centers))
        );
    }
}
