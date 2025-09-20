# AdminAccessLoggingAspect

```java
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// log 필드(로거) 자동 생성
@Slf4j
// 클래스가 AOP Aspect임을 표시
@Aspect
// 클래스 Bean 등록
@Component
@RequiredArgsConstructor
public class AdminAccessLoggingAspect {

    // 현재 HTTP 요청 정보를 가져오기 위해 프록시 주입
    private final HttpServletRequest request;

    // 메서드 실행 전에 실행될 Advice를 지정
    // @Before: 지정한 메서드 실행 전에 Advice 실행 
    // -> UserAdminController 클래스의 changeUserRole 메서드(리턴 타입 무관, 파라미터 무관)를 대상으로 메서드 호출 직전에 이 Advice를 실행하라는 의미
    @Before("execution(* org.example.expert.domain.user.controller.UserAdminController.changeUserRole(..))")
    // JoinPoint joinPoint: 
    // 실제 호출된 메서드 정보를 담음
    // -> 클래스 명, 메서드 명, 파라미터, 대상 객체 등 정보 조회 가능
    public void logBeforeChangeUserRole(JoinPoint joinPoint) {
        // 로깅 정보 수집
        // userId -> request에서 userId 속성(Attribute)을 가져옴
        String userId = String.valueOf(request.getAttribute("userId"));
        // 요청 URL 정보
        String requestUrl = request.getRequestURI();
        // 현재 시간
        LocalDateTime requestTime = LocalDateTime.now();
        
        // 로그 출력
        log.info("Admin Access Log - User ID: {}, " +
                "Request Time: {}, " +
                "Request URL: {}, " +
                "Method: {}",
                userId,
                requestTime,
                requestUrl,
                // 호출된 메서드 이름(changeUserRole) 반환
                // 클래스 명 반환 시: getSignature().getDeclaringTypeName()
                joinPoint.getSignature().getName()
        );
    }
}
```

## 역할

- Spring AOP로 `UserAdminController.changeUserRole(...)` 메서드가 실행되기 직전에(Before) 호출, 관리자 접근 로그를 찍는 Aspect
- Advice를 실행해서 `HttpServletRequest`에서 현재 요청의 `userId`와 요청 URL(`getRequestURI()`)을 읽어오고 현재 시간(`LocalDateTime.now()`)과 호출된 메서드 이름(`joinPoint.getSignature().getName()`)을 로그(`log.info`)로 기록

---

## 의도

- 누가 언제 어떤 URL 호출했는지 기록
- 관리자 권한 변경과 같은 민감 액션 추적
- 추후 감사(audit) 로그나 보안 분석에 활용 가능

---

## 흐름

1. 클라이언트 HTTP 요청
2. 컨트롤러 호출
3. 프록시(Aspect)가 호출 가로챔
4. `@Before` Advice 실행 -> 로그 찍음
5. 실제 `changeUserRole()` 실행

---

## 설명

### `private final HttpServletRequest request;`

- HttpServletRequest 객체는 HTTP 요청마다 새로 만들어짐 
  - 요청별로 다른 정보(유저, URL 등)를 담고 있음 
- Spring Bean은 기본적으로 싱글턴(애플리케이션 전체에서 단 하나만 존재) 
  - 따라서 싱글턴 빈 내 요청마다 다른 객체를 바로 주입 시 섞인다는 문제 발생
- Spring은 프록시(실제 요청이 들어오면 지금 스레드의 요청을 찾아서 참조하는 "대리 객체") 주입
- Spring 내부에서 스레드-로컬(ThreadLocal) 이용해 현재 요청 정보를 안전하게 참조할 수 있음
  - 스레드-로컬(ThreadLocal): 
    - 각 스레드가 독립적으로 갖는 저장소 즉 스레드마다 따로 저장되는 변수
    - 한 스레드에서 저장한 값은 다른 스레드에서 볼 수 없음
    - 스레드끼리 값이 섞이지 않게 안전하게 저장 가능
- 또한 멀티스레드 환경에서도 안전하게 현재 요청 정보를 읽을 수 있음
- 단, 웹 요청이 없는 스레드(예: 백그라운드 스케줄러, JUnit 테스트)에서는 request가 없어서 예외 발생 가능

### Advice & Aspect & 횡단 관심사(Cross-Cutting Concern)

- Advice:
    - AOP에서 공통 기능(코드) 그 자체 -> 횡단 관심사를 실제로 구현한 코드
    - 핵심 로직과 분리되어 있어, 언제 실행될지(`@Before`, `@After`, `@Around`)만 지정
    - 예:
        - 로깅: 메서드 호출 기록, 요청 정보 기록
            - 트랜잭션 관리: commit/rollback 자동 처리
            - 보안/권한 체크: 특정 메서드 접근 제한
            - 성능 모니터링: 메서드 실행 시간 기록
            - 예외 처리: 공통적인 에러 로직
- Aspect:
    - 횡단 관심사 + 적용 범위를 묶은 모듈 -> Advice + Pointcut + 기타 설정을 하나로 묶은 것
- 횡단 관심사(Cross-Cutting Concern):
    - 여러 클래스/메서드에 걸쳐 반복되는 공통 기능
    - 핵심 로직과 분리되어 모든 대상에 적용 가능
    - 예: 로깅, 트랜잭션 관리, 보안 및 권한 체크, 성능 모니터링, 예외 처리

=> 정리

- Aspect는 “전체 공통 기능(횡단 관심사)”을 담은 그릇/클래스
  - AdminAccessLoggingAspect 클래스
- Advice는 그 Aspect 안에서 “실제로 동작하는 코드”
  - logBeforeChangeUserRole 메서드 -> 실제 로그를 찍는 코드
- Pointcut은 “어디(어떤 메서드)에서 Advice를 실행할지”를 지정하는 규칙

### JoinPoint

- Advice(횡단 관심사) 안에서 현재 호출된 메서드 정보 조회용 객체 타입

### 포인트컷(Pointcut)

- 작성 시 Spring AOP가 제공하는 AspectJ 표현식(AspectJ Expression) 사용
- 기본 문법: 리턴 타입 패키지.클래스.메서드(파라미터)
    - 리턴 타입 = `*` -> 리턴 타입 무관
    - 파라미터 = `..` -> 파라미터 개수 및 타입 무관

---

## 개선 사항

### userId 취득 방식

- `request.getAttribute("userId")`가 아닌 Spring Security를 사용 시,
- `SecurityContextHolder.getContext().getAuthentication()`에서 사용자 정보를 가져오는 것이 더 표준적이고 안전

### 시간 표기

- `LocalDateTime.now()`는 서버 로컬 타임존 기준
- 분산 환경/다국적 서비스라면 Instant + UTC 저장, 로그 레벨 시각 포맷 일관화

### 실행 순서 제어

- 여러 Aspect가 있다면, `@Order`로 실행 순서 제어 가능