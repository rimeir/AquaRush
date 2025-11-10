package com.aquarush.ticketing.center.service;

import com.aquarush.ticketing.center.entity.Center;
import com.aquarush.ticketing.center.repository.CenterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CenterService {

    private final CenterRepository centerRepository;

    public List<Center> findAllCenters() {
        log.info("전체 센터 목록 조회");
        return centerRepository.findByIsActiveTrue();
    }
}
