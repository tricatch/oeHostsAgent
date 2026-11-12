# oeHosts-Agent

Java 애플리케이션에 `-javaagent`로 붙여, [oeHubApp](https://github.com/tricatch/oeHubApp) oeHosts의 **공유 URL**에 적힌 호스트를 그 IP로 연결하게 하는 에이전트입니다. OS의 hosts 파일을 고치지 않고, 그 JVM 하나에만 적용됩니다.

- JDK 8, 11, 17, 18 이상에서 동작합니다. 실제로 JDK 8·11·17·25에서 테스트합니다.

| JDK | 연결 방식 |
|---|---|
| 18 이상 | JDK의 공개 리졸버 SPI(`InetAddressResolverProvider`, JEP 418) |
| 9 ~ 17 | JDK 내부의 이름 조회 객체(`InetAddress.nameService`)를 감쌈. `--add-opens` 옵션은 필요 없습니다. |
| 8 | JDK 내부의 이름 조회 목록(`InetAddress.nameServices`) 맨 앞에 추가 |

- 8과 9~17은 JDK 내부 구조를 쓰는 방식입니다. 연결에 실패하면 경고만 출력하고 매핑 없이 애플리케이션을 그대로 실행합니다.
- 의존성이 없는 jar 하나(`oehosts-agent-<버전>.jar`)입니다.

## 사용법

1. oeHubApp에서 프로필을 열고 **공유** → **이 프로필 공유**를 켠 뒤 **URL 복사**를 누릅니다.
2. 애플리케이션을 실행할 때 에이전트와 URL을 붙입니다.

```sh
java -javaagent:oehosts-agent-<버전>.jar=http://127.0.0.1:36981/hosts/<ID>-<서명>/<이름> -jar app.jar
```

```
[oehosts-agent] oeHosts-Agent 0.1.0 (Java 21)
[oehosts-agent] loaded: 3 host(s) from 1 URL(s)
```

첫 줄에 에이전트 버전과 실행 중인 Java 버전이 나옵니다.

- URL 여러 개는 `,`로 구분합니다. 같은 호스트가 여러 URL에 있으면 앞의 것이 이깁니다.
- Gradle·Maven·IDE에서는 JVM 옵션(`jvmArgs`, `-DargLine`, VM options)에 같은 `-javaagent:...`를 넣습니다.

## 옵션 (시스템 속성)

| 옵션 | 기본값 | 설명 |
|---|---|---|
| `-Doehosts.agent.url=<URL>` | | 에이전트 인자 대신 URL 지정 (`,`로 여러 개) |
| `-Doehosts.agent.refresh=<초>` | `60` | 공유 URL을 다시 받는 주기. `0`이면 시작할 때 한 번만 받습니다. |
| `-Doehosts.agent.debug=true` | | 매핑된 조회마다 `호스트 -> IP`를 로그로 남깁니다. |

## 동작

- 애플리케이션의 `main`보다 먼저 한 번 받아 오므로 처음 조회부터 적용됩니다.
- hosts 파일 형식(`IP 이름 [이름...]`, `#` 주석)을 읽습니다. 같은 호스트는 먼저 나온 줄이 이깁니다. `*.example.com`은 모든 하위 도메인에 맞고(`example.com` 자체는 아님), 정확한 이름이 와일드카드보다 우선합니다.
- 목록에 없는 호스트와 역방향 조회는 원래대로 JDK가 처리합니다(DNS, OS hosts).
- 목록에 있는 호스트가 요청한 주소 종류(IPv4/IPv6)로 매핑되지 않았으면 DNS로 넘기지 않고 찾을 수 없음으로 처리합니다. hosts 파일과 같은 동작입니다.
- URL을 받지 못하면(앱이 꺼짐, 공유 꺼짐 등) 직전 매핑을 계속 쓰고, 처음부터 못 받았으면 매핑 없이 실행됩니다. 애플리케이션을 멈추지 않습니다.
- 접속 주소는 그대로이므로 HTTPS 인증서 확인도 원래 호스트 이름으로 합니다.
- 로그에는 URL의 서명 부분을 `…`로 가려서 남깁니다.

## 참고

- JDK는 조회 결과를 따로 캐시합니다(기본 30초, `networkaddress.cache.ttl`). 프로필을 고친 뒤 반영까지 새로 고침 주기에 이 시간만큼 더 걸릴 수 있습니다. 바로 반영되어야 하면 `-Dsun.net.inetaddr.ttl=0`을 함께 줍니다.
- 이미 실행 중인 JVM에 붙이는 방식(attach)은 지원하지 않습니다. JDK가 리졸버를 처음 조회할 때 한 번만 정하기 때문입니다.
- 애플리케이션이 자체 DNS 클라이언트(예: Netty의 `DnsNameResolver`)를 쓰는 경우 그 경로에는 적용되지 않습니다.

## 빌드

```sh
./gradlew test jar     # → build/libs/oehosts-agent-<버전>.jar
```

JDK 25 툴체인으로 빌드합니다. JDK 18 SPI 클래스만 Java 18용으로, 나머지는 모두 Java 8용으로 컴파일합니다. 테스트는 JDK 8·11·17을 Gradle이 자동으로 받아 각 버전의 JVM에 에이전트를 붙여 실행합니다.

## 라이선스

[MIT](LICENSE)
