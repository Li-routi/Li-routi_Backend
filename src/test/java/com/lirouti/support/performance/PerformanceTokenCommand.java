package com.lirouti.support.performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.global.apiPayload.code.BaseErrorCode;
import com.lirouti.global.properties.JwtProperties;
import com.lirouti.global.util.JwtUtil;

/**
 * 로컬 DB의 테스트 회원으로 JMeter용 개발 토큰을 발급한다.
 * 테스트 소스에만 두어 배포 JAR에 포함하지 않고, 토큰 발급 HTTP API도 만들지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
// 회원 조회 외의 DB 변경이나 Redis 연결이 발생하지 않도록 필요한 자동 설정만 사용한다.
@EnableAutoConfiguration(exclude = {
        FlywayAutoConfiguration.class,
        DataRedisAutoConfiguration.class,
        DataRedisReactiveAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties(JwtProperties.class)
@EntityScan(basePackageClasses = Member.class)
@EnableJpaRepositories(basePackageClasses = MemberRepository.class)
@Import(JwtUtil.class)
public class PerformanceTokenCommand {
    private static final String MEMBER_ID_ENV = "PERFORMANCE_MEMBER_ID";
    private static final Long DEFAULT_MEMBER_ID = 9001L;
    private static final Path USERS_CSV = Path.of("performance", "jmeter", "users.csv");
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

    /**
     * 활성 로컬 회원을 확인한 뒤 토큰을 로그에 노출하지 않고 JMeter CSV에 저장한다.
     */
    public static void main(String[] args) {
        int exitCode = run(System.getenv(MEMBER_ID_ENV), USERS_CSV);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String configuredMemberId, Path output) {
        final Long memberId;
        try {
            memberId = resolveMemberId(configuredMemberId);
        } catch (IllegalArgumentException exception) {
            System.err.printf("❌ 회원 ID 설정을 확인하세요: %s%n", exception.getMessage());
            return 1;
        }

        try {
            try (ConfigurableApplicationContext context = startContext()) {
                String token = issue(
                        memberId,
                        context.getBean(MemberRepository.class),
                        context.getBean(JwtUtil.class)
                );
                writeUsersCsv(output, token);

                System.out.printf(
                        "✅ memberId=%d 개발 토큰을 %s에 저장했습니다.%n",
                        memberId,
                        output.toAbsolutePath().normalize()
                );
                System.out.println("   토큰 값은 출력하지 않았습니다. 이제 task performance-smoke를 실행하세요.");
                return 0;
            }
        } catch (MemberException exception) {
            printDomainError(exception.getCode());
        } catch (IOException exception) {
            System.err.println("❌ performance/jmeter/users.csv를 안전하게 저장하지 못했습니다.");
        } catch (RuntimeException exception) {
            // 하위 예외 메시지에는 DB 접속 정보가 포함될 수 있어 사용자 출력에 그대로 싣지 않는다.
            System.err.println("❌ 로컬 설정과 MySQL 연결 상태를 확인하세요.");
        }
        return 1;
    }

    static Long resolveMemberId(String configuredMemberId) {
        if (configuredMemberId == null || configuredMemberId.isBlank()) {
            return DEFAULT_MEMBER_ID;
        }

        try {
            long memberId = Long.parseLong(configuredMemberId.trim());
            if (memberId <= 0) {
                throw new IllegalArgumentException("memberId는 양수여야 합니다.");
            }
            return memberId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("memberId는 숫자여야 합니다.");
        }
    }

    static String issue(Long memberId, MemberRepository memberRepository, JwtUtil jwtUtil) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (!member.isActiveMember()) {
            throw new MemberException(MemberErrorCode.WITHDRAWN_MEMBER);
        }

        if (Role.ROLE_ADMIN.equals(member.getRole())) {
            return jwtUtil.createDevAdminToken(member.getId());
        }
        return jwtUtil.createDevToken(member.getId());
    }

    static void writeUsersCsv(Path output, String token) throws IOException {
        Path absoluteOutput = output.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        Files.createDirectories(parent);

        Path temporaryFile = Files.createTempFile(parent, ".users-", ".tmp");
        try {
            restrictToOwner(temporaryFile);
            Files.writeString(
                    temporaryFile,
                    "access_token\n" + token + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            moveReplacing(temporaryFile, absoluteOutput);
            restrictToOwner(absoluteOutput);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static ConfigurableApplicationContext startContext() {
        return new SpringApplicationBuilder(PerformanceTokenCommand.class)
                .web(WebApplicationType.NONE)
                .profiles("local")
                .run(
                        "--debug=false",
                        "--logging.level.root=WARN",
                        "--spring.main.banner-mode=off",
                        "--spring.main.log-startup-info=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.show-sql=false"
                );
    }

    private static void printDomainError(BaseErrorCode errorCode) {
        System.err.printf("❌ [%s] %s%n", errorCode.getCode(), errorCode.getMessage());
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        try {
            // 토큰 파일은 Git ignore와 별개로 현재 OS 사용자만 읽고 쓸 수 있게 제한한다.
            Files.setPosixFilePermissions(file, OWNER_READ_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // POSIX 권한을 지원하지 않는 파일시스템에서는 운영체제 기본 권한을 사용한다.
        }
    }
}
