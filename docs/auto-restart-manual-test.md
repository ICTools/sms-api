# Relance automatique : test manuel sur l'appareil

À faire sur le téléphone de production (Fairphone 6, Android 16) après
installation d'un APK contenant ICTools/sms-api#1. Ordinateur avec `adb`
connecté (USB ou réseau, voir [`build-and-install.md`](build-and-install.md)).

## Pourquoi

- Le service de premier plan est de type `remoteMessaging` : contrairement à
  `dataSync`, Android 15+ autorise son démarrage depuis `BOOT_COMPLETED` et ne
  le limite pas à 6 h par 24 h.
- `BootCompletedReceiver` relance le service après `BOOT_COMPLETED` et
  `MY_PACKAGE_REPLACED` (mise à jour de l'APK).
- `GatewayWatchdogWorker` (WorkManager, 15 min) relance le service s'il ne
  tourne plus. Démarrer un service de premier plan depuis l'arrière-plan exige
  l'exemption d'optimisation de batterie, demandée au premier lancement.
- Le service envoie un heartbeat toutes les 4 min
  ([contrat](server-integration.md#heartbeat)). Chaque envoi est déclenché par
  une alarme `ELAPSED_REALTIME_WAKEUP` (`HeartbeatAlarm`, autorisée en Doze) qui
  réveille le CPU et prend un wakelock partiel de 60 s au plus : un simple
  `delay()` s'arrête quand le CPU dort et dépasserait le seuil de 10 min.

## Préparation

1. Installer l'APK, ouvrir l'appli, accepter les permissions.
2. Accepter la boîte « Laisser la passerelle tourner en continu » →
   **Autoriser**. Vérifier :
   `adb shell dumpsys deviceidle whitelist | grep be.ictools.smsgateway`
   doit lister le paquet.
3. Configurer avec une clé API au scope `sms_gateway`. L'écran doit afficher
   `État SSE: Connectée` et `Heartbeat: OK à HH:MM`.
4. Côté serveur, `/admin/sms` affiche « Passerelle SMS : connectée ».

## 1. Redémarrage du téléphone

1. `adb reboot` (ou redémarrage manuel). **Ne pas ouvrir l'appli.**
2. Déverrouiller une fois le téléphone (les identifiants chiffrés ne sont
   lisibles qu'après le premier déverrouillage).
3. Attendu en moins de 2 min : la notification « SMS Gateway — Connectée »
   réapparaît ; `adb shell dumpsys activity services be.ictools.smsgateway`
   montre `GatewayForegroundService` avec `isForeground=true` et
   `foregroundServiceType=0x200` (remoteMessaging) ; un nouveau heartbeat
   arrive côté serveur.
4. En cas d'échec : `adb logcat -d | grep -iE "GatewayLauncher|ForegroundService"`.

## 2. Mise à jour de l'APK

1. `adb install -r app-release.apk` (même version ou supérieure). Ne pas ouvrir
   l'appli.
2. Attendu en moins d'une minute : notification de nouveau présente, heartbeat
   reçu côté serveur.

## 3. Arrêt par le système → watchdog

1. Arrêter le service sans tuer le processus :
   `adb shell am stopservice be.ictools.smsgateway/.service.GatewayForegroundService`
   (ou tuer le processus : `adb shell am kill be.ictools.smsgateway` ; dans ce
   cas `START_STICKY` relance déjà le service).
2. Forcer le passage du watchdog sans attendre 15 min :
   `adb shell cmd jobscheduler run -f be.ictools.smsgateway <job-id>` (id visible
   dans `adb shell dumpsys jobscheduler | grep -A3 be.ictools.smsgateway`), ou
   attendre au plus 15 min.
3. Attendu : `GatewayWatchdog: Gateway service was not running, restart requested`
   dans `logcat`, notification de nouveau présente.

## 4. Plus de 6 h connecté

Laisser tourner une nuit, écran éteint. Le lendemain : aucune alerte
« hors ligne » reçue, `/admin/sms` toujours « connectée », dernier heartbeat
de moins de 5 min.

## 5. Heartbeat écran éteint (Doze)

1. Débrancher le téléphone, éteindre l'écran.
2. Forcer Doze : `adb shell dumpsys battery unplug` puis
   `adb shell dumpsys deviceidle force-idle`.
3. Vérifier l'alarme : `adb shell dumpsys alarm | grep -A3 be.ictools.smsgateway`
   montre une alarme `ELAPSED_WAKEUP` sur `HeartbeatAlarmReceiver`, prévue dans
   moins de 4 min (7 min au pire si les alarmes exactes sont refusées).
4. Attendre 15 min sans toucher au téléphone. Attendu : côté serveur, au moins
   2 heartbeats, jamais plus de 7 min entre deux ; aucune alerte « hors ligne ».
5. Remettre l'état normal : `adb shell dumpsys deviceidle unforce` puis
   `adb shell dumpsys battery reset`.

## 6. Identifiants masqués

1. Écran Config : la clé s'affiche masquée (`01M6YB…XKV`), le secret
   uniquement comme « Secret configuré ✓ ».
2. **Modifier** demande l'empreinte ou le code de l'appareil ; en cas
   d'annulation, rien n'est modifiable.
3. Champs clé et secret vides : les laisser vides garde les valeurs
   enregistrées ; en saisir un les remplace.
4. **Réinitialiser la configuration** efface clé, secret et JWT : état SSE
   « Configuration manquante ».
5. `adb backup` / sauvegarde Google : `allowBackup="false"` et les règles
   `data_extraction_rules.xml` excluent `gateway_session.xml`.

Résultat à reporter dans la PR ou le ticket : date, version de l'APK, et pour
chaque étape OK / KO avec la ligne de `logcat` en cas de KO.
