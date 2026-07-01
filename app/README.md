# Monitor-batt

Aplicativo Android (Java) que monitora a bateria em background e emite um **bip a cada 30 segundos** quando a bateria estiver **≤ 5%** e o **carregador não estiver conectado**.

- Package: `com.vapesmadcat.monitorbatt`
- Versão Atual: `1.2.1`
- minSdk 24 / targetSdk 34 / JDK 17 / AGP 8.5.2

## Build automático pelo GitHub Actions

1. Suba o conteúdo desta pasta no repositório `Monitor-batt` (preserve `.github/workflows/build.yml`).
2. Vá em **Actions** → o workflow **Build Android APK** roda automaticamente em cada push para `main`/`master`. Também dá pra disparar manualmente em **Run workflow**.
3. Quando ficar verde ✅, abra o run → **Artifacts** → baixe **Monitor-batt-debug-apk** → dentro está `Monitor-batt-debug.apk`.
4. Transfira pro celular e instale (precisa autorizar "fontes desconhecidas").

> É um APK **debug**, assinado com a chave de debug do Android. Instala normalmente, mas não serve pra publicação na Play Store.

## Release para Play Store (AAB assinado)

1. Crie um arquivo `keystore.properties` na raiz do projeto (não versionar) com:
   - `storeFile=/caminho/para/sua-release-keystore.jks`
   - `storePassword=...`
   - `keyAlias=...`
   - `keyPassword=...`
2. Gere o bundle com:
   - `./gradlew bundleRelease`
3. O arquivo final fica em:
   - `app/build/outputs/bundle/release/app-release.aab`

> Sem `keystore.properties` (ou variáveis `RELEASE_*`), o build `release` é gerado unsigned e não usa assinatura de debug.

## Build local (Android Studio)

1. `File → Open` → selecione a pasta `Monitor-batt`.
2. Aguarde a sincronização do Gradle.
3. Conecte o aparelho com depuração USB e clique **Run ▶**.

## Como usar o app

1. Toque em **Iniciar monitoramento** e conceda permissão de notificações (Android 13+).
2. O serviço fica como **foreground service** com notificação persistente.
3. Quando a bateria atingir ≤ 5% sem carregador, toca um bip alto a cada 30 s.
4. Conectou o carregador (ou subiu acima de 5%) → para automaticamente.

## Observações

- O som usa `STREAM_ALARM` — toca mesmo no modo silencioso/vibrar. Em DnD total pode ser silenciado.
- Há um `BootReceiver` para religar o serviço após reiniciar o aparelho. Em Xiaomi/Samsung/Huawei/Oppo libere "autostart" e "executar em background sem restrições" nas configurações de bateria.
- Para mudar o limiar ou o intervalo do bip, edite `THRESHOLD` e `BEEP_INTERVAL_MS` em `BatteryService.java`.
