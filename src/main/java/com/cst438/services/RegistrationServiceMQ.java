package com.cst438.services;


import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.client.RestTemplate;

import com.cst438.controllers.GradeBookController;
import com.cst438.domain.Course;
import com.cst438.domain.CourseDTOG;
import com.cst438.domain.CourseRepository;
import com.cst438.domain.Enrollment;
import com.cst438.domain.EnrollmentDTO;
import com.cst438.domain.EnrollmentRepository;

public class RegistrationServiceMQ extends RegistrationService {

	@Autowired
	EnrollmentRepository enrollmentRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	public RegistrationServiceMQ() {
		System.out.println("MQ registration service ");
	}

	// ----- configuration of message queues

	@Autowired
	Queue registrationQueue;


	// ----- end of configuration of message queue

	// receiver of messages from Registration service
	
	@RabbitListener(queues = "gradebook-queue")
	@Transactional
	public void receive(EnrollmentDTO enrollmentDTO) {
		System.out.println("Recieved Rabbit message: "+ enrollmentDTO);
		Course course = courseRepository.findById(enrollmentDTO.course_id).orElse(null);
		Enrollment enrollment = new Enrollment();
		enrollment.setCourse(course);
		enrollment.setStudentName(enrollmentDTO.studentName);
		enrollment.setStudentEmail(enrollmentDTO.studentEmail);

		//save to repository
		enrollmentRepository.save(enrollment);
	}

	// sender of messages to Registration Service
	@Override
	public void sendFinalGrades(int course_id, CourseDTOG courseDTO) {
		System.out.println("Sending rabbitmq message: " + courseDTO);
		rabbitTemplate.convertAndSend(registrationQueue.getName(), courseDTO);
		System.out.println("Message sent.");
	}

}
