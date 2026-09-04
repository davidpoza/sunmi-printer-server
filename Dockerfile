# syntax=docker/dockerfile:1
#
# Genera el APK de forma reproducible, sin necesitar el Android SDK en el host.
#
#   # Compilar y extraer el APK a ./dist/ (requiere BuildKit)
#   DOCKER_BUILDKIT=1 docker build --target export --output type=local,dest=./dist .
#
#   # Alternativa: construir la imagen completa y copiar el APK a mano
#   docker build --target build -t sunmi-printer-server:build .
#   id=$(docker create sunmi-printer-server:build)
#   docker cp "$id":/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk
#   docker rm "$id"

# ----------------------------------------------------------------------------
# Stage 1: entorno de build (JDK + Android SDK) y compilación
# ----------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive \
    GRADLE_USER_HOME=/root/.gradle

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Android command-line tools
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -fsSL -o /tmp/cmdline-tools.zip \
       "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

# Aceptar licencias e instalar SDK (deben coincidir con app/build.gradle.kts)
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
       "platform-tools" \
       "platforms;android-34" \
       "build-tools;34.0.0" > /dev/null

WORKDIR /workspace

# Capa cacheable: wrapper + scripts de build primero (descarga Gradle y dependencias)
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY app/build.gradle.kts ./app/build.gradle.kts
RUN ./gradlew --no-daemon :app:help > /dev/null 2>&1 || true

# Código fuente y compilación
COPY . .
RUN ./gradlew --no-daemon :app:assembleDebug

# ----------------------------------------------------------------------------
# Stage 2: artefacto (solo el APK)
# ----------------------------------------------------------------------------
FROM scratch AS export
COPY --from=build /workspace/app/build/outputs/apk/debug/app-debug.apk /app-debug.apk
