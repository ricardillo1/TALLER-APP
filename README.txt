APP ANDROID "TALLER" (WebView)

OPCION A - SIN ANDROID STUDIO (APK en la nube con GitHub, gratis)
1) Crea una cuenta en github.com y un repositorio nuevo (boton "New").
2) Sube TODO el contenido de esta carpeta al repositorio
   (Add file > Upload files). Debe verse la carpeta .github/workflows/build.yml
   Si no se sube, crea ese archivo a mano: Add file > Create new file,
   nombre: .github/workflows/build.yml  y pega el contenido del archivo.
3) Ve a la pestana "Actions" > "Crear APK" > "Run workflow".
4) Espera 3-5 minutos. Entra a la ejecucion terminada y descarga
   "Taller-APK" (abajo, en Artifacts). Es un ZIP que contiene app-debug.apk.
5) Pasa el APK al celular e instalalo (permite "origen desconocido").

OPCION B - CON ANDROID STUDIO
File > Open > carpeta TallerApp > Build > Build APK(s).

CAMBIAR LA DIRECCION DEL SISTEMA:
  app/src/main/java/com/taller/app/MainActivity.kt -> START_URL y ALLOWED_HOST_SUFFIX

CAMBIAR NOMBRE, COLOR E ICONO:
  app/src/main/res/values/strings.xml (app_name)
  app/src/main/res/values/colors.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
