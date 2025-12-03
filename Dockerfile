FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy the winter directory
COPY winter/ .

# Compile all Java files
RUN mkdir -p out && \
    find src -name "*.java" > sources.txt && \
    javac -d out @sources.txt && \
    rm sources.txt

EXPOSE 8000

# Run from the app directory so it can find cs2003-C3.properties and public/
# JVM flags for predictable container memory behaviour:
# - MaxRAMPercentage: Use 75% of container memory as max heap
# - InitialRAMPercentage: Start with 50% to reduce early GC fluctuations
CMD ["java", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=50.0", "-cp", "out", "Main"]
