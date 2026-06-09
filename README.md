# Monitor de Bateria - App Residente

App Android leve e residente que monitora a bateria em segundo plano e emite **bipe a cada 30 segundos** quando a bateria está abaixo de 5% (e não está carregando).

O visual da bateria é exatamente o HTML que você forneceu, rodando em WebView quando o app está aberto.

## Funcionalidades
- Serviço foreground residente (roda mesmo com app fechado)
- Alerta sonoro automático abaixo de 5%
- Notificação persistente com status da bateria
- Inicia automaticamente após reiniciar o celular
- Muito leve (quase zero consumo quando não está em alerta crítico)
- Totalmente em português

## Como usar no GitHub (compilar pelo celular)

1. Crie um repositório novo no GitHub (pode ser privado).
2. Descompacte este ZIP dentro da pasta do repositório.
3. Faça commit e push de todos os arquivos.
4. Vá na aba **Actions** do repositório.
5. O workflow vai rodar automaticamente e gerar a APK.
6. Baixe a APK em **Artifacts** → `MonitorDeBateria-debug`.

Depois é só instalar no celular (pode precisar ativar "Instalar de fontes desconhecidas").

## Configuração recomendada após instalar

Vá em:
**Configurações do Android → Bateria → Uso de bateria → Otimização de bateria**  
Procure por "Monitor de Bateria" e coloque como **Sem restrições** ou "Não otimizar".

Isso garante que o serviço não seja morto pelo sistema.

## Como funciona
- Ao abrir o app pela primeira vez, o monitoramento residente é ativado.
- Uma notificação permanente aparece.
- Quando bateria < 5% → bipe forte a cada 30s até carregar ou subir acima de 5%.
- O bipe usa o canal de ALARME (toca mesmo no silencioso/vibrar).

## Arquivos principais
- `app/src/main/assets/index.html` → Seu HTML original da bateria
- `BatteryMonitorService.java` → O serviço que roda em background e controla o bipe
- `.github/workflows/android-build.yml` → Workflow que compila no GitHub

## Personalização
Quer mudar o tom do bipe, o intervalo, adicionar ícone bonito, ou integrar com o DriverFlux? É só pedir que eu ajusto.

Feito com ❤️ pra você não ficar na mão com bateria baixa no táxi.
