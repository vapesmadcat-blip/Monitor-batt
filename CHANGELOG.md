# Changelog - Monitor Bat

## [Não lançado]

### Melhorias
- Painel de estatísticas (canto superior esquerdo) reformulado: taxa de carga/descarga (%/min) e tempo estimado agora usam corrente e capacidade instantâneas (`BatteryManager`), com suavização para acabar com os valores que ficavam saltando ou travados em `0.00%/min`.
- Novas estatísticas no painel: corrente (mA), potência (W), temperatura (°C, com alerta de cor) e tensão (V) da bateria.

## [1.2.1] - 2026-06-24

### Melhorias
- Preparação do projeto para release na Play Store com assinatura de release via `keystore.properties` ou variáveis de ambiente.
- Atualização da infraestrutura de build para AGP `8.5.2`.

## [1.2.0] - 2026-06-23

### Adicionado
- Nova tela "Sobre" com informações da Nexus Soluções Globais.
- Exibição da versão do aplicativo na tela Sobre.
- Sistema de mascote para feedback visual do nível de bateria.
- Novas vozes de personagens (Lula, Bolsonaro, Goku, Faustão, etc.).
- Suporte a estilos visuais (Pilha Normal vs Mascote).
- Animação de raio quando o dispositivo está carregando.

### Melhorias
- Atualização do SDK alvo para Android 14 (API 34).
- Melhorias na estabilidade do serviço em background.
- Otimização do consumo de recursos pelo `BatteryService`.
- Limpeza de arquivos legados e duplicados no repositório.

### Corrigido
- Problemas de permissão de notificação no Android 13+.
- Sincronização de configurações ao iniciar o serviço.
