package kr.it.acfourd.knda_check.schedule;

import java.util.List;
import kr.it.acfourd.knda_check.common.ClassCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingDayRepository extends JpaRepository<TrainingDay, Long> {

    /** 훈련일만 날짜순으로 (비훈련일 제외) */
    List<TrainingDay> findByClassCodeAndTrainingDayTrueOrderByTrainingDate(ClassCode classCode);

    long countByUnitPeriodIdAndTrainingDayTrue(String unitPeriodId);
}