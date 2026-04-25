package com.sowon.enrollment.domain.course;

import com.sowon.enrollment.common.exception.BusinessException;
import com.sowon.enrollment.common.exception.CourseNotFoundException;
import com.sowon.enrollment.common.exception.UserNotFoundException;
import com.sowon.enrollment.domain.course.dto.CourseCreateRequest;
import com.sowon.enrollment.domain.course.dto.CourseResponse;
import com.sowon.enrollment.domain.course.dto.CourseStatusUpdateRequest;
import com.sowon.enrollment.domain.user.User;
import com.sowon.enrollment.domain.user.UserRepository;
import com.sowon.enrollment.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CourseResponse createCourse(String userId, CourseCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getRole() != UserRole.CREATOR) {
            throw new BusinessException("FORBIDDEN", "Only CREATOR can create courses");
        }

        Course course = Course.create(
                UUID.randomUUID().toString(),
                userId,
                request.title(),
                request.description(),
                request.price(),
                request.maxCapacity(),
                request.startDate(),
                request.endDate()
        );
        return CourseResponse.from(courseRepository.save(course));
    }

    public List<CourseResponse> getCourses(CourseStatus status) {
        List<Course> courses = (status != null)
                ? courseRepository.findByStatus(status)
                : courseRepository.findAll();
        return courses.stream().map(CourseResponse::from).toList();
    }

    public CourseResponse getCourseById(String courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return CourseResponse.from(course);
    }

    @Transactional
    public CourseResponse updateStatus(String userId, String courseId, CourseStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        if (user.getRole() != UserRole.CREATOR || !course.getCreatorId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Only the course creator can update status");
        }

        course.changeStatus(request.status());
        return CourseResponse.from(course);
    }
}
