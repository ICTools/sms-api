# Integration serveur pour SMS Gateway Android

## Vue d'ensemble

L'application Android sert de passerelle SMS entre un smartphone et le backend.

Le serveur :

- authentifie l'application avec `api key` + `api secret`
- retourne un JWT
- expose des endpoints JSON sous `/api/mobile/...`
- publie les SMS sortants via Mercure sur `sms/outgoing`

L'application :

- récupère un JWT
- écoute Mercure
- envoie physiquement les SMS depuis le téléphone
- remonte au backend les SMS entrants et les statuts

## Authentification

### Requête

L'app doit d'abord appeler :

`POST /api/authentication_token`

avec les headers :

```http
X-Api-Key: <api_key>
X-Api-Secret: <api_secret>
```

### Réponse attendue

```json
{
  "token": "...jwt...",
  "expires_in": 3600,
  "expires_at": 1234567890,
  "type": "api_key",
  "scope": "..."
}
```

L'application n'utilise que `token`. Les autres champs peuvent être ignorés côté mobile.

### Appels suivants

Une fois le JWT obtenu, l'app appelle l'API avec :

```http
Authorization: Bearer <jwt>
Content-Type: application/json
```

Si une requête métier retourne `401`, l'app redemande un JWT puis rejoue la requête.

## Endpoints mobile SMS

Les endpoints attendus côté backend sont :

- `POST /api/mobile/incoming-sms`
- `POST /api/mobile/outgoing-sms`
- `POST /api/mobile/messages/{ulid}/status`
- `GET /api/mobile/messages/{ulid}`
- `POST /api/mobile/heartbeat` (voir [Heartbeat](#heartbeat))

Le contrôleur serveur mentionné est :

- `src/Controller/Api/MobileSmsController.php`

## Heartbeat

Le service de la passerelle envoie un heartbeat au démarrage puis toutes les
**4 minutes** (`HeartbeatReporter.INTERVAL_MS`), déclenché par une alarme
`ELAPSED_REALTIME_WAKEUP` qui réveille le CPU même écran éteint (`HeartbeatAlarm`,
exacte si Android l'autorise, sinon au plus ~7 min). Côté serveur
(ICTools/chapitreneuf#936), l'alerte e-mail « Passerelle SMS hors ligne » part
quand le dernier heartbeat a plus de `SMS_GATEWAY_OFFLINE_THRESHOLD_MINUTES`
(10 par défaut) ou quand plus personne n'est abonné au topic Mercure
`sms/outgoing`. Le serveur attend une cadence de 2 à 5 minutes.

**Clé API au scope `sms_gateway` obligatoire.** La route refuse tous les autres
scopes avec `403`, y compris `global` (qui garde son accès transitoire aux
autres routes `/api/mobile`). Avec une clé `global`, les SMS passent mais le
heartbeat échoue : l'écran de configuration affiche alors
`Heartbeat: échec … (403 : la clé API n'a pas le scope sms_gateway)`.

```http
POST /api/mobile/heartbeat
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "batteryLevel": 87,
  "charging": true,
  "network": "wifi",
  "appVersion": "1.0"
}
```

Tous les champs sont facultatifs (`null` quand l'info manque) :

- `batteryLevel` : 0-100
- `charging` : en charge ou batterie pleine sur secteur
- `network` : `wifi`, `cellular`, `ethernet`, `other` ou `none`
- `appVersion` : `versionName` de l'APK

Réponse : `{"success": true, "serverTime": "…"}`.

## Contrat des payloads

### SMS entrant remonté par le téléphone

```json
{
  "phoneNumber": "+32470000000",
  "body": "Bonjour",
  "deviceMessageId": 123,
  "sentAt": "2026-03-31T10:15:00+02:00"
}
```

### Statut d'un SMS

```json
{
  "status": "sent",
  "errorMessage": null
}
```

Le backend doit aussi accepter les autres transitions de statut comme `delivered` ou `failed`.

### Récupération d'un message sortant

L'événement Mercure ne contient qu'un `ulid`. L'app récupère ensuite le détail via :

`GET /api/mobile/messages/{ulid}`

Exemple de réponse attendue :

```json
{
  "ulid": "01HV...",
  "phoneNumber": "+32470000000",
  "body": "Bonjour",
  "direction": "outgoing",
  "createdAt": "2026-03-31T10:15:00+02:00"
}
```

## Flux réel de fonctionnement

### 1. Connexion de l'app

1. L'utilisateur saisit `Base URL`, `API key` et `API secret`.
2. L'app appelle `POST /api/authentication_token`.
3. Le serveur retourne un JWT.
4. L'app ouvre ensuite la connexion Mercure avec ce JWT.

### 2. SMS sortant piloté par le serveur

Le serveur ne délègue pas l'envoi à Twilio ou à un autre provider.

Dans :

- `src/Service/Sms/SmsService.php`

le serveur :

1. crée le SMS sortant en base
2. publie un événement Mercure sur `sms/outgoing`

Ensuite, côté mobile :

1. l'app reçoit l'événement Mercure
2. elle récupère le détail du message via `GET /api/mobile/messages/{ulid}`
3. elle envoie le SMS depuis le téléphone
4. elle renvoie les événements backend nécessaires

À minima, le backend doit recevoir :

- `POST /api/mobile/outgoing-sms`
- `POST /api/mobile/messages/{ulid}/status`

### 3. SMS entrant reçu sur le téléphone

1. Android reçoit le SMS
2. l'app l'enregistre localement
3. l'app appelle `POST /api/mobile/incoming-sms`

## Ce qu'il faut côté admin

Il faut créer une clé API dans l'admin :

- `src/Controller/Admin/ApiKeyCrudController.php`

pour un utilisateur ayant :

- `ROLE_API_READER`

L'application mobile doit ensuite utiliser cette paire :

- `api key`
- `api secret`

pour obtenir son JWT.

## Conditions nécessaires pour que l'app fonctionne

- une `Base URL` joignable depuis le téléphone
- une paire `api key` / `api secret` valide, au scope `sms_gateway` (heartbeat)
- l'endpoint `POST /api/authentication_token`
- les endpoints `/api/mobile/...`
- Mercure accessible sur `/.well-known/mercure`
- un JWT accepté à la fois par l'API HTTP et par Mercure
- les permissions SMS et notifications acceptées sur Android

## Points d'attention

- le téléphone peut être hors ligne ; le backend doit tolérer les retards et doublons
- les endpoints mobile doivent être idempotents autant que possible
- la publication Mercure signale un SMS à expédier, pas un SMS déjà envoyé
- la confirmation réelle vient ensuite des callbacks mobiles remontés au backend
