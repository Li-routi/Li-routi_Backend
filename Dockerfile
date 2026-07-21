# 빌드는 GitHub Actions 러너에서 수행(./gradlew bootJar). 2GB 서버에서 빌드하지 않기 위함이다.
# 이 이미지는 만들어진 실행 jar를 담아 실행만 한다.
FROM eclipse-temurin:21-jre

WORKDIR /app

# build.gradle에서 plain jar를 끄므로 build/libs에는 실행 jar 하나만 남는다.
COPY build/libs/*.jar app.jar

EXPOSE 8080

# 힙 상한 등 JVM 옵션은 컨테이너 환경변수 JAVA_TOOL_OPTIONS로 주입한다(서버 compose 참고).
ENTRYPOINT ["java", "-jar", "app.jar"]
