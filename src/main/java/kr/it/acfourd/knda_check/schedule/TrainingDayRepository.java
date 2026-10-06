package kr.it.acfourd.knda_check.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import kr.it.acfourd.knda_check.common.ClassCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingDayRepository extends JpaRepository<TrainingDay, Long> {

    /** 훈련일만 날짜순으로 (비훈련일 제외) */
    List<TrainingDay> findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode classCode);

    long countByUnitPeriodIdAndTrainingDayTrue(String unitPeriodId);

    Optional<TrainingDay> findByClassCodeAndTrainingDate(ClassCode classCode, LocalDate trainingDate);
}