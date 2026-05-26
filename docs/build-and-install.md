# Build et installation de l'app interne

L'app SMS Gateway est distribuée hors Play Store, par sideload. Cette doc
décrit comment générer la clé de signature, construire un APK release signé,
et l'installer sur un téléphone Android via USB ou adb réseau.

## Prérequis

- JDK 17
- Android SDK (variable `ANDROID_HOME` ou `ANDROID_SDK_ROOT`) avec build-tools 34.x
- `adb` accessible dans le `PATH`
- Téléphone avec **Options développeur** activées + **Débogage USB** ON

## Étape 1 — générer le keystore (une seule fois)

```bash
scripts/generate-keystore.sh
```

Saisir un mot de passe store et un mot de passe key (peuvent être identiques).
Le keystore est créé sous `keystore/sms-gateway.keystore`.

**Sauvegarder le keystore + les mots de passe.** Toute mise à jour future devra
être signée avec le même keystore, sinon Android refuse l'install (signature
mismatch).

## Étape 2 — exporter les variables d'env de signature

Dans `~/.bashrc` / `~/.zshrc`, ou dans un fichier `~/.gradle/gradle.properties`
(format `SMS_GATEWAY_KEYSTORE=...`) :

```bash
export SMS_GATEWAY_KEYSTORE="$HOME/Documents/sms-api/keystore/sms-gateway.keystore"
export SMS_GATEWAY_KEYSTORE_PASSWORD="..."
export SMS_GATEWAY_KEY_ALIAS="sms-gateway"
export SMS_GATEWAY_KEY_PASSWORD="..."
```

## Étape 3 — construire l'APK release

```bash
scripts/build-release.sh
```

L'APK signé est généré dans `app/build/outputs/apk/release/app-release.apk`.

## Étape 4 — installer sur le téléphone

USB :

```bash
adb devices            # vérifier qu'un device apparaît
scripts/install.sh
```

Réseau (Android 11+) :

```bash
adb pair  <ip>:<port>  # une seule fois, code affiché sur le téléphone
adb connect <ip>:5555
scripts/install.sh
```

## Étape 5 — premier lancement sur le téléphone

1. Ouvrir l'app depuis le tiroir.
2. Accepter les permissions : SMS (envoyer/recevoir/lire) + Notifications.
3. Renseigner `Base URL`, `API key`, `API secret` puis **Enregistrer & connecter**.
4. La carte d'état doit afficher `Connectée` après quelques secondes.

## Mise à jour

Bump `versionCode` dans `app/build.gradle.kts` (incrément obligatoire).
Refaire `build-release.sh` puis `install.sh` — Android remplace l'APK sans
perte des données locales (Room DB et préférences chiffrées conservées).

## Désinstallation propre

```bash
adb uninstall com.example.smsgateway
```

Cela efface la base SMS et la session API stockée.
