#!/system/bin/sh
echo "Corrigindo strings.xml..."
sed -i 's/<string name="notif_fmt">/<string name="notif_fmt" formatted="false">/g' app/src/main/res/values/strings.xml
sed -i 's/<string name="battery_fmt">/<string name="battery_fmt" formatted="false">/g' app/src/main/res/values/strings.xml
echo "Corrigindo build.gradle..."
if grep -q "namespace" app/build.gradle; then
    sed -i 's/namespace .*/namespace "com.example.batteryalert"/' app/build.gradle
else
    sed -i '/android {/a\    namespace "com.example.batteryalert"' app/build.gradle
fi
echo "Removendo package do manifest..."
sed -i 's/ package="[^"]*"//' app/src/main/AndroidManifest.xml
echo "Pronto! Execute ./gradlew clean build"
