#!/bin/bash
cd ~/web/Monitor-batt

echo "🚀 Removendo Gradle Wrapper e configurando Gradle normal..."

# 1. Remove wrapper
rm -f gradlew gradlew.bat
rm -rf gradle/wrapper

# 2. Corrige settings.gradle
cat > settings.gradle << 'EOF'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
        repositories {
                google()
                        mavenCentral()
                            }
                            }
                            rootProject.name = "BatteryMonitor"
                            include ':app'
                            EOF
                            
                            # 3. Corrige build.gradle (sem repositórios)
                            cat > build.gradle << 'EOF'
                            buildscript {
                                dependencies {
                                        classpath 'com.android.tools.build:gradle:8.5.0'
                                            }
                                            }
                                            tasks.register('clean', Delete) {
                                                delete rootProject.buildDir
                                                }
                                                EOF
                                                
                                                # 4. Cria workflow que usa 'gradle' (não ./gradlew)
                                                mkdir -p .github/workflows
                                                cat > .github/workflows/build.yml << 'YAML'
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
                                                                                                                        - name: Setup Gradle
                                                                                                                                uses: gradle/actions/setup-gradle@v4
                                                                                                                                      - name: Build APK
                                                                                                                                              run: gradle assembleDebug
                                                                                                                                                    - name: Upload APK
                                                                                                                                                            uses: actions/upload-artifact@v4
                                                                                                                                                                    with:
                                                                                                                                                                              name: app-debug
                                                                                                                                                                                        path: app/build/outputs/apk/debug/*.apk
                                                                                                                                                                                        YAML
                                                                                                                                                                                        
                                                                                                                                                                                        # 5. Remove qualquer outro workflow antigo
                                                                                                                                                                                        rm -f .github/workflows/build-android.yml 2>/dev/null
                                                                                                                                                                                        rm -f .github/workflows/android.yml 2>/dev/null
                                                                                                                                                                                        
                                                                                                                                                                                        echo "✅ Pronto. Agora enviando para o GitHub..."
                                                                                                                                                                                        
                                                                                                                                                                                        # 6. Commit e push
                                                                                                                                                                                        git add -A
                                                                                                                                                                                        git commit -m "Remove Gradle wrapper, usa gradle normal no workflow"
                                                                                                                                                                                        git push origin main
                                                                                                                                                                                        
cd ~/web/Monitor-batt

rm -f gradlew gradlew.bat
rm -rf gradle/wrapper

cat > settings.gradle << "EOF"
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
        repositories {
                google()
                        mavenCentral()
                            }
                            }
                            rootProject.name = "BatteryMonitor"
                            include ':app'
                            EOF
                            
                            cat > build.gradle << "EOF"
                            buildscript {
                                dependencies {
                                        classpath 'com.android.tools.build:gradle:8.5.0'
                                            }
                                            }
                                            tasks.register('clean', Delete) {
                                                delete rootProject.buildDir
                                                }
                                                EOF
                                                
                                                mkdir -p .github/workflows
                                                cat > .github/workflows/build.yml << "EOF"
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
                                                                                                                        - name: Setup Gradle
                                                                                                                                uses: gradle/actions/setup-gradle@v4
                                                                                                                                      - name: Build APK
                                                                                                                                              run: gradle assembleDebug
                                                                                                                                                    - name: Upload APK
                                                                                                                                                            uses: actions/upload-artifact@v4
                                                                                                                                                                    with:
                                                                                                                                                                              name: app-debug
                                                                                                                                                                                        path: app/build/outputs/apk/debug/*.apk
                                                                                                                                                                                        EOF
                                                                                                                                                                                        
                                                                                                                                                                                        rm -f .github/workflows/build-android.yml .github/workflows/android.yml
                                                                                                                                                                                        
                                                                                                                                                                                        git add -A
                                                                                                                                                                                        git commit -m "Remove wrapper, usa gradle normal"
                                                                                                                                                                                        git push origin main                                                                                                                                                                                        echo "🎉 Enviado! Acesse a aba Actions e veja o build passando."