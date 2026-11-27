# ==========================================
# Stage 1: build (runs the tests too)
# ==========================================
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline -Pproduction

COPY src ./src
RUN mvn -B -Pproduction package

# ==========================================
# Stage 2: runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S bastion && adduser -S bastion -G bastion
USER bastion

COPY --from=build /build/target/bastionvault-*.jar bastion-vault.jar

EXPOSE 8080

# 200 while the process is healthy, even when sealed (a sealed vault should not be restarted).
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/api/v1/health >/dev/null || exit 1

# Size the heap from the container memory limit instead of the host's RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Dspring.profiles.active=prod", "-jar", "bastion-vault.jar"]
