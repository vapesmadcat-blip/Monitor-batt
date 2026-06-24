#!/bin/bash

echo "=== Aplicando correção de tema claro/escuro ==="

# Atualiza themes.xml
cat > app/src/main/res/values/themes.xml << 'THEME'
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.BatteryMonitor" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
            <item name="colorPrimary">@color/purple_500</item>
                    <item name="colorPrimaryVariant">@color/purple_700</item>
                            <item name="colorOnPrimary">@color/white</item>
                                    <item name="colorSecondary">@color/teal_200</item>
                                            <item name="colorSecondaryVariant">@color/teal_700</item>
                                                    <item name="colorOnSecondary">@color/black</item>
                                                    
                                                            <!-- Cores que respeitam o tema do sistema -->
                                                                    <item name="android:colorBackground">@color/colorBackground</item>
                                                                            <item name="colorSurface">@color/colorSurface</item>
                                                                                    <item name="colorOnSurface">@color/colorOnSurface</item>
                                                                                    
                                                                                            <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
                                                                                            
                                                                                                    <!-- Corrige menus e spinners -->
                                                                                                            <item name="android:popupMenuStyle">@style/PopupMenuStyle</item>
                                                                                                                    <item name="android:spinnerStyle">@style/Widget.App.Spinner</item>
                                                                                                                        </style>
                                                                                                                        
                                                                                                                            <style name="PopupMenuStyle" parent="Widget.AppCompat.PopupMenu">
                                                                                                                                    <item name="android:popupBackground">?attr/colorSurface</item>
                                                                                                                                        </style>
                                                                                                                                        
                                                                                                                                            <style name="Widget.App.Spinner" parent="Widget.AppCompat.Spinner">
                                                                                                                                                    <item name="android:popupBackground">?attr/colorSurface</item>
                                                                                                                                                        </style>
                                                                                                                                                        </resources>
                                                                                                                                                        THEME
                                                                                                                                                        
                                                                                                                                                        # Cria values-night
                                                                                                                                                        mkdir -p app/src/main/res/values-night
                                                                                                                                                        cat > app/src/main/res/values-night/themes.xml << 'NIGHT'
                                                                                                                                                        <?xml version="1.0" encoding="utf-8"?>
                                                                                                                                                        <resources>
                                                                                                                                                            <style name="Theme.BatteryMonitor" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
                                                                                                                                                                    <item name="android:colorBackground">#121212</item>
                                                                                                                                                                            <item name="colorSurface">#1E1E1E</item>
                                                                                                                                                                                    <item name="colorOnSurface">#FFFFFF</item>
                                                                                                                                                                                        </style>
                                                                                                                                                                                        </resources>
                                                                                                                                                                                        NIGHT
                                                                                                                                                                                        
                                                                                                                                                                                        echo "✅ Temas atualizados com sucesso!"
                                                                                                                                                                                        echo "Agora rode: git status"
                                                                                                                                                                                        echo "Depois: git add . && git commit -m 'Fix: light/dark theme support for menus and spinners'"
                                                                                                                                                                                        echo "E git push"
                                                                                                                                                                                        EOF
                                                                                                                                                                                        
                                                                                                                                                                                        chmod +x fix-theme.sh
                                                                                                                                                                                        echo "Script criado! Agora rode:"
                                                                                                                                                                                        echo "./fix-theme.sh"
                                                                                                                                                                                        
