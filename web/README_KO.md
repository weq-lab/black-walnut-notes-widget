# Black Walnut Notes Windows PWA

Android Black Walnut Notes와 같은 Firebase 프로젝트 및 Google 계정을 사용하는 Windows용 설치형 웹 앱입니다. 서버나 Cloud Functions 없이 `users/{uid}/notes/{noteId}` 문서만 사용합니다.

## 준비

1. [Node.js](https://nodejs.org/) 24 LTS 이상을 설치합니다. Node 설치에는 `npm`이 포함됩니다.
2. 저장소의 `web` 폴더에서 `npm ci`를 실행합니다.
3. `FIREBASE_WEB_SETUP_KO.md`에 따라 Firebase Web App을 등록합니다.
4. `.env.example`을 `.env.local`로 복사하고 여섯 값을 입력합니다. `.env.local`은 Git에서 제외됩니다.

Android의 `google-services.json`은 웹 설정 파일이 아니므로 복사하거나 파싱하지 않습니다.

## 로컬 실행과 검증

```powershell
cd web
npm ci
npm run dev
npm run lint
npm run test
npm run build
npm audit --audit-level=high
```

개발 서버가 안내한 로컬 주소를 Edge 또는 Chrome에서 엽니다. production source map은 생성하지 않습니다. Firebase 설정이 빠지면 빈 화면 대신 누락된 환경변수 목록이 나타납니다.

## Windows에 설치

### Microsoft Edge

1. Firebase Hosting 주소를 Edge에서 엽니다.
2. 주소창 오른쪽의 **앱 사용 가능** 아이콘 또는 **… → 앱 → 이 사이트를 앱으로 설치**를 선택합니다.
3. 설치 후 작업표시줄 아이콘을 우클릭해 **작업 표시줄에 고정**합니다.
4. 시작 메뉴에서 앱을 찾을 수 있으며 독립 창과 Alt+Tab 항목으로 실행됩니다.

Chrome에서는 주소창의 설치 아이콘을 사용합니다. 설치 버튼이 보이지 않으면 HTTPS, manifest, 서비스 워커 및 아이콘 응답을 확인합니다.

Windows 시작 시 자동 실행은 Edge의 `edge://apps`에서 Black Walnut Notes의 세부 정보를 열고 **기기 로그인 시 자동 시작**을 켭니다. 항상 위에 둘 필요가 있다면 Microsoft PowerToys의 **Always On Top** 기능을 선택적으로 사용합니다.

브라우저는 창 크기를 자체적으로 복원합니다. 앱은 마지막으로 열었던 `noteId`를 `localStorage`에 저장합니다.

## 로그인과 개인 PC 선택

- Firebase Authentication의 Google 로그인만 사용합니다.
- 로그인 전에는 Firestore를 읽거나 쓰지 않습니다.
- 첫 로그인 때 **개인 PC**를 고르면 Firestore `persistentLocalCache`와 다중 탭 관리자를 사용합니다.
- **공용 또는 임시 PC**를 고르면 `memoryLocalCache`를 사용합니다.
- 캐시 선택은 이 브라우저의 로컬 설정입니다.
- 로그아웃만으로 IndexedDB 영구 캐시가 삭제되지는 않습니다.
- 영구 캐시를 끄거나 지우려면 Edge/Chrome의 사이트 설정에서 해당 Firebase Hosting 사이트의 저장된 데이터를 삭제하고 다시 로그인해 공용 PC 모드를 선택합니다.

Firestore 오프라인 캐시는 브라우저 수준의 로컬 저장소이며 앱 자체 암호화를 추가하지 않았습니다. 개인 Windows 계정과 디스크 암호화가 적용된 개인 PC에서만 영구 캐시를 사용하세요.

## 저장과 실시간 동기화

- 계정당 `users/{uid}/notes` 컬렉션 리스너 하나만 유지합니다.
- 검색과 정렬은 이미 받은 노트에서 로컬로 수행합니다.
- 입력이 멈춘 뒤 500ms에 변경 문서 하나만 저장합니다.
- 동일한 내용은 다시 쓰지 않습니다.
- 노트 전환, 로그아웃, `Ctrl+S`, 창이 숨겨질 때 대기 중 저장을 즉시 큐에 넣습니다.
- 오프라인 쓰기는 Firestore 로컬 큐에 보관되고 연결 복구 후 전송됩니다.
- 자신의 pending write 또는 metadata-only 갱신은 편집 내용을 다시 저장하지 않습니다.
- 원격 변경 중 로컬 초안이 있으면 자동으로 덮지 않고 충돌 배너를 표시합니다.

Android 앱 실행 중에는 기존 실시간 리스너로 Windows 변경이 반영됩니다. Android 앱 프로세스가 종료된 상태에서는 WorkManager가 네트워크 연결 시 약 1시간 주기로 확인합니다. 즉시 확인하려면 Android 위젯의 새로고침 버튼을 누릅니다. Android 절전 정책 때문에 정확히 1시간 또는 즉시 실행을 보장하지 않습니다.

## 키보드

- `Ctrl+N`: 새 노트
- `Ctrl+F`: 검색창
- `Ctrl+S`: 즉시 저장
- `Esc`: 좁은 창 편집기에서 목록으로 이동
- 체크리스트 입력에서 `Alt+↑`, `Alt+↓`: 항목 순서 변경

## 백업과 복원

설정에서 다음을 실행할 수 있습니다.

- **JSON 전체 백업**: 현재 메모를 허용된 8개 필드, 백업 생성 시각, 포맷 버전과 함께 로컬 다운로드합니다.
- **Markdown 내보내기**: 제목, 본문 줄바꿈, `- [ ]`/`- [x]` 체크리스트가 포함된 단일 Markdown 파일을 다운로드합니다.
- **JSON 복원**: 실제 쓰기 전에 유효·무효 노트와 예상 쓰기 횟수를 표시합니다. 같은 `noteId`는 건너뛰기, 최신 `updatedAt`, 새 ID 복사 중 선택합니다.

파일은 브라우저에서 직접 생성하며 서버나 Cloud Storage에 올리지 않습니다. 동기화는 실수 삭제까지 동기화하므로 정기 JSON 백업을 권장합니다.

## 큰 노트

직렬화된 UTF-8 크기가 약 800KiB 이상이면 분할 권고가 나타납니다. 약 950KiB 이상이면 Firestore 저장을 차단하고 현재 초안을 브라우저에 유지합니다. JSON 또는 Markdown으로 내보낸 뒤 여러 노트로 나누세요.

## PWA 업데이트

서비스 워커는 같은 출처의 앱 셸과 정적 파일만 캐시합니다. Firestore, Google 로그인, Google API 응답은 캐시하지 않습니다. 새 버전이 설치 대기 상태가 되면 **업데이트 가능** 안내가 나타납니다. **저장 후 적용**을 누르면 대기 중 초안을 먼저 저장 큐에 넣고 새 서비스 워커를 활성화합니다.

문제가 반복되면 사이트 데이터에서 서비스 워커와 캐시를 삭제한 뒤 다시 접속합니다. 영구 Firestore 캐시도 함께 삭제될 수 있으므로 먼저 JSON 백업을 만드세요.

## Firebase Hosting 배포

저장소 루트에서 다음을 실행합니다.

```powershell
cd web
npm ci
npm run build
cd ..
firebase login
firebase use YOUR_FIREBASE_PROJECT_ID
firebase deploy --only hosting
```

실제 배포 전에 `firestore.rules`의 UID placeholder를 본인 UID로 교체해야 합니다. 규칙 배포는 별도 승인과 검증 후 `firebase deploy --only firestore:rules`로 수행합니다. Hosting만 배포하는 명령은 Firestore Rules를 변경하지 않습니다.

## 오류 해결

- **Google 로그인 창이 닫힘/차단됨**: 팝업을 허용합니다. 차단 또는 팝업 미지원 환경은 redirect 방식으로 자동 전환됩니다.
- **승인되지 않은 도메인**: Firebase Console → 빌드 → Authentication → 설정 → 승인된 도메인에 Hosting 도메인을 추가합니다.
- **Firestore 권한 오류**: 로그인한 Google 계정의 UID와 `firestore.rules`의 허용 UID가 같은지 확인합니다.
- **UID 규칙 오류**: 다른 Firebase 프로젝트의 UID를 넣지 않았는지, 따옴표를 유지했는지 확인합니다.
- **오프라인에서 노트가 없음**: 해당 PC에서 온라인 로그인과 최초 동기화를 한 적이 있어야 합니다. 개인 PC 모드인지 확인합니다.
- **서비스 워커 업데이트가 안 됨**: 모든 앱 창을 닫고 다시 열거나 사이트의 서비스 워커를 제거합니다.
- **사용자 HEX 색상이 다름**: 현재 Firestore는 프리셋 이름만 저장합니다. `HEX 직접 입력`의 실제 색상 값은 동기화되지 않아 웹에서 블랙 월넛으로 대체합니다.

## 무료 사용량 확인

Firebase Console → **사용량 및 결제**와 Firestore → **사용량**에서 읽기·쓰기 추이를 확인합니다. 개발 모드 화면 오른쪽 아래의 `L/R/W` 카운터는 활성 리스너, snapshot, 쓰기 호출을 간단히 보여줍니다. 운영 화면에는 표시되지 않습니다. 짧은 polling, 노트별 리스너, 서버 검색, 분석, Cloud Functions는 사용하지 않습니다.
