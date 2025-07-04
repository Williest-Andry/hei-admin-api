package school.hei.haapi.repository.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import school.hei.haapi.model.AwardedCourse;
import school.hei.haapi.model.Exam;
import school.hei.haapi.model.Grade;
import school.hei.haapi.model.User;
import school.hei.haapi.model.exception.NotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class GradeDao {
    private final EntityManager entityManager;
    private final ExamDao examDao;
    private final AwardedCourseDao awardedCourseDao;

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
        List<Exam> exams = new ArrayList<>();
        List<AwardedCourse> awardedCourses = awardedCourseDao.findByCriteria(null, courseId, Pageable.ofSize(10));
        awardedCourses.forEach(awardedCourse -> {
            exams.addAll(examDao.findByCriteria(Pageable.ofSize(10), null, null, groupRef,
                    null, null, awardedCourse.getId()));
        });
        if (exams.isEmpty()) {
            throw new NotFoundException("There is no exam for course id " + courseId);
        }

        return exams.stream()
                .distinct()
                .map(exam -> getGradesByExamId(exam.getId()))
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(Grade::getStudent))
                .entrySet()
                .stream()
                .map(
                        entry -> {
                            User student = entry.getKey();
                            List<Grade> studentGrades = entry.getValue();

                            double weightedSum =
                                    studentGrades.stream()
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
