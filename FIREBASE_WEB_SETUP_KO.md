# Firebase Web App 설정

이 문서는 기존 Android 앱이 사용하는 동일한 Firebase 프로젝트에 Windows PWA용 Web App을 추가하는 절차입니다. 서비스 계정 키나 관리자 비밀키는 필요하지 않습니다.

## 1. Web App 등록

1. [Firebase Console](https://console.firebase.google.com/)에서 Android 앱과 연결된 `blackwalnut` 프로젝트를 엽니다.
2. 왼쪽 위 톱니바퀴 → **프로젝트 설정** → **일반**으로 이동합니다.
3. **내 앱**에서 웹 아이콘 `</>`을 선택합니다.
4. 앱 닉네임(예: `Black Walnut Notes Windows`)을 입력합니다.
5. Hosting은 여기서 바로 설정해도 되고 나중에 CLI로 설정해도 됩니다.
6. **앱 등록**을 누릅니다.
7. 표시되는 `firebaseConfig`에서 다음 여섯 값만 확인합니다. 코드에 직접 붙이지 않습니다.

## 2. 환경변수

`web/.env.example`을 `web/.env.local`로 복사하고 값을 채웁니다.

```dotenv
VITE_FIREBASE_API_KEY=...
VITE_FIREBASE_AUTH_DOMAIN=YOUR_PROJECT.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=...
VITE_FIREBASE_STORAGE_BUCKET=...
VITE_FIREBASE_MESSAGING_SENDER_ID=...
VITE_FIREBASE_APP_ID=...
```

Vite의 `VITE_` 값은 브라우저 번들에 포함되는 클라이언트 설정입니다. 비밀키로 취급하지 않으며 실제 접근 통제는 Firebase Authentication과 Firestore Rules가 담당합니다. `.env.local`은 Git에서 제외되어 있습니다. Android의 `app/google-services.json`을 재사용하지 않습니다.

## 3. Google 로그인

1. Firebase Console → **빌드 → Authentication**을 엽니다.
2. **Sign-in method** 또는 **로그인 방법**에서 **Google**을 선택합니다.
3. 사용 설정을 켜고 프로젝트 지원 이메일을 선택한 뒤 저장합니다.
4. **설정 → 승인된 도메인**에 다음을 확인합니다.
   - 로컬 테스트용 `localhost`
   - `YOUR_PROJECT.web.app`
   - `YOUR_PROJECT.firebaseapp.com`
   - 별도 커스텀 도메인을 쓴다면 그 도메인

팝업 차단 환경에서는 앱이 redirect 로그인으로 전환합니다. `auth/unauthorized-domain` 오류가 나면 위 승인 도메인을 먼저 확인합니다.

## 4. Firestore와 개인 UID 잠금

Android에서 이미 Firestore를 사용 중이면 같은 데이터베이스를 유지합니다. 새 데이터베이스를 만들지 않습니다. 경로는 항상 다음과 같습니다.

```text
users/{로그인한 uid}/notes/{noteId}
```

`FIRESTORE_PERSONAL_LOCKDOWN_KO.md`에 따라 본인 UID를 확인하고 로컬 `firestore.rules`의 `REPLACE_WITH_YOUR_FIREBASE_UID`를 교체합니다. placeholder 상태에서는 어떤 실제 사용자도 허용되지 않습니다. 규칙은 사용자 확인 없이 배포하지 않습니다.

## 5. 로컬 확인

```powershell
cd web
npm ci
npm run dev
```

브라우저에서 Google 로그인 후 다음을 확인합니다.

- 계정 이메일이 표시되는지
- Android에서 만든 노트가 나타나는지
- Windows에서 수정한 제목·본문·체크리스트·프리셋이 Android에 반영되는지
- 브라우저 개발자 도구 콘솔에 `permission-denied` 또는 CSP 오류가 없는지

운영 Firestore에 자동 테스트 데이터를 만들지 않습니다. 수동 테스트 노트는 별도 제목으로 만들고 완료 후 직접 삭제합니다.

## 6. Firebase Hosting

Firebase CLI가 없다면 Node 설치 후 다음으로 설치합니다.

```powershell
npm install -g firebase-tools
firebase login
firebase use YOUR_FIREBASE_PROJECT_ID
```

빌드 및 Hosting만 배포합니다.

```powershell
cd web
npm ci
npm run build
cd ..
firebase deploy --only hosting
```

`firebase.json`은 `web/dist`를 배포하고 SPA rewrite 및 보안 헤더를 적용합니다. 기존 Firestore rules/indexes 설정도 유지합니다. `--only hosting`은 규칙을 배포하지 않습니다.

## 7. 보안 헤더 확인

배포 후 브라우저 개발자 도구의 Network 탭에서 문서 응답에 CSP, `nosniff`, Referrer-Policy, Permissions-Policy, clickjacking 방지 헤더가 있는지 확인합니다. Google 로그인이나 Firestore 연결이 CSP에 막히면 임의로 `*` 또는 `unsafe-eval`을 추가하지 말고 실제 차단된 Firebase 공식 origin만 검토합니다.

## 사용자가 한 번만 입력할 값

1. Firebase Web App의 여섯 환경변수
2. Firebase Authentication Google 제공업체와 승인 도메인
3. 본인 Firebase Authentication UID
4. Firebase CLI에서 사용할 기존 프로젝트 ID

실제 UID, `.env.local`, 서비스 계정 키, `google-services.json`은 커밋하지 않습니다.
