package br.com.brasildecide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({TseProperties.class, R2Properties.class})
public class Application {
    public static void main(String[] args) { SpringApplication.run(Application.class, args); }
}
