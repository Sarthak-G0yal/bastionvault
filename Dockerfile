# ==========================================
# STAGE 1: Build the Application
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Build the Java app and compile the Vaadin frontend UI
RUN mvn clean package -Pproduction -DskipTests

# ==========================================
# STAGE 2: Run the Application
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S bastiongroup && adduser -S bastionuser -G bastiongroup
USER bastionuser

COPY --from=build /build/target/*.jar bastion-vault.jar

EXPOSE 8080

# Force Spring Boot into the 'prod' profile defined in application.yml
ENTRYPOINT ["java", "-Dspring.profiles.active=prod", "-jar", "bastion-vault.jar"]   