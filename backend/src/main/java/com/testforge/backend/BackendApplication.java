package com.testforge.backend;

import com.testforge.backend.config.AiProperties;
import com.testforge.backend.config.CorsProperties;
import com.testforge.backend.config.JwtProperties;
import com.testforge.backend.config.StorageProperties;
import com.testforge.backend.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, JwtProperties.class, CorsProperties.class, AiProperties.class,
        UploadProperties.class})
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
