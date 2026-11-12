# oeHosts-Agent

Java 애플리케이션에 `-javaagent`로 붙이는 hosts 매핑 에이전트입니다. [oeHubApp](https://github.com/tricatch/oeHubApp)에서 공유한 oeHosts 프로필을 읽어, 거기 적힌 호스트를 그 IP로 연결합니다.

- **OS의 hosts 파일을 고치지 않습니다.** 관리자 권한이 필요 없고, 에이전트를 붙인 JVM 하나에만 적용됩니다. 같은 PC의 다른 프로그램이나 브라우저에는 영향이 없습니다.
- **프로필을 고치면 실행 중에도 반영됩니다.** 에이전트가 공유 URL을 주기적으로 다시 받습니다.
- **JDK 8, 11, 17, 18 이상**에서 동작합니다. JDK 8·11·17·25에서 테스트합니다.
- 의존성이 없는 jar 파일 하나(`oehosts-agent.jar`)입니다.

## 목차

- [받기](#받기)
- [빠른 시작](#빠른-시작)
- [빌드 도구·IDE에서 쓰기](#빌드-도구ide에서-쓰기)
- [옵션](#옵션)
- [동작](#동작)
- [JDK별 연결 방식](#jdk별-연결-방식)
- [제한 사항](#제한-사항)
- [문제 해결](#문제-해결)
- [보안](#보안)
- [빌드](#빌드)
- [라이선스](#라이선스)

## 받기

**oeHubApp을 쓰고 있다면 따로 받을 필요가 없습니다.**

- oeHubApp에 에이전트가 들어 있어, 앱 폴더에 `oehosts-agent.jar`로 설치됩니다.
- oeHubApp이 시작할 때마다 이 저장소의 최신 릴리스를 확인해 에이전트를 자동으로 업데이트합니다. 파일 이름이 그대로라 한 번 넣은 `-javaagent` 옵션은 고칠 필요가 없습니다.
- oeHubApp의 **설정 → oeHosts → Java 에이전트**에서 프로필을 고르면 완성된 `-javaagent` 옵션을 복사할 수 있습니다.

직접 받으려면 [최신 릴리스](https://github.com/tricatch/oeHostsAgent/releases/latest)에서 `oehosts-agent.jar`를 받습니다. 함께 올라간 `SHA256SUMS.txt`로 내려받은 파일을 확인할 수 있습니다.

```sh
sha256sum -c SHA256SUMS.txt                      # Linux
shasum -a 256 -c SHA256SUMS.txt                  # macOS
Get-FileHash oehosts-agent.jar -Algorithm SHA256 # Windows PowerShell (값을 직접 비교)
```

## 빠른 시작

1. oeHubApp에서 쓸 프로필을 열고 편집기 위의 **공유** 버튼을 누릅니다.
2. **이 프로필 공유**를 켜고 **URL 복사**를 누릅니다. 이런 형식의 URL입니다.
   ```
   http://127.0.0.1:36981/hosts/<ID>-<서명>/<이름>
   ```
3. 애플리케이션을 실행할 때 `-javaagent:<에이전트 경로>=<공유 URL>`을 붙입니다.
   ```sh
   java -javaagent:/path/to/oehosts-agent.jar=http://127.0.0.1:36981/hosts/<ID>-<서명>/<이름> -jar app.jar
   ```
4. 시작할 때 표준 오류(stderr)에 이런 로그가 나오면 적용된 것입니다.
   ```
   [oehosts-agent] oeHosts-Agent 0.1.0 (Java 21)
   [oehosts-agent] loaded: 3 host(s) from 1 URL(s)
   ```
   첫 줄은 에이전트 버전과 실행 중인 Java 버전, 둘째 줄은 읽어 온 호스트 수입니다.

- `-javaagent` 옵션은 반드시 `-jar`나 메인 클래스 **앞에** 둡니다. 뒤에 두면 애플리케이션 인자로 넘어갑니다.
- 공유 URL 여러 개를 `,`로 이어 붙일 수 있습니다. 같은 호스트가 여러 프로필에 있으면 앞에 적은 URL이 이깁니다.
- 경로에 공백이 있으면 옵션 전체를 따옴표로 감쌉니다. 예: `"-javaagent:C:\My Tools\oehosts-agent.jar=http://..."`
- oeHubApp의 Java 에이전트 설정 화면에서 복사하면 경로와 URL이 모두 들어간 옵션을 그대로 쓸 수 있습니다.

## 빌드 도구·IDE에서 쓰기

모두 같은 `-javaagent:...` 문자열을 JVM 옵션 자리에 넣으면 됩니다. 아래 표의 `AGENT`는 `/path/to/oehosts-agent.jar=<공유 URL>`을 뜻합니다.

| 도구 | 넣는 곳 |
|---|---|
| IntelliJ IDEA | 실행 구성(Run/Debug Configurations) → **Modify options → Add VM options** → `-javaagent:AGENT` |
| Eclipse | 실행 구성 → **Arguments** 탭 → **VM arguments** |
| Gradle (`run`, `bootRun`, `test`) | `tasks.named<JavaExec>("bootRun") { jvmArgs("-javaagent:AGENT") }`<br>`tasks.test { jvmArgs("-javaagent:AGENT") }` |
| Maven 테스트 (Surefire) | `mvn test -DargLine="-javaagent:AGENT"` |
| Maven `spring-boot:run` | `mvn spring-boot:run -Dspring-boot.run.jvmArguments="-javaagent:AGENT"` |
| 환경 변수 | `JAVA_TOOL_OPTIONS="-javaagent:AGENT"` (그 셸에서 뜨는 모든 JVM에 붙습니다) |

- IDE에서 디버그로 실행할 때도 같은 VM 옵션이 쓰입니다.
- Gradle과 Maven은 테스트와 애플리케이션을 별도 JVM에서 실행합니다. 빌드 도구 자체의 JVM 옵션(`org.gradle.jvmargs`, `MAVEN_OPTS`)이 아니라 위처럼 **실행되는 JVM의 옵션**에 넣어야 합니다.

## 옵션

에이전트 인자 외의 설정은 시스템 속성(`-D`)으로 줍니다.

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `-Doehosts.agent.url=<URL>` | | 공유 URL(`,`로 여러 개). `-javaagent:...jar` 뒤에 `=URL`을 붙이지 않았을 때 씁니다. 둘 다 있으면 에이전트 인자가 우선합니다. |
| `-Doehosts.agent.refresh=<초>` | `60` | 공유 URL을 다시 받는 주기. `0`이면 시작할 때 한 번만 받습니다. |
| `-Doehosts.agent.debug=true` | | 매핑된 조회마다 `호스트 -> IP`를 로그로 남깁니다. 적용 여부를 확인할 때 씁니다. |

## 동작

**읽는 형식**

- hosts 파일 형식을 읽습니다. 한 줄에 `IP 이름 [이름...]`이고, `#` 뒤는 주석입니다. IPv4와 IPv6 주소를 모두 받습니다.
- 같은 호스트가 여러 줄에 있으면 **먼저 나온 줄**이 이깁니다.
- `*.example.com`처럼 와일드카드를 쓸 수 있습니다. 모든 하위 도메인(`a.example.com`, `a.b.example.com`)에 맞고 `example.com` 자체에는 맞지 않습니다. Chrome의 `--host-resolver-rules`와 같은 방식입니다.
- 정확한 이름이 와일드카드보다 우선하고, 더 구체적인(긴) 와일드카드가 짧은 것보다 우선합니다.
- 호스트 이름은 대소문자를 구분하지 않고, 끝의 `.`은 무시합니다.

**조회**

- 목록에 있는 호스트는 적힌 IP로 연결합니다. 목록에 없는 호스트와 역방향 조회(IP → 이름)는 원래대로 JDK가 처리합니다(DNS, OS의 hosts 파일).
- 목록에 있는 호스트를 요청한 주소 종류(IPv4/IPv6)로 찾을 수 없으면, DNS로 넘기지 않고 "찾을 수 없음"으로 끝냅니다. OS의 hosts 파일과 같은 동작입니다.
- 접속하는 호스트 이름은 그대로이므로 HTTPS 인증서도 원래 호스트 이름으로 확인합니다. 연결되는 서버의 인증서가 그 이름을 포함해야 합니다.

**받아 오기와 실패 처리**

- 애플리케이션의 `main`이 실행되기 전에 한 번 받아 오므로, 처음 조회부터 적용됩니다.
- 그 뒤 `oehosts.agent.refresh` 주기마다 다시 받습니다. `loaded:` 로그는 호스트 수가 바뀌었거나 실패에서 회복했을 때만 다시 남깁니다.
- URL 중 하나라도 받지 못하면(oeHubApp이 꺼짐, 공유가 꺼짐 등) **직전 매핑을 그대로 씁니다.** 처음부터 받지 못했으면 매핑 없이 실행합니다. 같은 실패는 다시 성공할 때까지 한 번만 로그에 남깁니다.
- 어떤 경우에도 애플리케이션을 멈추거나 시작을 막지 않습니다. 접속과 응답 대기 시간 제한은 각각 3초입니다.
- 공유 URL에는 시스템에 설정된 HTTP 프록시를 거치지 않고 직접 접속합니다(`127.0.0.1`은 이 PC의 oeHubApp이므로). 리다이렉트는 따라가지 않습니다.

## JDK별 연결 방식

| JDK | 방식 |
|---|---|
| 18 이상 | JDK의 공개 리졸버 SPI(`InetAddressResolverProvider`, [JEP 418](https://openjdk.org/jeps/418)) |
| 9 ~ 17 | JDK 내부의 이름 조회 객체(`InetAddress.nameService`)를 감쌉니다. `--add-opens` 옵션은 필요 없습니다. |
| 8 | JDK 내부의 이름 조회 목록(`InetAddress.nameServices`) 맨 앞에 추가합니다. |

8과 9~17은 JDK 내부 구조를 쓰는 방식입니다. 내부 구조가 다른 JDK 배포판이라 연결에 실패하면, 경고 한 줄만 남기고 매핑 없이 애플리케이션을 그대로 실행합니다.

## 제한 사항

- **JDK 조회 캐시**: JDK는 이름 조회 결과를 따로 캐시합니다(기본 30초, `networkaddress.cache.ttl`). 그래서 프로필을 고친 뒤 실행 중인 애플리케이션에 반영되기까지 새로 고침 주기(기본 60초)에 이 시간이 더해질 수 있습니다. 바로 반영되어야 하면 `-Dsun.net.inetaddr.ttl=0`을 함께 줍니다. 애플리케이션이 이미 맺은 연결(커넥션 풀 등)은 그대로이고, 새 연결부터 바뀐 IP로 갑니다.
- **실행 중인 JVM에는 붙일 수 없습니다**(attach 미지원). JDK가 리졸버를 처음 조회할 때 한 번만 정하기 때문에, 시작할 때 `-javaagent`로 붙여야 합니다.
- **JDK를 거치지 않는 조회에는 적용되지 않습니다.** 애플리케이션이 자체 DNS 클라이언트(예: Netty의 `DnsNameResolver`)로 직접 조회하면 그 경로는 매핑되지 않습니다. `InetAddress`로 조회하는 일반적인 경로(JDK `HttpClient`, `HttpURLConnection`, Apache HttpClient, OkHttp, JDBC 드라이버 등)에는 적용됩니다.
- **같은 PC의 oeHubApp에서만 받을 수 있습니다.** oeHubApp의 공유 서버는 `127.0.0.1`에만 열려 있으므로, 에이전트를 붙인 애플리케이션도 같은 PC에서 실행되어야 합니다.

## 문제 해결

로그는 모두 `[oehosts-agent]`로 시작하며 표준 오류(stderr)에 나옵니다.

| 증상·로그 | 뜻과 조치 |
|---|---|
| 로그가 전혀 없음 | 에이전트가 붙지 않았습니다. `-javaagent` 옵션이 `-jar`·메인 클래스 앞에 있는지, 실제로 실행되는 JVM(테스트용 JVM 등)에 들어갔는지 확인합니다. |
| `no share URL given ...` | 에이전트 인자에 URL이 없습니다. `-javaagent:...jar=` 뒤에 공유 URL을 붙입니다. |
| `could not load ... (Connection refused ...)` | oeHubApp이 꺼져 있거나 공유 서버 포트가 다릅니다. oeHubApp의 설정 → oeHosts → 공유 서버에서 상태와 포트를 확인합니다. |
| `could not load ... (HTTP 404)` | 그 프로필의 공유가 꺼져 있거나 URL이 잘못 복사됐습니다. oeHubApp에서 공유를 켜고 URL을 다시 복사합니다. |
| `ignored (not http/https)`, `ignored (not a URL)` | URL 형식이 잘못됐습니다. 따옴표나 공백이 섞이지 않았는지 확인합니다. |
| `could not hook into name lookup on Java N; hosts mapping is off` | 이 JDK의 내부 구조에 연결하지 못했습니다(JDK 8~17). 가능하면 JDK 18 이상에서 실행합니다. |
| `loaded:`는 나오는데 적용이 안 되는 것 같음 | `-Doehosts.agent.debug=true`로 실행해 `호스트 -> IP` 로그가 나오는지 봅니다. 나오지 않으면 그 호스트를 JDK 밖에서 조회하는 경우입니다([제한 사항](#제한-사항) 참고). |

## 보안

- 공유 URL의 `<서명>` 부분은 oeHubApp이 이 PC에만 있는 비밀키로 만든 값으로, 그 프로필을 읽을 수 있는 권한 자체입니다. 다른 사람에게 알려 주지 마세요.
- 에이전트는 로그에 URL을 남길 때 서명 부분을 `...`로 가립니다.
- 에이전트는 지정한 공유 URL에 GET 요청만 보내고, 그 밖의 곳에는 접속하지 않습니다.

## 빌드

```sh
./gradlew test jar     # → build/libs/oehosts-agent-<버전>.jar
```

- JDK 25 툴체인으로 빌드합니다. JDK 18 SPI 클래스만 Java 18용으로, 나머지는 모두 Java 8용으로 컴파일합니다.
- 테스트는 JDK 8·11·17을 Gradle이 자동으로 받아, 각 버전과 빌드 JDK의 JVM에 에이전트를 실제로 붙여 실행합니다.
- 릴리스에는 빌드한 jar를 `oehosts-agent.jar`라는 이름으로 올립니다.

## 라이선스

[MIT](LICENSE)
