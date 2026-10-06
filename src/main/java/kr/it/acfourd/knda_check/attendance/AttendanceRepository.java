package kr.it.acfourd.knda_check.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** 한 수강생의 출결 전체. 훈련일정을 함께 가져와 날짜별로 지연 로딩이 반복되지 않게 한다. */
    @Query("""
            select a from Attendance a
            join fetch a.trainingDay
            where a.student.id = :studentId
            order by a.trainingDay.trainingDate
            """)
    List<Attendance> findAllByStudentId(@Param("studentId") String studentId);
}