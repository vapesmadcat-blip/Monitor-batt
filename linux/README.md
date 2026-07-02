# Monitor-Batt para Linux

Versão Linux do aplicativo **Monitor-Batt**, equivalente ao app Android.

Monitora a bateria do seu notebook/laptop e emite alertas sonoros e notificações desktop quando o nível estiver baixo.

---

## Funcionalidades

| Funcionalidade           | Descrição                                                         |
|--------------------------|-------------------------------------------------------------------|
| Alerta de bateria baixa  | Bipe + notificação quando abaixo do threshold configurado         |
| Alertas por nível        | Tom diferente para nível baixo, crítico e muito crítico           |
| TTS (voz)                | Fala o alerta em português via `espeak-ng`                        |
| Bateria cheia            | Notificação quando atingir 100%                                   |
| Carregador conectado     | Notificação ao conectar o carregador                              |
| Detecção de mau contato  | Detecta múltiplas conexões/desconexões rápidas do cabo            |

---

## Requisitos

```bash
# Debian/Ubuntu
sudo apt install python3 libnotify-bin espeak-ng beep

# Arch Linux
sudo pacman -S python libnotify espeak-ng beep

# Fedora
sudo dnf install python3 libnotify espeak-ng beep
```

> `beep` requer acesso ao PC speaker. Se não funcionar, o script usa `paplay` como fallback.

---

## Instalação rápida

```bash
# 1. Copiar o script
sudo mkdir -p /opt/monitor-batt
sudo cp monitor_batt.py /opt/monitor-batt/
sudo chmod +x /opt/monitor-batt/monitor_batt.py

# 2. Criar configuração padrão
python3 /opt/monitor-batt/monitor_batt.py --init-config

# 3. Executar manualmente (para testar)
python3 /opt/monitor-batt/monitor_batt.py
```

---

## Configuração

O arquivo de configuração fica em `~/.config/monitor-batt/config.ini`.

Crie-o automaticamente com:
```bash
python3 monitor_batt.py --init-config
```

### Opções disponíveis

```ini
[monitor]
# Nível de bateria (%) que ativa o alerta
threshold = 10

# Intervalo entre beeps durante o alerta (segundos)
beep_interval_seconds = 15

# Silenciar todos os sons (true/false)
muted = false

# Ativar bipes (true/false)
beep_enabled = true

# Ativar TTS — fala o nível de bateria em voz alta (true/false)
tts_enabled = false

# Comando TTS usado para falar os alertas
tts_command = espeak-ng -v pt-br

# Limiares para os diferentes níveis de voz TTS
voice_low_threshold = 10
voice_critical_threshold = 5
voice_verylow_threshold = 2

# Detecção de mau contato: número de eventos plug/unplug
bad_contact_event_count = 4
bad_contact_window_seconds = 10

# Caminho da bateria em /sys (deixe vazio para autodetectar)
battery_path =
```

---

## Executar como serviço systemd (usuário)

```bash
# 1. Copiar o arquivo de serviço
mkdir -p ~/.config/systemd/user
cp monitor-batt.service ~/.config/systemd/user/

# 2. Ajustar o caminho do script no arquivo de serviço se necessário
# ExecStart=/usr/bin/python3 /opt/monitor-batt/monitor_batt.py

# 3. Ativar e iniciar
systemctl --user enable monitor-batt.service
systemctl --user start monitor-batt.service

# 4. Verificar status
systemctl --user status monitor-batt.service

# 5. Ver logs
journalctl --user -u monitor-batt.service -f
```

---

## Uso manual

```bash
# Executar normalmente
python3 monitor_batt.py

# Usar arquivo de configuração alternativo
python3 monitor_batt.py --config /caminho/config.ini

# Modo debug (mais detalhes no terminal)
python3 monitor_batt.py --debug

# Criar configuração padrão
python3 monitor_batt.py --init-config
```

---

## Comparativo com o app Android

| Recurso Android           | Equivalente Linux               |
|---------------------------|---------------------------------|
| BatteryService            | Loop principal do daemon Python |
| ToneGenerator             | `beep` / `paplay` / `ffplay`    |
| TextToSpeech              | `espeak-ng`                     |
| NotificationManager       | `notify-send`                   |
| SharedPreferences         | `~/.config/monitor-batt/config.ini` |
| BootReceiver              | Serviço systemd                 |
