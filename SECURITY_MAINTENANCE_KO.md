# 보안 및 장기 유지관리

이 프로젝트는 개인 한 명의 Android·Windows 메모 앱입니다. 광고, 분석, 행동 추적, 외부 오류 수집, 자체 계정, 자체 비밀번호, Cloud Functions 또는 서버를 사용하지 않습니다.

## Dependabot과 보안 경고

`.github/dependabot.yml`은 npm, Gradle, GitHub Actions 의존성을 월 1회 확인합니다. 자동 병합은 설정하지 않았습니다.

1. GitHub 저장소 → **Security → Dependabot alerts**에서 경고를 확인합니다.
2. High 또는 Critical 경고는 영향받는 패키지가 실제 production 경로에 포함되는지 확인합니다.
3. 수정 버전의 release note와 breaking change를 읽습니다.
4. PR에서 웹 lint/test/build, Android unit test/assembleDebug가 모두 통과하는지 확인합니다.
5. 백업 후 수동 로그인·오프라인·동기화 테스트를 수행하고 직접 병합합니다.

자동 병합은 금지합니다. 일반적인 비보안 버전 업데이트는 개인용 운영 기준으로 연 1회 정도 한꺼번에 검토해도 되지만, High/Critical 및 Firebase/Auth/브라우저 보안 업데이트는 발견 시 우선 검토합니다.

## CI 보안 검사

GitHub Actions는 다음을 실행합니다.

- `npm ci`
- `npm run lint`
- `npm run test`
- `npm run build`
- `npm audit --audit-level=high`
- Android unit test 및 debug APK 빌드

`npm audit` 결과를 무조건 `--force`로 해결하지 않습니다. 실제 의존 경로와 breaking change를 검토합니다. lockfile을 수동으로 임의 편집하지 않습니다.

## Firestore Rules 변경

- `validNote`의 허용 8필드 목록을 유지합니다.
- catch-all deny를 유지합니다.
- 본인 UID 외 사용자를 추가하지 않습니다.
- 변경 전 Rules Playground 또는 Firestore Emulator에서 본인·다른 UID·미인증·추가 필드를 검사합니다.
- Hosting 배포와 Rules 배포를 분리합니다.
- 실제 UID가 들어간 규칙을 공개 저장소에 게시할지는 개인 식별자 노출 가능성을 고려해 결정합니다. 이 저장소에는 placeholder를 유지하고 배포 직전에 로컬에서 치환하는 방법이 가장 안전합니다.

## Google 계정과 Firebase

연 1회 또는 기기 변경 시 다음을 확인합니다.

- Google 계정 복구 이메일과 전화번호
- 패스키 및 2단계 인증 기기
- 분실한 로그인 세션과 기기
- Firebase 프로젝트 소유자 및 IAM 구성원
- Authentication 승인 도메인
- Firestore Rules 배포 이력
- Firebase Console의 Firestore 읽기·쓰기와 Spark 사용량

서비스 계정 키와 관리자 비밀키는 만들거나 웹 앱에 넣지 않습니다. Firebase Web config는 공개 클라이언트 식별 정보이며 Rules가 보안 경계입니다.

## 백업 원칙

- 최소 월 1회 또는 중요한 대량 수정 전 JSON 백업을 만듭니다.
- 백업 파일은 개인 Windows 계정의 암호화된 로컬 폴더 또는 신뢰하는 개인 백업 장치에 저장합니다.
- 동기화는 삭제와 손상도 전파하므로 백업을 대신하지 않습니다.
- 복원 전에 PWA 미리보기와 예상 쓰기 수를 확인합니다.
- 민감한 메모가 있는 백업을 공유 폴더나 공개 Git 저장소에 넣지 않습니다.

## 오프라인 캐시

영구 Firestore 캐시는 개인 PC에서만 사용합니다. 로그아웃은 IndexedDB 캐시를 자동 삭제하지 않습니다. PC 양도·수리·공용 전환 전에는 JSON 백업 후 브라우저 사이트 데이터와 Windows 사용자 프로필을 정리합니다. BitLocker 같은 디스크 암호화와 Windows 로그인 보호를 권장합니다.

## App Check

이번 버전에서는 App Check enforcement를 활성화하지 않습니다. 잘못 적용하면 Android와 PWA가 함께 차단될 수 있습니다. 향후 봇성 사용량이나 키 악용 징후가 있을 때 다음 순서로 검토합니다.

1. Firebase Console에서 App Check 지원 제공업체와 Android/PWA 등록 요구사항을 확인합니다.
2. enforcement 없이 모니터링 모드로 정상 요청 비율을 관찰합니다.
3. Android debug/release와 Firebase Hosting PWA를 각각 실제 기기에서 검증합니다.
4. 충분한 관찰 뒤 서비스별 enforcement를 단계적으로 적용합니다.
5. 차단 시 되돌릴 절차를 미리 기록합니다.

## 보안 헤더와 CSP

`firebase.json`은 clickjacking, MIME sniffing, referrer, 불필요한 카메라·마이크·위치 권한을 제한합니다. CSP에 `unsafe-eval`, 외부 CDN 또는 와일드카드 전체 허용을 추가하지 않습니다. Firebase 공식 origin 변경으로 로그인이 막히면 브라우저 오류의 정확한 origin만 검토합니다.

사용자 입력은 React의 일반 텍스트 입력과 렌더링만 사용합니다. `dangerouslySetInnerHTML`, 입력 HTML 실행, 외부 스크립트 CDN을 도입하지 않습니다.

## 로컬 폰트 유지관리

PWA와 Android는 저장소에 포함된 Cormorant Garamond 및 MaruBuri 정적 TTF만 사용합니다. 런타임 외부 폰트 요청이나 CDN은 허용하지 않습니다. 폰트 교체 시 `FONT_ASSETS_REQUIRED_KO.md`의 네 경로, 내장 메타데이터의 family/PostScript 이름과 400/600 굵기, `THIRD_PARTY_FONTS_LICENSES.md`의 저작권 고지를 함께 검토합니다. 기능 UI는 번들 폰트가 아닌 운영체제 기본 산세리프를 유지합니다.

## 사고 대응

1. Google 계정 또는 Firebase 프로젝트 접근이 의심되면 Google 세션과 Firebase IAM을 먼저 확인합니다.
2. Firestore Rules를 본인 UID 전용인지 재확인합니다.
3. Authentication 승인 도메인에서 모르는 도메인을 제거합니다.
4. 최근 JSON 백업을 별도 보존합니다.
5. GitHub Dependabot 및 Actions 로그를 확인합니다.
6. 원인을 확인하기 전 자동 업데이트나 Rules 확장을 하지 않습니다.
