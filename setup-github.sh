#!/bin/bash
# Script simples pra preparar o repositório pro GitHub (roda no Termux ou Linux)

echo "=== Configurando repositório Monitor de Bateria ==="

# Inicializa git se ainda não existir
if [ ! -d .git ]; then
    git init
    echo "Git inicializado."
fi

# Adiciona todos os arquivos
git add .

# Commit inicial
git commit -m "Monitor de Bateria v1.0 - App residente com alerta sonoro abaixo de 5%" || echo "Nada novo pra commitar."

echo ""
echo "✅ Pronto!"
echo ""
echo "Agora faça o seguinte:"
echo "1. Crie um repositório novo no GitHub (pode ser privado)"
echo "2. Copie a URL do repositório (ex: https://github.com/seuuser/monitor-bateria.git)"
echo "3. Rode os comandos abaixo (substitua pela sua URL):"
echo ""
echo "   git branch -M main"
echo "   git remote add origin https://github.com/SEU-USUARIO/monitor-bateria.git"
echo "   git push -u origin main"
echo ""
echo "Depois disso, vá na aba Actions do GitHub e a APK vai ser gerada automaticamente!"
echo ""
echo "Boa sorte! Qualquer coisa é só chamar."