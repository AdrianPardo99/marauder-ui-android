# Marauder UI Pro - Android Edition

## ¿Qué es esto?
Marauder UI Pro es una interfaz gráfica avanzada y moderna diseñada para interactuar con el firmware ESP32 Marauder. Esta versión en específico está preparada como una **Aplicación Nativa para Android**, construida bajo una arquitectura híbrida utilizando Vue.js y Capacitor.

## ¿Cómo funciona?
El proyecto está dividido en dos capas fundamentales que trabajan en conjunto:

1. **Frontend (Web - Vue.js):** Todo lo que ves (los paneles de comandos, el mapa GPS, la interfaz de Wardriving y NFC) está escrito en tecnologías web modernas y vive en la carpeta `src/`.
2. **Capa Nativa (Android - Capacitor):** El código web se empaqueta dentro de un proyecto de Android Studio (carpeta `android/`). Capacitor actúa como el "puente" que permite que tu código web se ejecute como una aplicación instalable real en el celular.

### 🔌 Soporte Serial Nativo (USB OTG)
Uno de los componentes más importantes de este repositorio es su soporte serial. Ya que los navegadores de los celulares Android **no soportan** la *Web Serial API* por defecto, este proyecto incluye **plugins de Java personalizados** (como `MarauderSerialPlugin` y `SerialService`). 

Estos plugins nativos permiten que la aplicación se comunique directamente con el ESP32 a través de un cable **USB OTG**, traduciendo las peticiones de la interfaz web hacia el hardware de forma transparente.

---

## 🛠️ Comandos de Configuración y Compilación

Para compilar y probar este proyecto, necesitas tener instalado **Node.js** y **Android Studio**.

### 1. Instalar Dependencias
Al clonar o abrir este repositorio por primera vez, instala todas las librerías necesarias ejecutando:
```bash
npm install
```

### 2. Construir la Interfaz Visual
Cada vez que hagas un cambio en los archivos `.vue` o `.js` de la carpeta `src/`, debes reconstruir el proyecto de producción:
```bash
npm run build
```

### 3. Sincronizar con Capacitor (Android)
Una vez que el código web está construido, debes "inyectarlo" o sincronizarlo con el proyecto nativo de Android. Ejecuta:
```bash
npx cap sync android
```

### 4. Lanzar la Aplicación
Para abrir el proyecto en Android Studio y compilar tu archivo `.apk`:
```bash
npx cap open android
```
*(Si ya tienes tu celular conectado por USB con el modo de depuración activado, puedes correr la app directamente desde la terminal con `npx cap run android`)*.

---

## 🗺️ Notas sobre los Mapas
- La aplicación utiliza **OpenStreetMap** (vía Leaflet) para renderizar el panel GPS. 
- A partir de Android 9, el sistema operativo es estricto con las descargas en segundo plano. Este repositorio ya viene configurado con `android:usesCleartextTraffic="true"` en el *AndroidManifest* para evitar que el mapa se quede en negro.
- **Importante:** Para que el mapa funcione (descargue las imágenes de las calles), el teléfono debe tener conexión a Internet (WiFi o Datos Móviles). Si no hay internet y no hay señal GPS del ESP32, el mapa podría mostrar la coordenada `0,0` (un océano azul).
