package kr.it.acfourd.knda_check.schedule;

import java.util.List;
import kr.it.acfourd.knda_check.common.ClassCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitPeriodRepository extends JpaRepository<UnitPeriod, String> {

    List<UnitPeriod> findByClassCodeOrderByPeriodNo(ClassCode classCode);
}