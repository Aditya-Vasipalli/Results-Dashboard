package edu.vit.results.repo;

import edu.vit.results.domain.Subject;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findAllByOrderByCodeAsc();
    List<Subject> findAllByTeacherIdOrderByCodeAsc(Long teacherId);

    @Modifying
    @Query("update Subject s set s.teacher = null where s.teacher.id = :teacherId")
    void unassignTeacher(Long teacherId);
}
