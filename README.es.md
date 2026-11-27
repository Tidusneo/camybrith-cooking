# Camybrith Cooking

🇫🇷 **[Version française](README.md)** · 🇬🇧 **[English version](README.en.md)**

**Una interfaz nueva, rápida y moderna para el robot de cocina Monsieur Cuisine Smart de Lidl (SKMS 1200 A1).**
Gratuita, libre (licencia MIT), sin cuenta, sin suscripción, y funciona sin Internet.

Camybrith Cooking sustituye la app original en la tableta del robot. Controla ella misma el motor,
el calentamiento y la báscula, y conserva las recetas oficiales de Monsieur Cuisine.

> **Proyecto independiente**, sin relación con Lidl, SilverCrest ni Hoyer. Hay que «rootear» el
> robot, lo que probablemente anula la garantía y conlleva un pequeño riesgo de dejarlo inservible
> si un paso sale mal. La guía está escrita para evitarlo, con una copia de seguridad completa antes
> de tocar nada, pero todo lo haces **bajo tu propia responsabilidad**. Probado en un Smart
> SKMS 1200 A1 (Android 8.1, versión del sistema V01.00.83 de junio de 2022) en noviembre de 2026.

<p align="center"><img src="docs/images/animation_demarrage.gif" width="560" alt="Animación de arranque de Camybrith Cooking"></p>

<p align="center"><img src="docs/images/pesee-195g.gif" width="560" alt="Pesaje: el vaso se llena en pantalla"><br><sub>El pesaje en directo: el vaso se llena en pantalla mientras viertes.</sub></p>

| | |
|---|---|
| ![Inicio](docs/images/accueil.png) | ![Recetas](docs/images/recettes.png) |
| **Inicio**: la receta en curso, la cocción manual, la báscula, las mejor valoradas | **Recetas**: cerca de 1700 recetas de Monsieur Cuisine, filtros y favoritos |
| ![Receta](docs/images/recette.png) | ![Pesaje guiado](docs/images/pesee-guidee.png) |
| **Una receta**: ingredientes, pasos, opiniones | **Cocción guiada**: cada ingrediente se pesa en el vaso, paso a paso |
| ![Cocción manual](docs/images/cuisson-manuelle.png) | ![Báscula](docs/images/balance.png) |
| **Cocción manual**: tiempo, temperatura y velocidad con el mando | **Báscula**: el vaso se llena en pantalla hasta el peso objetivo |
| ![Búsqueda](docs/images/recherche.png) | ![Ajustes](docs/images/reglages.png) |
| **Búsqueda** instantánea, incluso con faltas | **Ajustes**: tema claro u oscuro, idioma, sonidos |

<sub>Capturas tomadas en el robot. Las fotos de las recetas pertenecen a Monsieur Cuisine (Lidl). Las capturas muestran la app en francés; también está disponible en español y en inglés.</sub>

## La app en español

La app está disponible en **español** (además de francés e inglés): **Ajustes › Langue · Language ›
Español**, o automáticamente si Android está en español. Con la app en español, la sincronización
descarga las **recetas de Monsieur Cuisine en español**.

## Instalar

La guía paso a paso existe en [inglés](docs/GUIDE.en.md) y en [francés](docs/GUIDE.md). Todavía no
hay versión en español, pero el **asistente de Claude** (más abajo) te guía en español, paso a paso.

Necesitas:
* un **PC con Windows** (para la herramienta que escribe en la memoria del robot);
* un cable **USB-A macho a USB-A macho** (USB 2.0) y un destornillador Torx pequeño;
* el Wi-Fi de casa;
* entre dos y tres horas, sin prisas.

Una vez rooteado el robot, todo lo demás (instalación, actualizaciones, sonidos, logo) se hace
**por Wi-Fi**, desde Windows (con Ubuntu, ver la guía), un Mac o Linux. Los scripts de instalación
muestran sus mensajes en francés; la guía indica con qué mensaje debe terminar cada uno.

## Deja que Claude te guíe

El proyecto incluye un **asistente listo para usar** para Claude: conoce toda la guía, avanza paso
a paso, comprueba contigo los tamaños de archivo y las fotos de pantalla antes de cada paso
delicado, y se detiene si algo no cuadra. Habla español.

1. Descarga `camybrith-assistant-claude.zip` en la página
   [Releases](https://github.com/scaule/camybrith-cooking/releases/latest).
2. En claude.ai o en la app de Claude: comprueba que la ejecución de código y la creación de
   archivos están activadas en **Ajustes > Capacidades**, luego ve a **Personalizar > Skills**,
   botón **+**, **Crear skill**, **Subir un skill**, y elige el ZIP.
3. Abre una conversación nueva y escribe por ejemplo: «Quiero instalar Camybrith Cooking en mi
   Monsieur Cuisine Smart, guíame».

## ¿Tienes un Monsieur Cuisine Connect?

Camybrith Cooking también puede funcionar en el **Connect** (SKMC 1200), de forma **experimental**:
todavía no se ha probado en un Connect real. Ver [docs/CONNECT.en.md](docs/CONNECT.en.md).

## Lo que aporta

* **Una interfaz pensada para la pantalla y el mando del robot**, fluida en su pequeña tableta.
* **Las recetas de Monsieur Cuisine**, descargadas del sitio público de Lidl, por la noche o cuando
  quieras, con sus ajustes, fotos, valoraciones y opiniones.
* **Una búsqueda que funciona**: ignora acentos, plurales y faltas de ortografía. Búsqueda por
  ingredientes, filtros, favoritos.
* **Cocción guiada**: cada paso se inicia con un toque, el siguiente aparece solo, el pesaje está
  integrado (mando o botones +10 g y +50 g para llegar al peso).
* **Tus propias recetas** escritas para otro robot (archivo JSON, ver
  [docs/RECIPES.en.md](docs/RECIPES.en.md)): velocidades, temperaturas, Varoma y amasado se
  convierten para el Smart, en una pestaña aparte.
* **Tapa abierta = pausa**, y se reanuda con un toque al cerrarla.
* **Sin conexión**: nada depende de un servidor.
* **Todo se personaliza**: sonidos, animación de arranque con tu propio logo, incluso el nombre y
  el icono (ver [personnalisation/README.en.md](personnalisation/README.en.md)).

## Home Assistant

Camybrith puede enviar el estado del robot a Home Assistant por MQTT (estado, programa,
temperatura, peso, tiempo restante, tapa, alerta), con descubrimiento automático de los sensores.
Solo lectura: nada puede poner en marcha el robot a distancia. Ver
[docs/HOME-ASSISTANT.en.md](docs/HOME-ASSISTANT.en.md) (en inglés).

## Seguridad de la cocción

Se mantienen todos los límites de la app original (velocidad 3 como máximo al calentar y en sentido
inverso, duraciones máximas, condiciones del Turbo). La tapa, los errores y la frescura de los datos
de la base se comprueban **antes** de cada arranque; si la base deja de responder, todo se detiene;
una alerta permanece en pantalla hasta que se lee; nada vuelve a arrancar solo tras una pausa.

El control del motor funciona en **una app aparte** (el «piloto del motor»): si la interfaz falla,
la cocción no se corta. Además, la placa del robot detiene el motor por sí sola unos 4 segundos
después de la última orden recibida.

## Créditos y licencia

Camybrith Cooking es una adaptación al Smart de **Toque**
([Wuki60/MrCuisineVibeConnect](https://github.com/Wuki60/MrCuisineVibeConnect)), escrita para el
Monsieur Cuisine Connect. Gracias a Wuki60 y a los colaboradores de Toque.

El método de root procede de la comunidad de
[EliasKotlyar/Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack)
(issue n.º 38, en especial depau e ihrapsa). El root lo proporciona
[Magisk](https://github.com/topjohnwu/Magisk).

Licencia MIT: ver [LICENSE](LICENSE). Este repositorio no incluye código, recetas ni archivos de
Lidl; las únicas imágenes de Lidl son las fotos de recetas visibles en las capturas.
