package com.imqh.usermanagementapi;

import com.imqh.usermanagementapi.dto.request.PhoneRequest;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
@AutoConfigureMockMvc
class UserManagementApiApplicationTests {

	@Autowired
	private UserService userService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;
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
		assertEquals(response.getToken(), jdbcTemplate.queryForObject(
				"select token from users where id = ?", String.class, response.getId()));
		assertEquals(true, stored.get("IS_ACTIVE"));
		Map<String, Object> storedPhone = jdbcTemplate.queryForMap(
				"select number, citycode, contrycode from phones where user_id = ?", response.getId());
		assertEquals("1234567", storedPhone.get("NUMBER"));
		assertEquals("1", storedPhone.get("CITYCODE"));
		assertEquals("56", storedPhone.get("CONTRYCODE"));
	}

	@Test
	void registerUser_sequentialDuplicateReturnsConflict() throws Exception {
		String json = """
				{"name":"Contract Test","email":"c@d.cl","password":"Password1","phones":[]}
				""";
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Contract Test"))
				.andExpect(jsonPath("$.email").value("c@d.cl"))
				.andExpect(jsonPath("$.phones").isEmpty())
				.andExpect(jsonPath("$.lastLogin").doesNotExist())
				.andExpect(jsonPath("$.isActive").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(result -> {
					var body = jsonMapper.readTree(result.getResponse().getContentAsString());
					assertEquals(body.get("created"), body.get("modified"));
					assertEquals(body.get("created"), body.get("last_login"));
				});
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.mensaje").value("El correo ya registrado"))
				.andExpect(result -> assertEquals(1,
						jsonMapper.readTree(result.getResponse().getContentAsString()).size()));
		assertEquals(1, jdbcTemplate.queryForObject("select count(*) from users where email = ?", Integer.class, "c@d.cl"));
	}

	@Test
	void registerUser_invalidConfiguredPasswordReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("""
				{"name":"Contract Test","email":"e@f.cl","password":"hunter2","phones":[]}
				"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.mensaje").value("La contraseña no cumple con el formato requerido"))
				.andExpect(result -> assertEquals(1,
						jsonMapper.readTree(result.getResponse().getContentAsString()).size()));
		assertEquals(0, jdbcTemplate.queryForObject("select count(*) from users where email = ?", Integer.class, "e@f.cl"));
	}

}
