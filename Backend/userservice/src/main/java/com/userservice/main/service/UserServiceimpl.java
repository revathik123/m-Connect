package com.userservice.main.service;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.BeanUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.userservice.main.entity.EmpAttandence;
import com.userservice.main.entity.Employee;
import com.userservice.main.entity.EmployeeLeave;
import com.userservice.main.entity.UserEntity;
import com.userservice.main.exception.ErrorMessage;
import com.userservice.main.registration.dto.EmailUtils;
import com.userservice.main.registration.dto.EmpAttandenceDto;
import com.userservice.main.registration.dto.EmployeeLeaveDto;
import com.userservice.main.registration.dto.LoginForm;
import com.userservice.main.registration.dto.PasswordUtils;
import com.userservice.main.registration.dto.RegistrationDto;
import com.userservice.main.registration.dto.ResponseMsg;
import com.userservice.main.repository.EmpAttandenceRepo;
import com.userservice.main.repository.EmployeeLeaveRepository;
import com.userservice.main.repository.EmployeeRepo;
import com.userservice.main.repository.UserRepository;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

//import jakarta.persistence.NonUniqueResultException;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService, UserDetailsService {

	@Autowired
	private UserRepository userrepo;

	@Autowired
	private EmployeeRepo emprepo;

	@Autowired
	private EmployeeLeaveRepository empleaverepo;

	@Autowired
	private EmailUtils emailutils;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private MessageConverter messageConverter;
	
	@Autowired
	private EmpAttandenceRepo empAttandencerepo;

//	@Autowired
//	private Queue userQueue; // Assuming you have a user-specific queue

//    @Override
//	public UserEntity save(RegistrationDto registrationDto) {
//		
//		UserEntity userEntity = new UserEntity(
//				registrationDto.getGuid(),
//				registrationDto.getId(),
//				registrationDto.getEmail(),
//				passwordEncoder.encode(registrationDto.getPassword()),
//				registrationDto.getRole()
//				);
//				
//				return userrepo.save(userEntity);
//		
//	}

	@Override
	public UserDetails loadUserByUsername(String gmail) throws UsernameNotFoundException {
		UserEntity user = userrepo.findByGmail(gmail);
		if (user == null) {
			throw new UsernameNotFoundException("User not found with gmail: " + gmail);
		}
		return new org.springframework.security.core.userdetails.User(user.getGmail(), user.getPassword(),
				Collections.emptyList());
	}

	// Your other service methods here..

	@Override
	public String userLogin(LoginForm loginform) {
		BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

		UserEntity user = userrepo.findByGmail(loginform.getGmail());

		log.info("Employee Loging... method running. ");

		if (user != null && bcrypt.matches(loginform.getPassword(), user.getPassword())) {
			if (user.isAdminStatus() == true) {
				return "Admin";
			} else {
				return "Employee";
			}
		} else {
			return "Incorrect username or password";
		}
	}

	@Override
	public Employee saveEmployee(Employee employee) {
		return emprepo.save(employee);

	}

	@Override
	public List<Employee> getAllEmployees() {
		List<Employee> emp = emprepo.findAll();
		return emp;
	}

	@Override
	public Employee getEmployeeById(Long id) {

		Optional<Employee> emp = emprepo.findById(id);

		if (emp.isPresent()) {
			return emp.get();
		} else {
			return null;
		}

	}

	@Override
	public String forgotPassword(LoginForm loginform) {

		UserEntity user = userrepo.findByGmail(loginform.getGmail());

		String temppwd = PasswordUtils.generateRandomPwd();
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance("SHA-256");
			digest.reset();
			digest.update(temppwd.getBytes());
			byte[] encryptedPwd = digest.digest();
			String encodepwd = Base64.getEncoder().encodeToString(encryptedPwd);

			user.setGmail(user.getGmail());
			user.setPassword(encodepwd);
			user.setAccStatus("LOKED");
			userrepo.save(user);
			String to = loginform.getGmail();
			String subject = "Verification Your Account";
			StringBuffer body = new StringBuffer();
			body.append("<h1>'Otp For Verifying Your Account.</h1>' ");

			body.append("<h3>" + temppwd + "</h3>");

			log.info("Employee Forgot Password Message Sending To Mail ... Method Running.");
			emailutils.sendmail(to, subject, body.toString());
//			return true;
			return temppwd;

		} catch (Exception e) {
			// Handle the exception appropriately (log, throw, etc.) using exception class.
			throw new ErrorMessage(e);
		}

	}

	@Override
	public boolean getOtp(String gmail, String otp) {

		try {
			UserEntity user = userrepo.findByGmail(gmail);

			if (user != null) {

				String pwd = user.getPassword();
				MessageDigest digest;

				try {
					digest = MessageDigest.getInstance("SHA-256");
					digest.reset();
					digest.update(otp.getBytes());

					byte[] encryptpwd = digest.digest();
					String encodedpwd = Base64.getEncoder().encodeToString(encryptpwd);
					log.info("Employee getotp ... Method Running.");
					if (encodedpwd.equals(pwd) && !"".equals(encodedpwd)) {
						user.setAccStatus("UNLOKED");
						userrepo.save(user);
						return true;
					}
				} catch (Exception er) {
					er.printStackTrace();
				}
			}
		} catch (Exception e) {
			// Handle the exception appropriately (log, throw, etc.) using exception class.
			throw new ErrorMessage(e);
		}
		return false;
	}

	@Override
	public String setpassword(String gmail, String password) {

		UserEntity user = userrepo.findByGmail(gmail);
		log.info("Employee Re-Setting Password... Method Running.");

		if (user != null) {
			MessageDigest digist;
			try {
				digist = MessageDigest.getInstance("SHA-256");
				digist.reset();
				digist.update(password.getBytes());

				byte[] encryptpwd = digist.digest();

				String encodedpwd = Base64.getEncoder().encodeToString(encryptpwd);

				user.setPassword(encodedpwd);
				userrepo.save(user);

				return "Password Set Succesfully";

			} catch (Exception er) {
				er.printStackTrace();
				return "check passowrd";
			}

		} else {
			return "user not found";
		}
	}

	@Override
	public ResponseMsg updateEmp(String gmail, RegistrationDto registerEmp) {

		Optional<Employee> empOptional = emprepo.findByGmail(gmail);

		System.out.println("method checking:--------->" + empOptional != null);

		log.info("Updating Employee Details... Method Running.");

		if (empOptional.isPresent()) {

			Employee emp = empOptional.get();

			EmployeeLeave updatedata = new EmployeeLeave();

			BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
			String encryptedPwd = bcrypt.encode(registerEmp.getPassword());

			emp.setPassword(encryptedPwd);
			emp.setBlood_group(registerEmp.getBlood_group());
			emp.setCurrentPincode(registerEmp.getCurrentPincode());
			emp.setCurrentAddress(registerEmp.getCurrentAddress());
			emp.setCurrentCity(registerEmp.getCurrentCity());
			emp.setCurrentCountry(registerEmp.getCurrentCountry());
			emp.setCurrentState(registerEmp.getCurrentState());
			emp.setDob(registerEmp.getDob());
			emp.setFirstname(registerEmp.getFirstname());
			emp.setLastname(registerEmp.getLastname());
			emp.setGender(registerEmp.getGender());
			emp.setLivingincurrent(registerEmp.getLivingincurrent());
			emp.setMarital_status(registerEmp.getMarital_status());
			emp.setStayingsince(registerEmp.getStayingsince());
			emp.setPermanentAddress(registerEmp.getPermanentAddress());
			emp.setPermanentCity(registerEmp.getPermanentCity());
			emp.setPermanentCountry(registerEmp.getPermanentCountry());
			emp.setPermanentPincode(registerEmp.getPermanentPincode());
			emp.setPermanentState(registerEmp.getPermanentState());
			emp.setPhonenumber(registerEmp.getPhonenumber());
			emp.setRole(registerEmp.getRole());

			emprepo.save(emp);

			BeanUtils.copyProperties(emp, updatedata);
			empleaverepo.save(updatedata);

			EmpAttandence uploadEmpId = new EmpAttandence();
            BeanUtils.copyProperties(emp, uploadEmpId);
            empAttandencerepo.save(uploadEmpId);
            
			return new ResponseMsg(true, emp.getFirstname(), "Employee updated successfully.");
			
		} else {
			return new ResponseMsg(false, "", "Employee with" + gmail + "ID not found.");
		}
	}

	@Override
	public EmployeeLeave applyLeave(EmployeeLeave employeeLeave) {
		return empleaverepo.save(employeeLeave);
	}

//	@Override
//	public String deleteemp(String guid) {	     
//		return emprepo.delete(guid);
//	}
//	
	@Override
	public String DeleteUserById(long id) {
		emprepo.deleteById(id);
//		userrepo.deleteAll();
		return "User Data Droped";
	}

//	@Override
//	public void createEmployeeLeave(Employee employee) {
//        // Create EmployeeLeave entity based on the provided Employee data
//        EmployeeLeave employeeLeave = new EmployeeLeave();
//        employeeLeave.setEmployeeId(employee.getId());
//        // Set other fields as needed
//        
//        // Save EmployeeLeave entity to the database
//        employeeLeaveRepository.save(employeeLeave);
//    }

	@Override
	public ResponseMsg saveLeaveDetails(String guid, EmployeeLeaveDto empDto) {
		Optional<EmployeeLeave> employeeLeaveDataOptional = empleaverepo.findByGuid(guid);

		log.info("Employee Leave Details Form Filling  ....Method Running.");

		if (employeeLeaveDataOptional.isPresent()) {
			EmployeeLeave employeeLeave = employeeLeaveDataOptional.get();

			// Update the fields of the existing entity
			employeeLeave.setAdmingmail(empDto.getAdmingmail());
			employeeLeave.setType(empDto.getType());
			employeeLeave.setFromDate(empDto.getFromDate());
			employeeLeave.setFromShift(empDto.getFromShift());
			employeeLeave.setToDate(empDto.getToDate());
			employeeLeave.setToShift(empDto.getToShift());
			employeeLeave.setReasonFor(empDto.getReasonFor());
			employeeLeave.setAdmingmail(empDto.getAdmingmail());

			// Save the updated entity
			employeeLeave = empleaverepo.save(employeeLeave);

			// Send approval notification to admin service
			log.info("Employee Leave Details Data Sending to Perticular Admin  ... Method Running. Data is :"
					+ employeeLeave.toString());
			// Ensure that employeeLeave is converted to JSON before sending
			Message message = MessageBuilder.withBody(convertObjectToJsonBytes(employeeLeave.toString())).andProperties(
					MessagePropertiesBuilder.newInstance().setContentType(MessageProperties.CONTENT_TYPE_JSON).build())
					.build();
			rabbitTemplate.convertAndSend(MQConfig.EXCHANGE, MQConfig.ROUTING_KEY, message);
//	        rabbitTemplate.convertAndSend(MQConfig.EXCHANGE, MQConfig.ROUTING_KEY, employeeLeave.toString());

			System.out.println("Leave Request is sent Successfully.");

			return new ResponseMsg(true, employeeLeave.getGuid(), "Leave request updated successfully");
		} else {
			return new ResponseMsg(false, "", "Employee leave data not found for the provided GUID: " + guid);
		}
	}

	private byte[] convertObjectToJsonBytes(Object object) throws MessageConversionException {
		try {
			return messageConverter.toMessage(object, null).getBody();
		} catch (MessageConversionException e) {
			throw new RuntimeException("Failed to convert object to JSON bytes", e);
		}
	}

	
	
	@Override
	public ResponseMsg empAttandenceDataStoring(String guid, EmpAttandenceDto empattandenceDto) {

		 
		Optional<EmpAttandence> attandencedata = empAttandencerepo.findByGuid(guid);
		
		if(attandencedata.isPresent()) {
			EmpAttandence empAttandence = attandencedata.get();
			empAttandence.setDate(guid);
			empAttandence.setClockInTime(guid);
			empAttandence.setClockOutTime(guid);
			empAttandence.setBreakHours(guid);
			empAttandence.setAnomalous(guid);
			empAttandence.setTotalHours(guid);
			
			empAttandencerepo.save(empAttandence);
		return new ResponseMsg(true,empAttandence.getGuid(),"Employee Atteandence Stored Succesfully.");
		}else {
		   return new ResponseMsg(false," ","Error Occured Employee Atteandence Not Stored.");
		}
	}
  }

