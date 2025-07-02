package school.hei.haapi.repository.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import school.hei.haapi.endpoint.rest.model.Student;
import school.hei.haapi.model.StudentGrade;
import school.hei.haapi.model.Grade;
import school.hei.haapi.model.User;
import school.hei.haapi.model.exception.NotFoundException;

@Repository
@AllArgsConstructor
public class GradeDao {
    private final EntityManager entityManager;
    private final ExamDao examDao;

    public List<Grade> getGradesByExamId(String examId, @NotNull Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Grade> query = builder.createQuery(Grade.class);
        Root<Grade> root = query.from(Grade.class);

        query.where(builder.equal(root.get("exam").get("id"), examId));

        if (pageable.isUnpaged()) {
            return entityManager.createQuery(query).getResultList();
        }

        return entityManager
                .createQuery(query)
                .setFirstResult((pageable.getPageNumber()) * pageable.getPageSize())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    public List<Grade> getGradesByExamId(String examId) {
        return getGradesByExamId(examId, Pageable.unpaged());
    }

    public List<Grade> getFinalGradesByCourseId(String courseId, String groupRef) {
        var allCourseExams = examDao.findByCriteria(
                Pageable.unpaged(),
                null, null, null, null, null,
                courseId);

        if (allCourseExams.isEmpty()) {
            throw new NotFoundException("There is no exam for course id " + courseId);
        }

        return allCourseExams
                .stream()
                .map(exam -> getGradesByExamId(exam.getId()))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        Grade::getStudent
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    User student = entry.getKey();
                    List<Grade> studentGrades = entry.getValue();

                    double weightedSum = studentGrades.stream()
                            .mapToDouble(g -> g.getScore() * g.getExam().getCoefficient())
                            .sum();

                    long examCount = studentGrades.size(); // or distinct exam count if needed

                    double finalScore = examCount == 0 ? 0 : weightedSum / examCount;

                    Grade finalGrade = new Grade();
                    finalGrade.setStudent(student);
                    finalGrade.setScore(finalScore);
                    finalGrade.setExam(null); // not needed

                    return finalGrade;
                })
                .collect(Collectors.toList());
    }
}
