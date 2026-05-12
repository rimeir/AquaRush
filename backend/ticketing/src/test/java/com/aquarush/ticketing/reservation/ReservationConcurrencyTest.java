package com.aquarush.ticketing.reservation;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.repository.ReservationRepository;
import com.aquarush.ticketing.reservation.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("예약 동시성 부하 테스트")
class ReservationConcurrencyTest {

    @Autowired private ReservationService reservationService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ReservationRepository reservationRepository;

    private static final Long TEST_COURSE_ID = 1L;
    private static final int MAX_CAPACITY = 20;

    @BeforeEach
    void setUp() {
        List<Reservation> existing = reservationRepository.findByCourseId(TEST_COURSE_ID);
        if (!existing.isEmpty()) {
            reservationRepository.deleteAll(existing);
        }
        Course course = courseRepository.findById(TEST_COURSE_ID).orElseThrow();
        course.resetCapacity();
        courseRepository.save(course);
    }

    @AfterEach
    void tearDown() {
        List<Reservation> reservations = reservationRepository.findByCourseId(TEST_COURSE_ID);
        if (!reservations.isEmpty()) {
            reservationRepository.deleteAll(reservations);
        }
        Course course = courseRepository.findById(TEST_COURSE_ID).orElseThrow();
        course.resetCapacity();
        courseRepository.save(course);
    }

    @Test
    @DisplayName("10명 동시 접속 - 정원 여유 시 전원 성공")
    void 동시에_10명_예약_전원_성공() throws InterruptedException {
        int threadCount = 10;

        long start = System.currentTimeMillis();
        TestResult result = runConcurrent(threadCount);
        long elapsed = System.currentTimeMillis() - start;

        printResult("10명", result, elapsed);

        assertThat(result.success()).isEqualTo(threadCount);
        assertThat(result.fail()).isEqualTo(0);

        Course course = courseRepository.findById(TEST_COURSE_ID).orElseThrow();
        assertThat(course.getCurrentCapacity()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("100명 동시 접속 - 정확히 정원(20명)만 성공, 초과 없음")
    void 동시에_100명_예약_정원_초과_없음() throws InterruptedException {
        int threadCount = 100;

        long start = System.currentTimeMillis();
        TestResult result = runConcurrent(threadCount);
        long elapsed = System.currentTimeMillis() - start;

        printResult("100명", result, elapsed);

        assertThat(result.success()).isEqualTo(MAX_CAPACITY);
        assertThat(result.fail()).isEqualTo(threadCount - MAX_CAPACITY);

        Course course = courseRepository.findById(TEST_COURSE_ID).orElseThrow();
        assertThat(course.getCurrentCapacity()).isEqualTo(MAX_CAPACITY);
        assertThat(course.getCurrentCapacity()).isLessThanOrEqualTo(course.getMaxCapacity());
    }

    @Test
    @DisplayName("1000명 동시 접속 - 정확히 정원(20명)만 성공, 초과 없음")
    void 동시에_1000명_예약_정원_초과_없음() throws InterruptedException {
        int threadCount = 1000;

        long start = System.currentTimeMillis();
        TestResult result = runConcurrent(threadCount);
        long elapsed = System.currentTimeMillis() - start;

        printResult("1000명", result, elapsed);

        assertThat(result.success()).isEqualTo(MAX_CAPACITY);
        assertThat(result.fail()).isEqualTo(threadCount - MAX_CAPACITY);

        Course course = courseRepository.findById(TEST_COURSE_ID).orElseThrow();
        assertThat(course.getCurrentCapacity()).isEqualTo(MAX_CAPACITY);
        assertThat(course.getCurrentCapacity()).isLessThanOrEqualTo(course.getMaxCapacity());
    }

    private TestResult runConcurrent(int threadCount) throws InterruptedException {
        int poolSize = Math.min(threadCount, 200);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long userId = 100_000L + i;
            executor.submit(() -> {
                try {
                    reservationService.createReservation(
                            ReservationCreateRequest.builder()
                                    .courseId(TEST_COURSE_ID)
                                    .userId(userId)
                                    .userName("부하테스트유저" + userId)
                                    .userPhone("010-0000-0000")
                                    .build()
                    );
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        return new TestResult(success.get(), fail.get());
    }

    private void printResult(String label, TestResult result, long elapsedMs) {
        System.out.printf(
                "%n[부하 테스트 결과 - %s]%n  성공: %d명 / 실패: %d명 / 소요시간: %dms (%.2f초)%n",
                label, result.success(), result.fail(), elapsedMs, elapsedMs / 1000.0
        );
    }

    record TestResult(int success, int fail) {}
}
