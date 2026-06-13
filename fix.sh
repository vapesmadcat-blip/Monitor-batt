#!/bin/bash
cd ~/web/Monitor-batt

echo "🔧 Corrigindo configurações do Gradle..."

# 1. Backup
mkdir -p backup_$(date +%Y%m%d)
cp build.gradle backup_/ 2>/dev/null
cp settings.gradle backup_/ 2>/dev/null
cp .github/workflows/*.yml backup_/ 2>/dev/null

# 2. settings.gradle correto
cat > settings.gradle << 'SETTINGS'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BatteryMonitor"
include ':app'
SETTINGS

# 3. build.gradle mínimo (sem repositórios)
cat > build.gradle << 'BUILD'
buildscript {
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
    }
}
tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
BUILD

# 4. Remover Gradle Wrapper problemático
rm -f gradlew gradlew.bat
rm -rf gradle/wrapper
echo "✅ Wrapper removido (vamos usar Gradle instalado diretamente)"

# 5. Criar/sobrescrever workflow do GitHub Actions (usa gradle, não ./gradlew)
mkdir -p .github/workflows
cat > .github/workflows/build.yml << 'WORKFLOW'
name: Build APK with Gradle (no wrapper)

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle (install)
        uses: gradle/actions/setup-gradle@v4
        # Isso instala o Gradle e disponibiliza o comando 'gradle' no PATH

      - name: Build debug APK
        run: gradle assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
WORKFLOW

echo "✅ Workflow atualizado (usa 'gradle' em vez de './gradlew')"

# 6. Git commit e push (pergunta)
echo ""
echo "📦 Pronto para enviar ao GitHub."
read -p "Deseja fazer commit e push agora? (s/N) " -n 1 -r
echo
if [[ $REPLY =~ ^[Ss]$ ]]; then
    git add -A
    git commit -m "Troca Gradle wrapper por Gradle normal + corrige repositórios"
    git push origin main
    echo "✅ Enviado! Acesse a aba Actions do GitHub e veja o build."
else
    echo "OK. Execute manualmente:"
    echo "  git add -A"
    echo "  git commit -m 'Troca Gradle wrapper por Gradle normal'"
    echo "  git push origin main"
fi