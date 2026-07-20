# Black Walnut Notes Widget

검정·다크브라운 테마에 맞춘 Android 메모 위젯입니다. 선택한 `.md` 또는 `.txt` 파일 하나를 읽어 표시합니다.

## 보안 설계

- `INTERNET` 권한 없음
- `MANAGE_EXTERNAL_STORAGE` 없음
- 사진·연락처·문자·위치·마이크·카메라 권한 없음
- Android 파일 선택기에서 사용자가 직접 선택한 파일만 접근
- 앱 백업 비활성화
- 30분 주기 자동 갱신 + 위젯의 수동 새로고침

## 기본 색상

- 배경: `#000000`
- 제목: `#5A3021`
- 본문: `#3A2017`
- 강조: `#D1AE6F`

본문이 너무 어두우면 설정에서 **가독성** 프리셋을 누르세요.

## 지원 문법

```markdown
# 제목
- [ ] 미완료
- [x] 완료
- 일반 목록
**굵은 글자**
<span style="color:#D1AE6F">색상 글자</span>
```

## Android Studio에서 APK 만들기

1. Windows에 Android Studio를 설치합니다.
2. 이 폴더를 엽니다.
3. Gradle 동기화가 끝날 때까지 기다립니다.
4. 상단 메뉴에서 `Build > Build Bundle(s) / APK(s) > Build APK(s)`를 누릅니다.
5. 완성 파일: `app/build/outputs/apk/debug/app-debug.apk`

최종 개인용 배포본은 Android Studio의 `Build > Generate Signed App Bundle or APK`에서 **APK**를 선택하고 본인 키로 서명하는 것을 권장합니다.

## GitHub Actions로 무료 빌드

1. GitHub에 새 비공개 저장소를 만듭니다.
2. 이 프로젝트의 모든 파일을 업로드합니다. `.github` 폴더도 포함해야 합니다.
3. `Actions > APK 빌드 > Run workflow`를 누릅니다.
4. 완료된 실행의 `Artifacts`에서 `black-walnut-notes-debug-apk`를 받습니다.

## 휴대폰 적용

1. APK를 휴대폰에 옮겨 설치합니다.
2. 앱을 열고 **샘플 메모 파일 만들기**를 눌러 `home.md`를 만듭니다.
3. 홈 화면 빈 곳을 길게 누릅니다.
4. `위젯 > Black Walnut Notes > Black Walnut 메모`를 추가합니다.
5. 메모 파일과 색상·글자 크기를 선택하고 저장합니다.
6. 본문을 누르면 연결된 편집 앱으로 열리고, ↻를 누르면 즉시 다시 읽습니다.

## Windows ↔ Android 무료 동기화 권장 구조

```text
Windows OneDrive\BlackWalnutNotes\home.md
              ↕ OneDrive
Android /Documents/BlackWalnutNotes/home.md
              ↕ FolderSync 양방향 동기화
위젯 + Markor가 Android 로컬 파일 사용
```

동시에 양쪽에서 같은 파일을 수정하면 충돌본이 생길 수 있으므로, 한쪽 수정이 동기화된 뒤 다른 쪽에서 편집하세요.

## 색상 프리셋

위젯 설정 화면의 **색상 프리셋**에서 다음 조합을 바로 선택할 수 있습니다.

- 블랙 월넛
- 다크 브라운
- 앤티크 브론즈
- 앤티크 골드
- 딥 앰버
- 번트 코퍼
- 골드 가독성

프리셋을 고른 뒤에도 배경·제목·본문·강조 색상의 HEX 값을 직접 수정할 수 있으므로, 스마트 런처나 One UI 테마에 맞춰 미세 조정할 수 있습니다. 설정은 위젯마다 따로 저장됩니다.

