package smartcampus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		// Pinned to the same MySQL version docker-compose runs, so repository tests
		// execute against the engine the application actually uses.
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

}
