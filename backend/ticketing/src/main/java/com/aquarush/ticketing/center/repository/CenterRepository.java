package com.aquarush.ticketing.center.repository;

import com.aquarush.ticketing.center.entity.Center;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CenterRepository extends JpaRepository<Center, Long> {

    // 활성화된 센터만 조회
    List<Center> findByIsActiveTrue();
}
