#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "====================================="
echo " Corrigindo namespace do Monitor-batt"
echo "====================================="
echo

if [ ! -f app/build.gradle ]; then
    echo "ERRO: execute dentro da raiz do projeto."
        exit 1
        fi
        
        echo "[1/3] Fazendo backup..."
        cp app/build.gradle app/build.gradle.bak
        
        echo "[2/3] Corrigindo namespace..."
        sed -i 's/namespace "com.example.batteryalert"/namespace "com.vapesmadcat.monitorbatt"/g' app/build.gradle
        
        echo "[3/3] Verificando resultado..."
        
        grep -n "namespace" app/build.gradle
        
        echo
        echo "Pronto."
        echo
        echo "Agora execute:"
        echo
        echo "git add app/build.gradle"
        echo 'git commit -m "Fix namespace mismatch"'
        echo "git push"