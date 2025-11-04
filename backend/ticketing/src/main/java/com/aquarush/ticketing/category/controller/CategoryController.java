package com.aquarush.ticketing.category.controller;

import com.aquarush.ticketing.category.entity.Category;
import com.aquarush.ticketing.category.service.CategoryService;
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
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Category", description = "카테고리 API")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 카테고리 목록 조회
     */
    @Operation(
            summary = "카테고리 목록 조회",
            description = "활성화된 카테고리 목록을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<Category>>>> getCategories() {
        log.info("GET /categories - 카테고리 목록 조회");

        List<Category> categories = categoryService.findActiveCategories();

        log.info("카테고리 조회 완료 - 총 {}개", categories.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("categories", categories))
        );
    }
}