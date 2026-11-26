package tech.sarthakgoyal.bastionvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BastionvaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(BastionvaultApplication.class, args);
    }

    /** Version from the jar manifest, "dev" when running from an IDE. */
    public static String version() {
        String version = BastionvaultApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
