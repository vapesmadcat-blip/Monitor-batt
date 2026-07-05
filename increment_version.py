import re
import os

def increment_version(gradle_file_path):
    if not os.path.exists(gradle_file_path):
        print(f"Erro: Arquivo {gradle_file_path} não encontrado.")
        return

    with open(gradle_file_path, 'r') as f:
        content = f.read()

    # Increment versionCode
    version_code_match = re.search(r'versionCode\s+(\d+)', content)
    if version_code_match:
        old_version_code = int(version_code_match.group(1))
        new_version_code = old_version_code + 1
        content = re.sub(r'versionCode\s+\d+', f'versionCode {new_version_code}', content, 1)
        print(f'versionCode incrementado de {old_version_code} para {new_version_code}')
    else:
        print('versionCode não encontrado.')
        return

    # Increment versionName (ex: 1.2.1 -> 1.2.2)
    version_name_match = re.search(r'versionName\s+"(\d+\.\d+\.\d+)"', content)
    if version_name_match:
        old_version_name = version_name_match.group(1)
        parts = old_version_name.split('.')
        if len(parts) == 3:
            major, minor, patch = int(parts[0]), int(parts[1]), int(parts[2])
            new_patch = patch + 1
            new_version_name = f'{major}.{minor}.{new_patch}'
            content = re.sub(r'versionName\s+"\d+\.\d+\.\d+"', f'versionName "{new_version_name}"', content, 1)
            print(f'versionName incrementado de {old_version_name} para {new_version_name}')
        else:
            print('Formato de versionName inesperado.')
    else:
        print('versionName não encontrado.')

    with open(gradle_file_path, 'w') as f:
        f.write(content)

if __name__ == '__main__':
    # Caminho relativo ao app/build.gradle
    gradle_file = os.path.join(os.getcwd(), 'app', 'build.gradle')
    increment_version(gradle_file)
