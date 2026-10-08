package com.imqh.usermanagementapi;

import com.imqh.usermanagementapi.dto.request.PhoneRequest;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserManagementApiApplicationTests {

	@Autowired
	private UserService userService;

	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Test
	void contextLoads() {
	}

	@Test
	void registerUser_preservesH2PersistenceAndBCrypt() {
		UserRequest request = new UserRequest();
		request.setName("Migration Test");
		request.setEmail("a@b.cl");
		request.setPassword("Password1");
		PhoneRequest phone = new PhoneRequest();
		phone.setNumber("1234567");
		phone.setCitycode("1");
		phone.setContrycode("56");
		request.setPhones(List.of(phone));

		UserResponse response = userService.registerUser(request);
		Map<String, Object> stored = jdbcTemplate.queryForMap(
				"select name, email, password, token, is_active from users where id = ?", response.getId());
		assertEquals(request.getName(), stored.get("NAME"));
		assertEquals(request.getEmail(), stored.get("EMAIL"));
		assertNotEquals(request.getPassword(), stored.get("PASSWORD"));
		assertTrue(new BCryptPasswordEncoder().matches(request.getPassword(), (String) stored.get("PASSWORD")));
		assertNotNull(response.getToken());
		assertEquals(response.getToken(), stored.get("TOKEN"));
		assertEquals(true, stored.get("IS_ACTIVE"));
		Map<String, Object> storedPhone = jdbcTemplate.queryForMap(
				"select number, citycode, contrycode from phones where user_id = ?", response.getId());
		assertEquals("1234567", storedPhone.get("NUMBER"));
		assertEquals("1", storedPhone.get("CITYCODE"));
		assertEquals("56", storedPhone.get("CONTRYCODE"));
	}

}
