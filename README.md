# knda-check

K-뉴딜 아카데미(부산) 수강생 출결 조회 웹앱.
운영매니저로 일하며 수강생이 **본인의 출석률과 80% 충족 여부, 병결 잔여일수**를 직접 확인하기 편하게 만든 편의성 웹앱.

## 주요 기능

**수강생**

- 이메일로 받은 링크를 눌러 로그인한다. 비밀번호는 없음.
- 본인의 데이터만 확인가능. (다른 수강생 조회, 출결 수정 불가)
- 전체 누적과 단위기간별 출석률, 80% 충족 여부, 앞으로 결석 가능한 일수를 확인.
- 병결 사용일수와 잔여일수, 날짜별 출결 내역과 사유를 확인.

**관리자 (운영매니저 1명)**

- 날짜별 일괄 출결 입력과 확정 (`/admin/attendance`), 반별 전원 출석 버튼
- 수강생 이메일 일괄 등록·수정 (`/admin/students`)
- 반별 수강생 목록과 수강생별 출석 현황 (`/admin/roster`)

## 출석률 계산 규칙

- **확정된 출결만** 계산한다. 미확정 행은 결석이 아니라 미입력이며, 분모에서도 제외.
- 기본 인정 출석 = `출석인정 + 출석인정결석` (공가·인정 병결 포함)
- 지각·조퇴·외출은 **출석인정인 날에 한해** 합산하고, 3회마다 결석 1일로 환산. 이미 결석 처리된 날의 지각·조퇴는 이력만 보여주고 환산에서 제외.
- 전체 기간은 단위기간 결과를 더하지 않고, **전체 원본 횟수를 다시 합산해서** 환산.
- 80% 판정은 반올림 전 값으로, 정수 연산(`최종 인정 × 5 ≥ 훈련일수 × 4`).
- 병결 한도는 각 반마다 다름.

계산 로직 파일 : `attendance/AttendanceCalculator.java`에 있고
테스트로 규칙을 고정.

## 기술 스택

| 구분        | 사용 기술                                                   |
| ----------- | ----------------------------------------------------------- |
| 언어·빌드   | Java 21 (Temurin), Gradle                                   |
| 프레임워크  | Spring Boot 4.1.1 (Web MVC, Security, Data JPA, Validation) |
| 화면        | Thymeleaf 서버 렌더링                                       |
| DB          | PostgreSQL (Supabase 무료 플랜), Flyway 마이그레이션        |
| 로그인 상태 | Spring Session JDBC (DB에 세션 저장)                        |
| 메일        | Resend API                                                  |
| 배포        | Docker, Google Cloud Run                                    |

## 화면 경로

| 경로                | 대상           | 설명                                               |
| ------------------- | -------------- | -------------------------------------------------- |
| `/student/login`    | 누구나         | 수강생 이메일 입력                                 |
| `/auth/verify`      | 누구나         | 메일 링크 확인 후 로그인                           |
| `/my`               | 수강생         | 본인 출석 현황                                     |
| `/login`            | 누구나         | 관리자 로그인                                      |
| `/admin/attendance` | 관리자         | 일일 출결 입력·확정                                |
| `/admin/students`   | 관리자         | 수강생 이메일 관리                                 |
| `/admin/roster`     | 관리자         | 수강생 목록 → 수강생별 현황                        |
| `/dev/*`            | 개발 모드 전용 | 로그인 없이 확인하는 개발용 화면 (운영에서는 꺼짐) |

## 로컬 실행

필요한 것: Java 21, PostgreSQL 접속 정보

1. `config/application-local.properties` 생성. (Git에 올라가지 않는 파일)

   ```properties
   spring.datasource.url=jdbc:postgresql://<호스트>:5432/<DB>?currentSchema=knda_check&sslmode=require
   spring.datasource.username=<사용자>
   spring.datasource.password=<비밀번호>

   app.admin.username=<관리자 아이디>
   app.admin.password-hash=<BCrypt 해시>

   # 로컬 개발용: 로그인 메일을 보내지 않고 로그에 링크 출력, /dev 화면 활성화
   app.dev-mode=true
   ```

2. 실행. 처음 실행하면 Flyway가 `knda_check` 스키마와 테이블 생성.

   ```bash
   ./gradlew bootRun
   ```

3. `http://localhost:8080/login`에서 관리자로 로그인.

수강생 명단, 단위기간, 훈련일정 같은 **초기 데이터는 저장소에 없음.**
별도의 SQL로 DB 입력.

## 설정 값

| 설정 (환경변수)                                                                 | 설명                                                                 |
| ------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| `spring.datasource.url / username / password`<br>(`SPRING_DATASOURCE_*`)        | DB 접속 정보                                                         |
| `app.admin.username` (`APP_ADMIN_USERNAME`)                                     | 관리자 아이디                                                        |
| `app.admin.password-hash` (`APP_ADMIN_PASSWORD_HASH`)                           | 관리자 비밀번호의 **BCrypt 해시**. 형식이 틀리면 앱 시작 불가        |
| `app.base-url` (`APP_BASE_URL`)                                                 | 로그인 링크를 만들 때 쓰는 서비스 주소. 요청의 Host 값은 쓰지 않는다 |
| `app.mail.resend-api-key` (`APP_MAIL_RESEND_API_KEY`)                           | Resend API 키                                                        |
| `app.mail.from`                                                                 | 발신 주소 (기본값은 `application.properties`)                        |
| `server.servlet.session.cookie.secure` (`SERVER_SERVLET_SESSION_COOKIE_SECURE`) | 운영에서는 `true`                                                    |
| `app.dev-mode`                                                                  | `true`면 개발용 화면과 메일 로그 출력 사용.                          |

| 조건                            | 동작                          |
| ------------------------------- | ----------------------------- |
| `app.dev-mode=true`             | 로그인 링크를 로그에 출력     |
| 개발 모드 아님 + Resend 키 있음 | Resend로 발송                 |
| 개발 모드 아님 + Resend 키 없음 | 경고만 기록하고 발송하지 않음 |

## 로그인과 보안

- 로그인 링크는 **10분 유효, 1회 사용**이며 DB에는 토큰 원문이 아닌 해시만 저장.
- 등록되지 않은 이메일을 입력해도 화면은 같은 안내 문구 출력. (수강생 여부 노출 방지)
- 수강생이 직접 이메일을 등록 불가. 이메일은 관리자만 등록·변경 가능.
- 권한은 화면이 아니라 서버(Spring Security)에서 검사, 수강생 본인 ID는 요청 파라미터가 아니라 인증 정보에서 가져온다.
- 관리자 로그인은 5회 연속 실패하면 10분간 잠김.
- 세션 : 관리자 8시간, 수강생 3일.
- CSRF 방어는 기본값(켜짐) 사용.

## 테스트

```bash
./gradlew test
```

- 계산 로직, 보안, 로그인 서비스 테스트는 단독 실행.
- `DatabaseIntegrationTest`는 **실제 DB에 접속**. 시드 데이터(수강생 39명)가 들어 있어야 통과. 모든 쓰기는 테스트가 끝나면 롤백.

## 배포 (Google Cloud Run)

소스 폴더에서 아래 명령을 실행하면 Cloud Build가 `Dockerfile`로 이미지를 만들어 배포.

```bash
gcloud run deploy knda-check --source .
```

- 환경변수는 Cloud Run 서비스에 저장.
- 컨테이너는 `PORT` 환경변수로 포트를 받음.
- 최소 인스턴스 0. 유휴 후 콜드스타트.
- `gcloud` 설정(계정, 프로젝트, 리전) 필요.

## 프로젝트 구조

```
src/main/java/kr/it/acfourd/knda_check/
├─ attendance/   출결 엔티티, 계산기, 관리자 출결 입력
├─ auth/         이메일 로그인, 메일 발송기, 이메일 관리
├─ dashboard/    수강생 대시보드, 관리자 수강생 목록
├─ schedule/     단위기간, 훈련일정
├─ security/     Spring Security 설정, 로그인 시도 제한, 세션 필터
├─ student/      수강생
└─ common/       공통 (반 구분 등)
src/main/resources/
├─ db/migration/ Flyway (V1 초기 스키마, V2 이메일 로그인 토큰, V3 세션 테이블)
└─ templates/    Thymeleaf 화면
```

> 이미 적용된 마이그레이션(V1~V3)은 수정 불가. 변경은 새 버전 파일로 추가.

## 저장소에 올리지 않는 것

실명이 들어간 시드 데이터(`data/`), 로컬 설정(`application-local.*`), 배포용 환경변수 파일(`config/cloudrun-env.yaml`), `.env*`, 엑셀 원본, 비밀번호, API 키.
