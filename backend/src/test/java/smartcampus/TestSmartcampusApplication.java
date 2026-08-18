package smartcampus;

import org.springframework.boot.SpringApplication;

public class TestSmartcampusApplication {

	public static void main(String[] args) {
		SpringApplication.from(SmartcampusApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
