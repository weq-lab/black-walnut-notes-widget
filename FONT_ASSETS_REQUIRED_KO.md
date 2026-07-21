# 로컬 폰트 자산 배치 현황

외부 네트워크 없이 저장소 루트의 로컬 원본에서 아래 네 정적 TTF만 선택해 배치했습니다. 원본 폴더는 변경하거나 삭제하지 않으며 Git과 빌드 입력에서 제외합니다.

| 역할 | 메타데이터 굵기 | 원본 파일 크기 |
|---|---:|---:|
| Cormorant Garamond Regular | 400 | 666,632 bytes |
| Cormorant Garamond SemiBold | 600 | 666,700 bytes |
| MaruBuri Regular | 400 | 3,268,988 bytes |
| MaruBuri SemiBold | 600 | 3,277,140 bytes |

## Android 최종 경로

```text
app/src/main/res/font/cormorant_garamond_regular.ttf
app/src/main/res/font/cormorant_garamond_semibold.ttf
app/src/main/res/font/maruburi_regular.ttf
app/src/main/res/font/maruburi_semibold.ttf
```

## Windows PWA 최종 경로

```text
web/src/assets/fonts/cormorant-garamond/CormorantGaramond-Regular.ttf
web/src/assets/fonts/cormorant-garamond/CormorantGaramond-SemiBold.ttf
web/src/assets/fonts/maruburi/MaruBuri-Regular.ttf
web/src/assets/fonts/maruburi/MaruBuri-SemiBold.ttf
```

PWA는 위 TTF를 Vite 해시 자산으로만 번들합니다. 기능 UI는 별도 번들 폰트 없이 Windows/Android 시스템 산세리프를 사용합니다.
