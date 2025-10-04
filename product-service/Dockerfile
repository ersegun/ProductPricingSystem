# ---- Build stage ----
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
# Copy the whole project (multi-module needs root settings.gradle.kts)
COPY . .
# Build only the product-service module
RUN gradle :product-service:build -x test --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jdk
WORKDIR /app
# Copy the built jar from the module
COPY --from=build /app/product-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
