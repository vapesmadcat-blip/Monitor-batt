#!/data/data/com.termux/files/usr/bin/bash

# Uso: ./find_duplicates.sh <diretório>

if [ $# -eq 0 ]; then
    echo "Uso: $0 <diretorio>"
        exit 1
        fi
        
        dir="$1"
        if [ ! -d "$dir" ]; then
            echo "Erro: '$dir' não é um diretório válido."
                exit 1
                fi
                
                echo "Procurando duplicatas em: $dir"
                echo "Aguarde, calculando hashes..."
                
                # Gera checksum MD5 de todos os arquivos, ordena, e agrupa duplicatas
                find "$dir" -type f -exec md5sum {} + | sort | awk -F'  ' '
                {
                    if ($1 == prev_checksum) {
                            if (count == 1) {
                                        printf "\n🔁 Duplicatas (MD5: %s):\n", prev_checksum
                                                    print prev_path
                                                            }
                                                                    print $2
                                                                            count++
                                                                                } else {
                                                                                        prev_checksum = $1
                                                                                                prev_path = $2
                                                                                                        count = 1
                                                                                                            }
                                                                                                            }
                                                                                                            END {
                                                                                                                if (count > 0 && prev_checksum != "") print ""
                                                                                                                }
                                                                                                                '
                                                                                                                
                                                                                                                echo -e "\nBusca concluída."