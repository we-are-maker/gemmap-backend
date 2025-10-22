# gemmap-backend

Gemmap 프로젝트의 백엔드 서비스입니다. Gradle 멀티모듈 구조로 마이크로서비스 아키텍처를 구현하고 있습니다.

## 프로젝트 구조

```
gemmap-backend/
├── .github/                   # GitHub 설정 및 CI/CD
│   ├── workflows/             # GitHub Actions 워크플로
│   │   ├── build-user-service.yml
│   │   ├── build-spot-service.yml
│   │   └── release-charts.yml
│   ├── ISSUE_TEMPLATE/          # 이슈 템플릿
│   ├── commit-message.txt       # 커밋 템플릿
│   └── PULL_REQUEST_TEMPLATE.md # PR 템플릿
│
├── charts/                    # Helm 차트
│   ├── user-service/
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   └── spot-service/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│
├── k8s/                       # Kubernetes 매니페스트
│   ├── base/                  # 기본 리소스
│   │   ├── ingress/
│   │   ├── user-service/
│   │   └── spot-service/
│   ├── overlays/              # 환경별 오버레이
│   │   └── dev/
│   ├── releases/              # 릴리스 매니페스트
│   └── secrets/               # 시크릿 (gitignore)
│
├── libs/                      # 공통 라이브러리
│   ├── common-core/           # 핵심 공통 기능
│   │   ├── build.gradle
│   │   └── src/
│   └── common-security-jwt/   # JWT 보안 기능
│       ├── build.gradle
│       └── src/
│
├── services/                  # 마이크로서비스
│   ├── user-service/          # 사용자 서비스
│   │   ├── build.gradle
│   │   ├── Dockerfile
│   │   └── src/
│   └── spot-service/          # 스팟 서비스
│       ├── build.gradle
│       ├── Dockerfile
│       └── src/
│
├── build.gradle                # 루트 빌드 설정
├── settings.gradle             # 멀티모듈 설정
├── gradlew                     # Gradle Wrapper (Unix)
├── gradlew.bat                 # Gradle Wrapper (Windows)
└── .env                        # 환경 변수 (gitignore)
```

## 기술 스택

- **언어**: Java 17
- **빌드 도구**: Gradle (Multi-module)
- **컨테이너**: Docker
- **오케스트레이션**: Kubernetes
- **패키지 관리**: Helm
- **CI/CD**: GitHub Actions

## 빌드 및 실행

### 로컬 빌드
```bash
# 전체 프로젝트 빌드
./gradlew build

# 특정 서비스 빌드
./gradlew :services:user-service:build
./gradlew :services:spot-service:build
```

### Docker 빌드
```bash
# User Service
docker build -t user-service:latest ./services/user-service

# Spot Service
docker build -t spot-service:latest ./services/spot-service
```

## 배포

### Helm 차트를 통한 배포
```bash
# User Service 배포
helm install user-service ./charts/user-service

# Spot Service 배포
helm install spot-service ./charts/spot-service
```

### Kubernetes 매니페스트를 통한 배포
```bash
# Kustomize를 사용한 배포 (개발 환경)
kubectl apply -k k8s/overlays/dev
```

## CI/CD 파이프라인

- **빌드 파이프라인**: `build-user-service.yml`, `build-spot-service.yml`
  - 코드 체크아웃
  - Gradle 빌드
  - Docker 이미지 빌드 및 푸시

- **차트 릴리스 파이프라인**: `release-charts.yml`
  - 서비스 빌드 완료 후 자동 실행
  - Helm 차트 패키징 및 릴리스
  - NHN Cloud Pipeline 트리거