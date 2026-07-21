# 본인 계정 전용 Firestore 잠금

현재 저장소의 `firestore.rules`는 개인 한 명만 허용하도록 placeholder를 포함합니다. 실제 UID를 추측하거나 임의의 값으로 배포하면 안 됩니다.

## 1. 본인 UID 확인

1. Firebase Console에서 Android와 Web App이 등록된 정확한 프로젝트를 엽니다.
2. 왼쪽 **빌드 → Authentication**으로 이동합니다.
3. **사용자** 탭을 엽니다.
4. Android 앱 또는 PWA에서 사용할 Google 계정으로 한 번 로그인합니다.
5. 사용자 목록에서 해당 이메일을 찾습니다.
6. 행의 **사용자 UID**를 복사합니다. 이메일 주소나 Google 계정 숫자가 아니라 Firebase Authentication UID여야 합니다.

## 2. 규칙 교체

`firestore.rules`의 다음 문자열 한 곳을 찾습니다.

```text
REPLACE_WITH_YOUR_FIREBASE_UID
```

따옴표는 유지하고 문자열만 Firebase Console에서 복사한 UID로 바꿉니다.

```rules
function isAllowedUser() {
  return request.auth != null
      && request.auth.uid == "복사한_실제_UID";
}

function ownsUserPath(uid) {
  return isAllowedUser()
      && request.auth.uid == uid;
}
```

`validNote`의 8개 필드 제한과 catch-all deny 규칙은 제거하지 않습니다.

> 경고: placeholder를 실제 UID로 교체하고 테스트하기 전에는 규칙을 배포하지 마세요. placeholder 상태를 배포하면 Android와 Windows 모두 접근이 거부됩니다. 잘못된 UID를 배포해도 동일합니다.

## 3. Rules Playground 검사

Firebase Console → Firestore Database → **규칙** → **Rules Playground**에서 다음을 검사합니다.

- 본인 UID 인증, `/users/본인UID/notes/test-id` 읽기: 허용
- 본인 UID 인증, `/users/다른UID/notes/test-id` 읽기: 거부
- 다른 UID 인증, `/users/다른UID/notes/test-id` 읽기: 거부
- 미인증 읽기/쓰기: 거부
- 허용된 8개 필드의 create/update: 허용
- `ownerUid`, `deleted` 같은 추가 필드가 있는 쓰기: 거부
- 문서 ID와 `noteId`가 다른 쓰기: 거부

Playground의 create/update 데이터에는 `noteId`, `title`, `body`, `checklist`, `createdAt`, `updatedAt`, `colorPreset`, `schemaVersion: 1`을 모두 넣습니다.

## 4. Emulator 검사 선택사항

Firebase CLI 설치 후 운영 데이터를 건드리지 않고 Rules Emulator를 사용할 수 있습니다.

```powershell
firebase emulators:start --only firestore
```

Emulator 테스트에서도 허용 UID와 다른 UID, 미인증, 추가 필드, 잘못된 noteId를 각각 검사합니다. 저장소의 기본 자동 테스트는 운영 Firestore에 연결하지 않습니다.

## 5. 배포

검사와 UID 확인을 마친 뒤에만 다음을 실행합니다.

```powershell
firebase use YOUR_FIREBASE_PROJECT_ID
firebase deploy --only firestore:rules
```

규칙 배포는 Android와 PWA에 즉시 영향을 줍니다. Hosting 배포와 분리해 수행하세요.

## 6. Android·Windows 실제 로그인 확인

1. Android 앱에서 같은 Google 계정으로 로그인합니다.
2. 노트 목록이 동기화되는지 확인합니다.
3. Windows PWA에서 같은 이메일로 로그인합니다.
4. Windows에서 테스트 노트를 만들고 Android 앱 실행 중 실시간 반영을 확인합니다.
5. Android에서 수정하고 Windows 반영을 확인합니다.
6. 다른 Google 계정으로 PWA 로그인을 시도하면 `permission-denied`가 발생하고 노트가 보이지 않아야 합니다.

이 규칙은 협업이나 여러 사용자를 위한 것이 아닙니다. 허용 UID를 둘 이상으로 늘리지 않습니다.
