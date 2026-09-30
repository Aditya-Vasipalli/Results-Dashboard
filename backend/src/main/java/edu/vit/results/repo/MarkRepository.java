package edu.vit.results.repo;

import edu.vit.results.domain.Mark;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarkRepository extends JpaRepository<Mark, Long> {
    List<Mark> findAllBySubjectId(Long subjectId);
    List<Mark> findAllByStudentId(Long studentId);

    @Modifying
    @Query("delete from Mark m where m.student.id = :studentId")
    void deleteAllForStudent(Long studentId);

    @Query("select count(m) from Mark m where m.mse is not null and m.ese is not null")
    long countComplete();
}
