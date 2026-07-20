# Firebase 설정 안내

이 저장소는 Firebase 설정이 없어도 로컬 전용 모드로 빌드되고 실행됩니다. Firebase 프로젝트와 인증 정보는 사용자가 직접 Firebase Console에서 준비해야 합니다. `google-services.json`은 Git에서 제외되어 있으며 저장소에 커밋하면 안 됩니다.

## 1. Firebase 프로젝트 만들기

1. [Firebase Console](https://console.firebase.google.com/)에 Google 계정으로 로그인합니다.
2. **프로젝트 만들기**를 누릅니다.
3. 프로젝트 이름을 입력하고 **계속**을 누릅니다.
4. Google 애널리틱스는 이 앱에 필수가 아닙니다. 필요에 따라 사용 여부를 선택합니다.
5. **프로젝트 만들기**를 눌러 생성이 끝날 때까지 기다립니다.

## 2. Android 앱 등록하기

Firebase Console의 **프로젝트 개요**에서 Android 아이콘을 눌러 앱을 추가합니다.

- 배포용 패키지명: `com.blackwalnut.noteswidget`
- 현재 debug APK 테스트용 패키지명: `com.blackwalnut.noteswidget.debug`

이 프로젝트의 `debug` 빌드는 `.debug` 접미사를 사용합니다. 두 기기에서 GitHub Actions의 debug APK를 테스트하려면 `com.blackwalnut.noteswidget.debug` 앱을 반드시 등록해야 합니다. 이후 release 빌드도 사용할 계획이면 `com.blackwalnut.noteswidget` 앱도 별도로 등록합니다.

앱 닉네임은 자유롭게 입력하고 **앱 등록**을 누릅니다.

## 3. SHA-1과 SHA-256 등록하기

프로젝트 루트에서 다음 명령을 실행합니다.

```powershell
./gradlew signingReport
```

Windows에서 위 명령이 실행되지 않으면 `./gradlew.bat signingReport`를 사용합니다. 출력의 `Variant: debug` 아래에 있는 `SHA1`과 `SHA-256` 값을 복사합니다.

Firebase Console에서:

1. 왼쪽 위 톱니바퀴 → **프로젝트 설정** → **일반**을 엽니다.
2. **내 앱**에서 등록한 Android 앱을 선택합니다.
3. **SHA 인증서 지문** → **지문 추가**를 누릅니다.
4. SHA-1과 SHA-256을 각각 등록합니다.

두 패키지를 모두 등록했다면 각 Android 앱 설정에 필요한 지문을 등록합니다. Google 로그인을 사용하려면 최소 SHA-1이 필요하며, 배포·검증 호환성을 위해 SHA-256도 함께 등록해야 합니다.

## 4. google-services.json 배치하기

Android 앱 설정 화면에서 **google-services.json 다운로드**를 누릅니다. 여러 Android 클라이언트를 등록한 뒤 다시 다운로드하면 해당 프로젝트의 클라이언트 정보가 함께 포함됩니다.

파일명을 변경하지 말고 다음 위치에 놓습니다.

```text
BlackWalnutNotesWidget/app/google-services.json
```

정확한 경로는 앱 모듈의 `build.gradle`과 같은 폴더입니다. 이 파일은 `.gitignore`에 포함되어 있으므로 `git add -f`로 강제 추가하지 마세요.

파일이 없으면 `BuildConfig.FIREBASE_CONFIGURED`가 `false`가 되고 로그인·동기화 버튼은 로컬 전용 안내 상태로 유지됩니다. 파일이 있으면 Google Services 플러그인이 자동 적용됩니다.

## 5. Google 로그인 활성화하기

1. Firebase Console 왼쪽 메뉴에서 **빌드** → **Authentication**을 엽니다.
2. 처음이라면 **시작하기**를 누릅니다.
3. **로그인 방법** 탭에서 **Google**을 선택합니다.
4. **사용 설정**을 켭니다.
5. 프로젝트 공개 이름과 프로젝트 지원 이메일을 확인한 뒤 **저장**을 누릅니다.
6. Android 앱 설정으로 돌아가 최신 `google-services.json`을 다시 다운로드해 `app/google-services.json`을 교체합니다.

이 과정에서 생성되는 **웹 애플리케이션 OAuth 클라이언트 ID**가 Credential Manager의 서버 클라이언트 ID로 사용됩니다. 코드에 클라이언트 ID를 직접 입력하지 않습니다.

## 6. Cloud Firestore 만들기

1. Firebase Console 왼쪽 메뉴에서 **빌드** → **Firestore Database**를 엽니다.
2. **데이터베이스 만들기**를 누릅니다.
3. 위치를 선택합니다. 데이터베이스 위치는 나중에 변경하기 어려우므로 사용자와 가까운 리전을 선택합니다.
4. 초기 보안 모드는 **프로덕션 모드에서 시작**을 선택합니다.
5. **만들기**를 누릅니다.

앱은 다음 경로만 사용합니다.

```text
users/{uid}/notes/{noteId}
```

## 7. 보안 규칙 배포하기

저장소의 `firestore.rules`는 로그인한 사용자가 자신의 UID 경로만 읽고 쓰도록 제한합니다. 다른 사용자의 경로와 그 밖의 모든 문서 접근은 거부합니다.

Firebase Console에서 직접 배포하려면:

1. **Firestore Database** → **규칙** 탭을 엽니다.
2. 저장소의 `firestore.rules` 전체 내용을 붙여넣습니다.
3. **게시**를 누릅니다.

Firebase CLI를 사용할 수도 있습니다.

```powershell
npm install -g firebase-tools
firebase login
firebase use --add
firebase deploy --only firestore:rules,firestore:indexes
```

`firebase use --add`에서 직접 만든 Firebase 프로젝트를 선택합니다. 저장소의 `firestore.indexes.json`에는 현재 필요한 복합 인덱스가 없음을 명시해 두었습니다.

## 8. 빌드하고 두 기기에서 확인하기

```powershell
./gradlew test
./gradlew assembleDebug
```

생성 APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. Google Play 서비스와 Google 계정이 있는 두 Android 기기에 같은 Firebase 설정으로 빌드한 APK를 설치합니다.

1. 두 기기에서 같은 Google 계정으로 로그인합니다.
2. 기존 로컬 노트가 있다면 한 기기에서 **로컬 노트 가져오기**를 누릅니다.
3. 한 기기에서 노트나 체크리스트를 수정합니다.
4. 다른 기기에서 실시간으로 변경이 반영되는지 확인합니다.
5. 비행기 모드에서 수정한 뒤 네트워크를 복구하여 변경이 동기화되는지 확인합니다.

Room은 항상 로컬 캐시로 남습니다. 로그아웃은 Firebase·Credential Manager 세션만 종료하며 로컬 노트를 삭제하지 않습니다. 충돌 시 기본적으로 `updatedAt`이 더 최신인 내용을 적용하고, 미전송 로컬 변경이 덮일 경우 `sync_conflicts` Room 테이블에 로컬 스냅샷을 기록합니다.

## 참고

- [Firebase Android 프로젝트 설정](https://firebase.google.com/docs/android/setup)
- [Firebase Authentication과 Credential Manager를 이용한 Google 로그인](https://firebase.google.com/docs/auth/android/google-signin)
- [Cloud Firestore 실시간 리스너](https://firebase.google.com/docs/firestore/query-data/listen)
- [Cloud Firestore 오프라인 데이터](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
