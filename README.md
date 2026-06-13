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
├── worker/      SyncScheduler + SyncPendingMessagesWorker
├── service/     GatewayForegroundService (connexion persistante)
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
2. Renseigner `Base URL`, `API key`, `API secret` → **Enregistrer & connecter**.
3. La carte d'état doit afficher `Connectée` après quelques secondes.

Le serveur doit exposer `POST /api/authentication_token` (échange clé/secret →
JWT), les endpoints `/api/mobile/...` et Mercure sur `/.well-known/mercure`.
Contrat complet : [`docs/server-integration.md`](docs/server-integration.md).

## Tests

```bash
./gradlew testDebugUnitTest
```
