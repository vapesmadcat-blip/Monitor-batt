cd ~/web/Monitor-batt

rm -f gradlew gradlew.bat
rm -rf gradle/wrapper

cat > settings.gradle << 'FIM'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BatteryMonitor"
include ':app'
FIM

cat > build.gradle << 'FIM'
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
    }
}
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
FIM

mkdir -p .github/workflows

cat > .github/workflows/build.yml << 'FIM'
name: Build APK

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
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
      - name: Build APK
        run: gradle assembleDebug
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
FIM


git add -A
git commit -m "Corrige repositórios e remove wrapper"
git push origin main
