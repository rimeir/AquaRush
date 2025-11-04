package com.aquarush.ticketing.category.repository;

import com.aquarush.ticketing.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * 활성화된 카테고리만 조회
     */
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    /**
     * 이름으로 검색
     */
    List<Category> findByNameContaining(String name);
}
