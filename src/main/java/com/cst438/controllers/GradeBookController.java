package com.cst438.controllers;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.cst438.domain.Assignment;
import com.cst438.domain.AssignmentListDTO;
import com.cst438.domain.AssignmentGrade;
import com.cst438.domain.AssignmentGradeRepository;
import com.cst438.domain.AssignmentRepository;
import com.cst438.domain.Course;
import com.cst438.domain.CourseDTOG;
import com.cst438.domain.CourseRepository;
import com.cst438.domain.Enrollment;
import com.cst438.domain.EnrollmentRepository;
import com.cst438.domain.GradebookDTO;
import com.cst438.domain.AssignmentListDTO.AssignmentDTO;
import com.cst438.services.RegistrationService;
import com.cst438.services.RegistrationServiceMQ;
import com.cst438.services.RegistrationServiceREST;

//@RestController
//@CrossOrigin(origins = {"http://localhost:3000","https://cst438-gradebook-fe.herokuapp.com"})
@RestController
@CrossOrigin(origins = {"http://localhost:3000","http://localhost:3001"})
public class GradeBookController {
	
	@Autowired
	AssignmentRepository assignmentRepository;
	
	@Autowired
	AssignmentGradeRepository assignmentGradeRepository;
	
	@Autowired
	CourseRepository courseRepository;
	
	@Autowired
	EnrollmentRepository enrollmentRepository;
	
	@Autowired
	RegistrationService registrationService;
	
	// get assignments for an instructor that need grading
	@GetMapping("/gradebook")
	public AssignmentListDTO getAssignmentsNeedGrading(@AuthenticationPrincipal OAuth2User principal) {
		
		String email = principal.getAttribute("email");
		boolean instructor = false;
		
		// determine if user is an instructor or not
		Iterable<Course> courses = courseRepository.findAll();
		for (Course c : courses) {
			if (c.getInstructor().equals(email)) {
				instructor = true;
				continue;
			}
		}
		
		// if the user is an instructor, show all of the assignments for their courses
		// that need grading
		if (instructor) {
			List<Assignment> assignments = assignmentRepository.findNeedGradingByEmail(email);
			
			AssignmentListDTO result = new AssignmentListDTO();
			for (Assignment a: assignments) {
				result.assignments.add(new AssignmentListDTO.AssignmentDTO(a.getId(), a.getCourse().getCourse_id(), a.getName(), a.getDueDate().toString() , a.getCourse().getTitle()));
			}
			
			return result;
		}
		
		// otherwise, if the user is not an instructor, show the assignments for all of the 
		// courses they are enrolled in (if any)
		else {
			Iterable<Enrollment> enrollments = enrollmentRepository.findAll();
			List<Course> coursesEnrolled = new ArrayList<>();
			List<Assignment> assignments = new ArrayList<>();
			
			// determine which courses the user is enrolled in
			for (Enrollment e : enrollments) {
				if (e.getStudentEmail().equals(email)) {
					coursesEnrolled.add(e.getCourse());
				}
			}
			
			// get all of the assignments for each course the user is enrolled in
			for (Course c : coursesEnrolled) {
				assignments.addAll(c.getAssignments());
			}
			
			// add these assignments to result and return
			AssignmentListDTO result = new AssignmentListDTO();
			for (Assignment a : assignments) {
				result.assignments.add(new AssignmentListDTO.AssignmentDTO(a.getId(), a.getCourse().getCourse_id(), a.getName(), a.getDueDate().toString() , a.getCourse().getTitle()));
			}
			
			return result;
		}
	}
	
	@GetMapping("/gradebook/{id}")
	public GradebookDTO getGradebook(@PathVariable("id") Integer assignmentId, @AuthenticationPrincipal OAuth2User principal) {
		
		String email = principal.getAttribute("email");
		
		Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
		if (assignment.equals(null)) {
			throw new ResponseStatusException( HttpStatus.UNAUTHORIZED, "Invalid Assignment Id");
		}
		
		// if the user is the instructor of the course that the assignment belongs to
		// then show the assignment grades for every student enrolled in the course
		if (assignment.getCourse().getInstructor().equals(email)) {
			// get the enrollment for the course
			//  for each student, get the current grade for assignment, 
			//   if the student does not have a current grade, create an empty grade
			GradebookDTO gradebook = new GradebookDTO();
			gradebook.assignmentId= assignmentId;
			gradebook.assignmentName = assignment.getName();
			for (Enrollment e : assignment.getCourse().getEnrollments()) {
				GradebookDTO.Grade grade = new GradebookDTO.Grade();
				grade.name = e.getStudentName();
				grade.email = e.getStudentEmail();
				// does student have a grade for this assignment
				AssignmentGrade ag = assignmentGradeRepository.findByAssignmentIdAndStudentEmail(assignmentId,  grade.email);
				if (ag != null) {
					grade.grade = ag.getScore();
					grade.assignmentGradeId = ag.getId();
				} else {
					grade.grade = "";
					AssignmentGrade agNew = new AssignmentGrade(assignment, e);
					agNew = assignmentGradeRepository.save(agNew);
					grade.assignmentGradeId = agNew.getId();  // key value generated by database on save.
				}
				gradebook.grades.add(grade);
			}
			return gradebook;
		}
		
		// otherwise if the user is not an instructor, then only show the user's assignment grades
		// for the assignments of the courses that the user is enrolled in
		else {
			GradebookDTO gradebook = new GradebookDTO();
			gradebook.assignmentId= assignmentId;
			gradebook.assignmentName = assignment.getName();
			
			for (Enrollment e : assignment.getCourse().getEnrollments()) {
				GradebookDTO.Grade grade = new GradebookDTO.Grade();
				grade.name = e.getStudentName();
				grade.email = e.getStudentEmail();
				// does student have a grade for this assignment
				AssignmentGrade ag = assignmentGradeRepository.findByAssignmentIdAndStudentEmail(assignmentId,  grade.email);
				if (ag != null && e == ag.getStudentEnrollment()) {
					grade.grade = ag.getScore();
					grade.assignmentGradeId = ag.getId();
					gradebook.grades.add(grade);
				}
			}
			
			return gradebook;
		}
	}
	
	@PostMapping("/course/{course_id}/finalgrades")
	@Transactional
	public void calcFinalGrades(@PathVariable int course_id, @AuthenticationPrincipal OAuth2User principal) {
		System.out.println("Gradebook - calcFinalGrades for course " + course_id);
		
		// check that this request is from the course instructor 
		String email = principal.getAttribute("email"); 
		
		Course c = courseRepository.findById(course_id).orElse(null);
		if (!c.getInstructor().equals(email)) {
			throw new ResponseStatusException( HttpStatus.UNAUTHORIZED, "Not Authorized. " );
		}
		
		CourseDTOG cdto = new CourseDTOG();
		cdto.course_id = course_id;
		cdto.grades = new ArrayList<>();
		for (Enrollment e: c.getEnrollments()) {
			double total=0.0;
			int count = 0;
			for (AssignmentGrade ag : e.getAssignmentGrades()) {
				count++;
				total = total + Double.parseDouble(ag.getScore());
			}
			double average = total/count;
			CourseDTOG.GradeDTO gdto = new CourseDTOG.GradeDTO();
			gdto.grade=letterGrade(average);
			gdto.student_email=e.getStudentEmail();
			gdto.student_name=e.getStudentName();
			cdto.grades.add(gdto);
			System.out.println("Course="+course_id+" Student="+e.getStudentEmail()+" grade="+gdto.grade);
		}
		registrationService.sendFinalGrades(course_id, cdto);
	}
	
	private String letterGrade(double grade) {
		if (grade >= 90) return "A";
		if (grade >= 80) return "B";
		if (grade >= 70) return "C";
		if (grade >= 60) return "D";
		return "F";
	}
	
	@PutMapping("/gradebook/{id}")
	@Transactional
	public void updateGradebook (@RequestBody GradebookDTO gradebook, @PathVariable("id") Integer assignmentId, @AuthenticationPrincipal OAuth2User principal) {
		
		String email = principal.getAttribute("email");
		checkAssignment(assignmentId, email);  // check that user name matches instructor email of the course.
		
		// for each grade in gradebook, update the assignment grade in database 
		System.out.printf("%d %s %d\n",  gradebook.assignmentId, gradebook.assignmentName, gradebook.grades.size());
		
		for (GradebookDTO.Grade g : gradebook.grades) {
			System.out.printf("%s\n", g.toString());
			AssignmentGrade ag = assignmentGradeRepository.findById(g.assignmentGradeId).orElse(null);
			if (ag == null) {
				throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Invalid grade primary key. "+g.assignmentGradeId);
			}
			ag.setScore(g.grade);
			System.out.printf("%s\n", ag.toString());
			
			assignmentGradeRepository.save(ag);
		}
		
	}
	
	private Assignment checkAssignment(int assignmentId, String email) {
		// get assignment 
		Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
		if (assignment == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Assignment not found. "+assignmentId );
		}
		// check that user is the course instructor
		if (!assignment.getCourse().getInstructor().equals(email)) {
			throw new ResponseStatusException( HttpStatus.UNAUTHORIZED, "Not Authorized. " );
		}
		
		return assignment;
	}
	
	@PostMapping("/assignment")
	@Transactional
	public AssignmentListDTO.AssignmentDTO addAssignment(@RequestBody AssignmentListDTO.AssignmentDTO assignmentDTO, @AuthenticationPrincipal OAuth2User principal) {
		String email = principal.getAttribute("email"); 
		
		// if assignmentDTO data is empty
		if (assignmentDTO == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Assignment is null.");
		}
		// if assignmentDTO assignment name is invalid
		if (assignmentDTO.assignmentName == null || assignmentDTO.assignmentName.trim().isEmpty()) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Assignment name is empty");
		}
		
		// get course from assignmentDTO courseId
		Course course = courseRepository.findById(assignmentDTO.courseId).orElse(null);
		
		// if the courseId doesn't exist
		if (course == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Course not found. "+ assignmentDTO.courseId );
		}
		// if the course instructor does not match the email
		if (!course.getInstructor().equals(email)) {
			throw new ResponseStatusException( HttpStatus.UNAUTHORIZED, "Not Authorized.");
		}
		
		// TODO: check validity of due date
		
		// otherwise, create new assignment
		Assignment assignment = new Assignment();
		assignment.setName(assignmentDTO.assignmentName);
		assignment.setCourse(course);
		assignment.setNeedsGrading(1);
		assignment.setDueDate(java.sql.Date.valueOf(assignmentDTO.dueDate));
		
		Assignment savedAssignment = assignmentRepository.save(assignment);
		
		AssignmentListDTO.AssignmentDTO result = createAssignmentDTO(savedAssignment);
		return result;
	}
	
	private AssignmentListDTO.AssignmentDTO createAssignmentDTO(Assignment a) {
		Course c = a.getCourse();
		AssignmentListDTO.AssignmentDTO assignmentDTO = new AssignmentListDTO.AssignmentDTO();
		assignmentDTO.assignmentId = a.getId();
		assignmentDTO.assignmentName = a.getName();
		assignmentDTO.courseId = c.getCourse_id();
		assignmentDTO.courseTitle = c.getTitle();
		assignmentDTO.dueDate = a.getDueDate().toString();
		return assignmentDTO;
	}
	
	// get assignment from assignment id
	@GetMapping("/assignment/{id}")
	public AssignmentListDTO.AssignmentDTO getAssignment(@PathVariable("id") Integer assignmentId, @AuthenticationPrincipal OAuth2User principal) {
		String email = principal.getAttribute("email");
		
		Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
		if (assignment == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Invalid assignment.");
		}
		
		if (assignment.getCourse().getInstructor().equals(email)) {
			AssignmentListDTO.AssignmentDTO assignmentDTO = createAssignmentDTO(assignment);
			return assignmentDTO;
		}
	
		List<Enrollment> enrollment = assignment.getCourse().getEnrollments();
		for (Enrollment e : enrollment) {
			if (e.getStudentEmail().equals(email)) {
				AssignmentListDTO.AssignmentDTO assignmentDTO = createAssignmentDTO(assignment);
				return assignmentDTO;
			}
		}
		
		throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Not Authorized");
	}
	
	
	@PutMapping("/assignment/{id}")
	@Transactional
	public void updateAssignmentName (@RequestBody AssignmentListDTO.AssignmentDTO assignmentDTO, @PathVariable("id") Integer assignmentId, @AuthenticationPrincipal OAuth2User principal) {
		
		String email = principal.getAttribute("email"); 
		checkAssignment(assignmentId, email);  // check that user name matches instructor email of the course.
		
		Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
		
		if (assignment == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Invalid assignment.");
		}
		
		assignment.setName(assignmentDTO.assignmentName);
		assignmentRepository.save(assignment);
	}
	
	@DeleteMapping("/assignment/{id}")
	@Transactional
	public void dropAssignment(@PathVariable("id") Integer assignmentId, @AuthenticationPrincipal OAuth2User principal) {
		
		String email = principal.getAttribute("email");
		
		Assignment assignment = checkAssignment(assignmentId, email);
		
		if (assignment == null) {
			throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Assignment doesn't exist.");
		}
		
		GradebookDTO gradebook = new GradebookDTO();
		gradebook.assignmentId = assignmentId;
		gradebook.assignmentName = assignment.getName();
		
		for (Enrollment e : assignment.getCourse().getEnrollments()) {
			GradebookDTO.Grade grade = new GradebookDTO.Grade();
			grade.name = e.getStudentName();
			grade.email = e.getStudentName();
			
			AssignmentGrade ag = assignmentGradeRepository.findByAssignmentIdAndStudentEmail(assignmentId, grade.email);
			
			if (ag != null) {
				throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Cannot delete assignment.");
			}
		}
		
		// if there are no student grades then we can delete the assignment
		assignmentRepository.delete(assignment);
	}
}
