
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and project configuration
COPY mvnw pom.xml ./
COPY .mvn .mvn

# Download dependencies
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Copy application source
COPY src src

# Build executable Spring Boot JAR
RUN ./mvnw -B clean package -DskipTests


# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create a non-root user
RUN useradd --system --uid 10001 appuser

# Copy the executable JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Run as non-root user
USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
