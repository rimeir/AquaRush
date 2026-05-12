package com.aquarush.ticketing.reservation;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CourseRepository courseRepository;

    private Course testCourse;

    @BeforeEach
    void setUp() {
        // 테스트용 강좌 생성 (정원 20명)
        // TODO: 실제 테스트 데이터 준비
    }

    @Test
    void 동시에_100명이_예약_시도_정원_20명() throws InterruptedException {
        // given
        int threadCount = 100;
        int maxCapacity = 20;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            final long userId = i + 1;

            executor.submit(() -> {
                try {
                    ReservationCreateRequest request = ReservationCreateRequest.builder()
                            .courseId(testCourse.getId())
                            .userId(userId)
                            .userName("테스트" + userId)
                            .userPhone("010-0000-0000")
                            .build();

                    reservationService.createReservation(request);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(maxCapacity);
        assertThat(failCount.get()).isEqualTo(threadCount - maxCapacity);

        Course updatedCourse = courseRepository.findById(testCourse.getId()).orElseThrow();
        assertThat(updatedCourse.getCurrentCapacity()).isEqualTo(maxCapacity);
    }
}