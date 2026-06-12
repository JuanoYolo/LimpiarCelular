# App Cleaner Advisor

App Android local en Kotlin + Jetpack Compose para revisar aplicaciones instaladas y decidir cuales conviene mantener, revisar o desinstalar manualmente.

## Que hace

- Lista apps instaladas con `PackageManager`.
- Lee uso reciente con `UsageStatsManager`.
- Intenta calcular tamano aproximado con `StorageStatsManager`.
- Guia al usuario para activar Usage Access.
- Clasifica apps como `Mantener`, `Revisar`, `Candidata a borrar` o `No tocar`.
- Exporta un informe CSV con todas las apps cargadas para revisarlo fuera del celular.
- Nunca desinstala apps automaticamente.
- Abre la pantalla de ajustes de cada app al tocarla.

## Permisos

La app necesita que el usuario active manualmente el permiso de acceso de uso en:

`Ajustes > Seguridad/Privacidad > Acceso de uso > App Cleaner Advisor`

En Android, `PACKAGE_USAGE_STATS` no se puede pedir con un dialogo runtime normal, por eso la app abre la pantalla del sistema correspondiente.

## Abrir el proyecto

Abre esta carpeta en Android Studio y sincroniza Gradle. Si Android Studio sugiere actualizar Android Gradle Plugin o Compose BOM, puedes aceptar la actualizacion.
