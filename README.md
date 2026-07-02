# Monitor-batt

Aplicativo Android (Java) que monitora a bateria em background e emite um **bip a cada 30 segundos** quando a bateria estiver **≤ 5%** e o **carregador não estiver conectado**.

- Package: `com.vapesmadcat.monitorbatt`
- Versão Atual: `1.2.1`
- minSdk 24 / targetSdk 34 / JDK 17 / AGP 8.5.2

## Build automático pelo GitHub Actions

1. Suba o conteúdo desta pasta no repositório `Monitor-batt` (preserve `.github/workflows/build.yml`).
2. Vá em **Actions** → o workflow **Build Android APK** roda automaticamente em cada push para `main`/`master`. Também dá pra disparar manualmente em **Run workflow**.
3. Quando ficar verde ✅, abra o run → **Artifacts** → baixe **Monitor-Bat-Release-v1.2.0** → dentro está o APK pronto para uso.
4. Transfira pro celular e instale (precisa autorizar "fontes desconhecidas").

> É um APK **debug**, assinado com a chave de debug do Android. Instala normalmente, mas não serve pra publicação na Play Store.

## Release & Play Store

O repositório possui workflow automático em `.github/workflows/release.yml` para gerar **AAB assinado** e enviar para a Play Store (track `internal`) ao publicar uma tag `v*.*.*` (ex: `v1.2.1`) ou via `workflow_dispatch`.

1. Gere sua keystore de release (uma vez):
   ```bash
   keytool -genkey -v -keystore monitor-batt.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias monitor-batt
   ```
2. Converta a keystore para base64 (uma linha) e cadastre os secrets em **Settings → Secrets and variables → Actions**:
   ```bash
   base64 -i monitor-batt.jks | tr -d '\n'
   ```
   Secrets obrigatórios:
   - `RELEASE_STORE_FILE` — `.jks` em base64
   - `RELEASE_STORE_PASSWORD` — senha da keystore
   - `RELEASE_KEY_ALIAS` — alias da chave
   - `RELEASE_KEY_PASSWORD` — senha da chave
   - `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` — JSON da service account do Google Play (texto puro)
3. No Google Play Console, crie uma **service account** com acesso de release e baixe o arquivo JSON da chave para usar no secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
4. Dispare release automatizada criando e enviando uma tag de versão:
   ```bash
   git tag v1.2.1
   git push origin v1.2.1
   ```
5. A primeira versão do app precisa ser enviada manualmente no Play Console para criar o listing. Depois disso, os próximos envios podem ser automatizados pelo workflow.

> Sem credenciais de release (`keystore.properties` ou variáveis `RELEASE_*`), o build `release` é gerado **sem assinatura de debug** (unsigned), evitando confusão entre artefatos de debug e release.

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
