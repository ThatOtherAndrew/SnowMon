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
CMD ["java", "-cp", "out", "Main"]
