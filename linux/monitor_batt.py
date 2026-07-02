#!/usr/bin/env python3
"""
Monitor-Batt for Linux
======================
Daemon que monitora a bateria e emite alertas sonoros/visuais,
equivalente ao aplicativo Android Monitor-Batt.

Dependências (instale via apt/pacman/dnf):
  beep         - tons simples via PC speaker  (opcional, fallback para paplay)
  paplay       - reprodução de áudio PulseAudio (opcional)
  notify-send  - notificações desktop (libnotify-bin)
  espeak-ng    - TTS (opcional)

Uso:
  python3 monitor_batt.py [--config /caminho/para/config.ini]
"""

import argparse
import configparser
import os
import subprocess
import sys
import time
import logging
from pathlib import Path

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("monitor_batt")

# ---------------------------------------------------------------------------
# Padrão de configuração (equivalente às constantes do BatteryService.java)
# ---------------------------------------------------------------------------
DEFAULTS = {
    "threshold": "10",           # alerta abaixo deste %
    "beep_interval_seconds": "15",
    "muted": "false",
    "beep_enabled": "true",
    "tts_enabled": "false",
    "tts_command": "espeak-ng -v pt-br",  # comando TTS
    "voice_low_threshold": "10",
    "voice_critical_threshold": "5",
    "voice_verylow_threshold": "2",
    "bad_contact_event_count": "4",       # eventos plug/unplug para mau contato
    "bad_contact_window_seconds": "10",
    "battery_path": "",                   # deixe vazio para autodetectar
}

# ---------------------------------------------------------------------------
# Caminhos padrão de configuração
# ---------------------------------------------------------------------------
CONFIG_DIR = Path.home() / ".config" / "monitor-batt"
CONFIG_FILE = CONFIG_DIR / "config.ini"


def find_battery_path() -> str:
    """Detecta automaticamente o caminho da bateria em /sys."""
    base = Path("/sys/class/power_supply")
    if not base.exists():
        return ""
    for entry in sorted(base.iterdir()):
        type_file = entry / "type"
        if type_file.exists() and type_file.read_text().strip() == "Battery":
            return str(entry)
    return ""


def read_file(path: str) -> str:
    try:
        return Path(path).read_text().strip()
    except Exception:
        return ""


def get_battery_level(battery_path: str) -> int:
    """Retorna nível de bateria (0-100) ou -1 em erro."""
    val = read_file(f"{battery_path}/capacity")
    try:
        return max(0, min(100, int(val)))
    except ValueError:
        return -1


def is_charging(battery_path: str) -> bool:
    """Retorna True se carregando."""
    status = read_file(f"{battery_path}/status").lower()
    return status in ("charging", "full")


def is_full(battery_path: str) -> bool:
    status = read_file(f"{battery_path}/status").lower()
    return status == "full"


# ---------------------------------------------------------------------------
# Alertas sonoros
# ---------------------------------------------------------------------------

def _run(cmd: list[str]) -> bool:
    """Executa comando silenciosamente. Retorna True se OK."""
    try:
        subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       timeout=5)
        return True
    except Exception:
        return False


def play_beep(level: int, muted: bool, beep_enabled: bool):
    """Toca bip via `beep` (PC speaker) ou fallback com paplay/sox."""
    if muted or not beep_enabled:
        return

    # Frequência e duração baseadas no nível (igual ao getToneForLevel do Android)
    if level <= 2:
        freq, duration_ms = 1800, 800
    elif level <= 5:
        freq, duration_ms = 1400, 800
    else:
        freq, duration_ms = 1000, 400

    # Tenta `beep`
    if _run(["beep", "-f", str(freq), "-l", str(duration_ms)]):
        return

    # Fallback: gera tom via paplay + ffmpeg
    if _run(["ffplay", "-nodisp", "-autoexit", "-f", "lavfi",
             f"-i", f"sine=frequency={freq}:duration={duration_ms / 1000:.2f}",
             "-loglevel", "quiet"]):
        return

    # Fallback: paplay com arquivo de sistema
    _run(["paplay", "/usr/share/sounds/freedesktop/stereo/bell.oga"])


def send_notification(title: str, body: str, urgency: str = "normal"):
    """Envia notificação desktop via notify-send."""
    try:
        subprocess.run(
            ["notify-send", "-u", urgency, "-a", "Monitor-Batt", title, body],
            timeout=5, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
    except Exception:
        pass


def speak(message: str, tts_command: str, muted: bool, tts_enabled: bool):
    """Fala mensagem via TTS."""
    if muted or not tts_enabled:
        return
    try:
        cmd = tts_command.split() + [message]
        subprocess.run(cmd, timeout=15, stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)
    except Exception as e:
        log.warning("TTS falhou: %s", e)


# ---------------------------------------------------------------------------
# Loop principal
# ---------------------------------------------------------------------------

class MonitorBatt:
    def __init__(self, cfg: configparser.ConfigParser):
        s = cfg["monitor"]

        self.threshold = int(s["threshold"])
        self.interval = int(s["beep_interval_seconds"])
        self.muted = s["muted"].lower() == "true"
        self.beep_enabled = s["beep_enabled"].lower() == "true"
        self.tts_enabled = s["tts_enabled"].lower() == "true"
        self.tts_command = s["tts_command"]
        self.voice_low = int(s["voice_low_threshold"])
        self.voice_critical = int(s["voice_critical_threshold"])
        self.voice_verylow = int(s["voice_verylow_threshold"])
        self.bad_contact_count = int(s["bad_contact_event_count"])
        self.bad_contact_window = float(s["bad_contact_window_seconds"])

        self.battery_path = s["battery_path"] or find_battery_path()
        if not self.battery_path:
            log.error("Nenhuma bateria encontrada em /sys/class/power_supply/")
            sys.exit(1)
        log.info("Usando bateria: %s", self.battery_path)

        self.alerting = False
        self.prev_charging = None
        self.full_notified = False
        self.charger_event_timestamps: list[float] = []
        self.bad_contact_shown = False
        self.last_voice_time = 0.0
        self.voice_cooldown = 60.0

    def run(self):
        log.info("Monitor-Batt iniciado. Threshold=%d%% Intervalo=%ds",
                 self.threshold, self.interval)
        send_notification("Monitor-Batt", "Monitoramento de bateria iniciado.")

        next_beep = time.monotonic()

        while True:
            level = get_battery_level(self.battery_path)
            charging = is_charging(self.battery_path)
            full = is_full(self.battery_path)

            if level < 0:
                log.warning("Não foi possível ler o nível de bateria.")
                time.sleep(5)
                continue

            log.debug("Bateria: %d%% | Carregando: %s", level, charging)

            # Detectar mudança de estado do carregador
            if self.prev_charging is not None and charging != self.prev_charging:
                self._on_charger_changed(charging)

            self.prev_charging = charging

            # Bateria cheia
            if full and not self.full_notified:
                self._on_full()
                self.full_notified = True
            elif level < 95:
                self.full_notified = False

            # Avaliar estado de alerta
            should_alert = (level <= self.threshold) and not charging
            now = time.monotonic()

            if should_alert:
                if not self.alerting:
                    self.alerting = True
                    log.warning("ALERTA! Bateria baixa: %d%%", level)
                    send_notification("⚠️ Bateria Baixa",
                                      f"Nível: {level}% — conecte o carregador!",
                                      urgency="critical")
                    next_beep = now  # bipa imediatamente

                if now >= next_beep:
                    play_beep(level, self.muted, self.beep_enabled)
                    self._speak_alert(level)
                    next_beep = now + self.interval
            else:
                if self.alerting:
                    self.alerting = False
                    log.info("Alerta desativado — bateria normal (%d%%)", level)
                    send_notification("✅ Monitor-Batt",
                                      f"Bateria normalizada: {level}%")

            time.sleep(2)

    # ------------------------------------------------------------------
    # Eventos do carregador
    # ------------------------------------------------------------------

    def _on_charger_changed(self, now_charging: bool):
        now = time.time()
        self.charger_event_timestamps.append(now)
        # Remove eventos fora da janela
        self.charger_event_timestamps = [
            t for t in self.charger_event_timestamps
            if (now - t) <= self.bad_contact_window
        ]

        log.debug("Evento carregador: %s | na janela: %d",
                  "CONECTADO" if now_charging else "DESCONECTADO",
                  len(self.charger_event_timestamps))

        if (not self.bad_contact_shown and
                len(self.charger_event_timestamps) >= self.bad_contact_count):
            self.bad_contact_shown = True
            self.charger_event_timestamps.clear()
            self._on_bad_contact()
            return

        if now_charging:
            self._on_charger_connected()

        # Reset do flag após a janela de tempo
        # (simplificado: resetamos na próxima iteração se não houver eventos)

    def _on_bad_contact(self):
        log.warning("MAU CONTATO DETECTADO!")
        send_notification("⚠️ Possível Mau Contato",
                          "Detectadas múltiplas conexões/desconexões do carregador. "
                          "Verifique o cabo!",
                          urgency="critical")
        speak("Atenção! Possível problema no cabo. Detectada possibilidade de mau contato.",
              self.tts_command, self.muted, self.tts_enabled)

    def _on_charger_connected(self):
        log.info("Carregador conectado — monitoramento pausado.")
        send_notification("🔌 Carregador Conectado", "Monitoramento pausado.")
        speak("Carregador conectado. Monitoramento pausado.",
              self.tts_command, self.muted, self.tts_enabled)

    def _on_full(self):
        log.info("Bateria cheia! 100%%")
        send_notification("🔋 Bateria Cheia", "100% — pode desconectar o carregador.")
        speak("Bateria cheia! Cem por cento. Pode desconectar o carregador.",
              self.tts_command, self.muted, self.tts_enabled)

    # ------------------------------------------------------------------
    # TTS inteligente
    # ------------------------------------------------------------------

    def _speak_alert(self, level: int):
        if not self.tts_enabled or self.muted:
            return
        now = time.monotonic()
        if (now - self.last_voice_time) < self.voice_cooldown:
            return

        if level <= max(self.voice_verylow, self.voice_critical):
            msg = (f"Alerta crítico! A bateria está em apenas {level} por cento. "
                   "Conecte o carregador imediatamente.")
        elif level <= self.voice_low:
            msg = (f"Atenção! Bateria baixa em {level} por cento. "
                   "Recomendo conectar o carregador agora.")
        else:
            msg = f"Monitor de bateria. Nível atual: {level} por cento."

        speak(msg, self.tts_command, self.muted, self.tts_enabled)
        self.last_voice_time = now


# ---------------------------------------------------------------------------
# Utilitários de configuração
# ---------------------------------------------------------------------------

def load_config(path: Path) -> configparser.ConfigParser:
    cfg = configparser.ConfigParser()
    cfg["monitor"] = DEFAULTS

    if path.exists():
        cfg.read(path)
        log.info("Configuração carregada de %s", path)
    else:
        log.info("Usando configuração padrão (não encontrado: %s)", path)

    return cfg


def write_default_config(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    cfg = configparser.ConfigParser()
    cfg["monitor"] = DEFAULTS
    with open(path, "w") as f:
        cfg.write(f)
    log.info("Configuração padrão salva em %s", path)


# ---------------------------------------------------------------------------
# Ponto de entrada
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Monitor-Batt para Linux — daemon de monitoramento de bateria"
    )
    parser.add_argument(
        "--config", default=str(CONFIG_FILE),
        help=f"Caminho para o arquivo de configuração (padrão: {CONFIG_FILE})"
    )
    parser.add_argument(
        "--init-config", action="store_true",
        help="Cria arquivo de configuração padrão e sai"
    )
    parser.add_argument(
        "--debug", action="store_true",
        help="Ativa saída de debug detalhada"
    )
    args = parser.parse_args()

    if args.debug:
        logging.getLogger().setLevel(logging.DEBUG)

    config_path = Path(args.config)

    if args.init_config:
        write_default_config(config_path)
        print(f"Configuração criada em: {config_path}")
        sys.exit(0)

    cfg = load_config(config_path)
    monitor = MonitorBatt(cfg)

    try:
        monitor.run()
    except KeyboardInterrupt:
        log.info("Monitor-Batt encerrado pelo usuário.")
        send_notification("Monitor-Batt", "Monitoramento encerrado.")


if __name__ == "__main__":
    main()
