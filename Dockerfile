# ---- Giai đoạn 1: build ra file jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml trước để Docker cache phần tải thư viện; sửa code không phải tải lại
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Test chạy riêng bằng "mvn test" (test CSDL dùng Testcontainers, cần Docker nên không chạy trong lúc build image)
RUN mvn -B -q package -DskipTests

# ---- Giai đoạn 2: image chạy, chỉ có JRE + jar ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Không chạy app bằng root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
