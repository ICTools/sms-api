# SMS Gateway (Android)

Passerelle SMS Android : relaie les SMS entre un smartphone et un backend via
REST + Mercure (SSE). Le serveur publie les SMS sortants ; le téléphone les
envoie physiquement, puis remonte les SMS entrants et les statuts de livraison.

`applicationId` : `be.ictools.smsgateway`

## Stack

- Kotlin + Jetpack Compose (Material 3, Navigation)
- Room (cache local) + WorkManager (sync différée)
- OkHttp + `okhttp-sse` (client Mercure auto-réparant, backoff + conscience réseau)
- `security-crypto` (stockage chiffré des secrets API et du JWT)
- `minSdk 29` · `targetSdk 34` · JDK 17

## Architecture

```
app/src/main/java/be/ictools/smsgateway/
├── data/        api · sse (Mercure) · auth · local (Room) · repository
├── sms/         DeviceSmsGateway — envoi/réception SMS réels
├── receiver/    SmsReceived/Sent/Delivered + BootCompleted
├── worker/      SyncScheduler + SyncPendingMessagesWorker + GatewayWatchdogWorker
├── service/     GatewayForegroundService (connexion persistante, heartbeat)
├── ui/          écrans Compose + GatewayViewModel
└── domain/      Models, SseState
```

## Build & installation

Distribution hors Play Store, par sideload. Les scripts résolvent
automatiquement le JDK (JBR d'Android Studio ou OpenJDK système) si
`JAVA_HOME` n'est pas défini.

```bash
scripts/generate-keystore.sh        # 1× — génère keystore/sms-gateway.keystore
# exporter SMS_GATEWAY_KEYSTORE[_PASSWORD] / KEY_ALIAS / KEY_PASSWORD
scripts/build-release.sh            # → app/build/outputs/apk/release/app-release.apk
scripts/install.sh                  # via adb (USB ou réseau)
```

Détail complet (keystore, signature, adb réseau, mise à jour) :
[`docs/build-and-install.md`](docs/build-and-install.md).

## Configuration (premier lancement)

1. Accepter les permissions : SMS (envoyer/recevoir/lire) + Notifications.
2. Autoriser l'appli à ignorer l'optimisation de batterie (boîte affichée au
   lancement) : sans elle, Doze coupe le flux et le watchdog ne peut pas
   relancer le service.
3. Renseigner `Base URL`, `API key`, `API secret` → **Enregistrer & connecter**.
   La clé doit avoir le scope **`sms_gateway`** (une clé `global` reçoit `403`
   sur le heartbeat).
4. La carte d'état doit afficher `Connectée` et `Heartbeat: OK` après quelques
   secondes.

Une fois enregistrés, le secret n'est plus jamais affiché (« Secret configuré
✓ ») et la clé est masquée ; les modifier demande le verrouillage de
l'appareil. La passerelle redémarre seule après un redémarrage du téléphone,
une mise à jour de l'APK ou un arrêt par le système (watchdog 15 min). Test
manuel : [`docs/auto-restart-manual-test.md`](docs/auto-restart-manual-test.md).

Le serveur doit exposer `POST /api/authentication_token` (échange clé/secret →
JWT), les endpoints `/api/mobile/...` et Mercure sur `/.well-known/mercure`.
Contrat complet : [`docs/server-integration.md`](docs/server-integration.md).

## Tests

```bash
./gradlew testDebugUnitTest
```
