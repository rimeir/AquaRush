package com.aquarush.ticketing.category.service;

import com.aquarush.ticketing.category.entity.Category;
import com.aquarush.ticketing.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 모든 카테고리 조회
     */
    public List<Category> findAllCategories() {
        log.info("전체 카테고리 조회");
        return categoryRepository.findAll();
    }

    /**
     * 활성화된 카테고리만 조회
     */
    public List<Category> findActiveCategories() {
        log.info("활성 카테고리 조회");
        return categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }
}
