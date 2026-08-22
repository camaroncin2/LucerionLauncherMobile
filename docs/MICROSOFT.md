# Activar el inicio de sesión con Microsoft

El código del launcher ya está completo: usa el **flujo de código de
dispositivo**, el inquilino `consumers` y el permiso `XboxLive.signin
offline_access`, que es exactamente lo que Microsoft exige. Lo único que
falta es un **identificador de aplicación de Azure aprobado para la API de
Minecraft**, que solo puede pedir el estudio.

Sin ese identificador la app no falla de forma rara: la pantalla de Cuenta
dice que la opción no está habilitada.

---

## Paso 1 — Registrar la aplicación en Azure (gratis, 5 minutos)

1. Entra en <https://portal.azure.com> con la cuenta Microsoft del estudio
   (la misma que uses para administrar Cretania, para no perderla).
2. Busca **Microsoft Entra ID** → **Registros de aplicaciones** → **Nuevo
   registro**.
3. Rellena:
   - **Nombre**: `Lucerion Launcher`
   - **Tipos de cuenta admitidos**: **Solo cuentas personales de Microsoft**.
     Es lo que corresponde: las cuentas de Minecraft son personales, y el
     launcher pide el inquilino `consumers`. (También sirve la opción
     multiinquilino + cuentas personales, pero esta es la que encaja exacto.)
   - **URI de redireccionamiento**: déjalo vacío por ahora.
4. **Registrar**.

## Paso 2 — Marcarla como cliente público

El flujo de código de dispositivo solo funciona si Azure sabe que la
aplicación no guarda secretos (una app móvil no puede guardarlos).

1. En la aplicación → **Administrar** → **Autenticación**.
2. Abajo del todo, **Configuración avanzada** → **Permitir flujos de cliente
   público** → **Sí** → **Guardar**.
3. **No** crees ningún secreto de cliente: el launcher no usa ninguno.

## Paso 3 — Copiar el identificador

En **Información general**, copia el **Id. de aplicación (cliente)**. Es un
código con formato `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`.

Este identificador **no es un secreto** (los launchers públicos lo llevan
dentro), pero igual lo mantenemos fuera del repositorio porque es público y
así evitamos que alguien lo use en otra app y nos quemen la aprobación.

## Paso 4 — Pedir el permiso para la API de Minecraft

**Este es el paso que no se puede saltar.** Una aplicación recién creada NO
puede hablar con `api.minecraftservices.com`: devuelve error 403 hasta que
Microsoft la aprueba.

1. Abre el formulario de revisión: <https://aka.ms/mce-reviewappid>
2. Envía el identificador de aplicación del paso 3 y los datos del proyecto
   (nombre del launcher, para qué se usa, enlace al repositorio o a la web).
   Que quede claro que es un launcher para un servidor propio y que solo
   autentica a jugadores que ya poseen Minecraft Java.
3. Espera la respuesta. Microsoft revisa a mano; una vez aprobada, pueden
   pasar hasta 24 horas más hasta que el permiso surta efecto.

> Conviene **probar el inicio de sesión antes de enviar el formulario**: el
> intento fallido deja rastro en los registros de Microsoft y ayuda a que la
> revisión encuentre la aplicación.

## Paso 5 — Poner el identificador en la compilación

En la raíz del repositorio, en `local.properties` (que git ignora):

```
lucerion.oauthClientId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

O como variable de entorno antes de compilar:

```powershell
$env:LUCERION_OAUTH_CLIENT_ID = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

Recompila y listo: la tarjeta de Microsoft en la pantalla de Cuenta pasa de
"no está habilitada" a ofrecer el botón de inicio de sesión.

---

## Cómo lo vive el jugador

1. Cuenta → **INICIAR SESIÓN CON MICROSOFT**.
2. La app muestra un **código corto** y la dirección
   `microsoft.com/link`.
3. El jugador entra ahí (desde el mismo teléfono o desde cualquier otro
   dispositivo), escribe el código y aprueba.
4. La app lo detecta sola y guarda la sesión.

Su contraseña se escribe **solo en la página de Microsoft**. El launcher
nunca la ve: solo recibe un testigo de acceso y otro de refresco.

---

## Qué pasa si Microsoft no aprueba la aplicación

Nada se rompe: Cretania **no necesita** cuentas de Microsoft. El servidor
autentica con su propio sistema y el apodo alcanza para jugar. La cuenta de
Microsoft solo aporta jugar con la identidad y la skin oficiales.

Si el permiso llega y luego falla la sesión, el launcher tampoco bloquea la
entrada: registra el motivo y sigue con la cuenta libre.

---

## Detalles técnicos (para no volver a investigarlos)

| Qué | Valor |
|---|---|
| Flujo | Código de dispositivo (`urn:ietf:params:oauth:grant-type:device_code`) |
| Inquilino | `consumers` — obligatorio con `XboxLive.signin`; `common` o un inquilino de organización dan error |
| Permiso | `XboxLive.signin offline_access` |
| Secreto de cliente | Ninguno (cliente público) |
| Cadena posterior | Xbox Live → XSTS → `login_with_xbox` → comprobación de propiedad → perfil |
| Error si no está aprobada | 403 de `api.minecraftservices.com` |

Todo eso ya está implementado en `motor/ServicioMicrosoft.kt` y en el motor
(`FCLCore/auth/OAuth.java`, `auth/microsoft/MicrosoftService.java`).

Fuentes: [Microsoft authentication — Minecraft
Wiki](https://minecraft.wiki/w/Microsoft_authentication) ·
[Formulario de revisión](https://aka.ms/mce-reviewappid) ·
[Documentación de Helios Launcher](https://github.com/dscalzi/HeliosLauncher/blob/master/docs/MicrosoftAuth.md)
