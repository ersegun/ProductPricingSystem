# ---- Build stage ----
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
# Build only the discount-service module
RUN gradle :discount-service:build -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/discount-service/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
