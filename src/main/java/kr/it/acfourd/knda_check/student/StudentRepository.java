package kr.it.acfourd.knda_check.student;

import java.util.List;
import java.util.Optional;
import kr.it.acfourd.knda_check.common.ClassCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByClassCodeOrderById(ClassCode classCode);

    Optional<Student> findByEmailIgnoreCase(String email);
}