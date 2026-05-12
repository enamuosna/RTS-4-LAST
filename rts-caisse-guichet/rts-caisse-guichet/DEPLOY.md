# RTS Caisse Client — Notes de déploiement MSI

## 🛠 Build

```powershell
# Build standard (backend = localhost:8080)
.\build-msi.ps1

# Build pour environnement de prod
.\build-msi.ps1 -Version "1.0.0" -BackendUrl "http://srv-caisse.rts.local:8080/api" -Clean

# Build pour installation par utilisateur (pas besoin d'être admin)
.\build-msi.ps1 -InstallType per-user
```

Le MSI final apparaît dans `build\msi\RTS Caisse Client-1.0.0.msi`.

## 📥 Installation sur un poste caissier

### Mode interactif
Double-clic sur le `.msi` → suivre l'assistant.

### Mode silencieux (utile pour GPO / SCCM)
```cmd
msiexec /i "RTS Caisse Client-1.0.0.msi" /qn /l*v "C:\Temp\rts-install.log"
```

Options utiles :
- `/qn` : aucune UI
- `/qb` : barre de progression seulement
- `/l*v "fichier.log"` : log verbeux pour debug
- `INSTALLDIR="D:\RTS\Caisse"` : dossier d'install custom

### Désinstallation silencieuse
```cmd
msiexec /x "RTS Caisse Client-1.0.0.msi" /qn
```

## 🔄 Stratégie de mise à jour

Le `--win-upgrade-uuid` (GUID figé dans `build-msi.ps1`) permet à un nouveau MSI
**avec un numéro de version supérieur** de désinstaller automatiquement l'ancien
avant d'installer le nouveau.

⚠️ **Critique** : ne change JAMAIS ce GUID entre versions, sinon le mécanisme
d'upgrade casse et tu te retrouves avec deux versions installées en parallèle.

## 🐛 Pièges classiques

### "WiX Toolset is not found"
WiX 3.14 doit être dans le PATH. Vérifie avec `candle -?`. Le 4.x ne marche
pas avec jpackage actuellement.

### Le client se ferme silencieusement au démarrage
Reconstruis avec l'option console pour voir le stack trace :
```powershell
# Décommenter la ligne $jpackageArgs += '--win-console' dans build-msi.ps1
.\build-msi.ps1 -Clean
```
Puis lance le `.exe` depuis `cmd` au lieu d'un raccourci.

### "ClassNotFoundException: javafx.application.Application"
Tu as oublié le module `javafx.controls` (ou `javafx.fxml`, etc.) dans
`$ModulesList`. Ajuste la liste dans `build-msi.ps1`.

### MSI signé ?
Pour la prod RTS, signe le MSI avec un certificat de l'organisation pour
éviter les avertissements SmartScreen :
```powershell
signtool sign /f cert.pfx /p MOTDEPASSE /t http://timestamp.digicert.com `
    /fd sha256 /td sha256 "build\msi\RTS Caisse Client-1.0.0.msi"
```

## 📊 Tailles attendues

| Composant            | Taille   |
| -------------------- | -------- |
| Fat-JAR appli        | ~5-15 MB |
| Runtime jlink        | ~50 MB   |
| **MSI final**        | **~40-60 MB** (compressé) |

## 🎯 Checklist avant déploiement RTS

- [ ] Icône `.ico` 256x256 multi-résolutions dans `src/main/resources/icons/rts.ico`
- [ ] Numéro de version incrémenté dans le `pom.xml` ET passé à `-Version`
- [ ] URL backend prod testée avant build (`curl http://srv-caisse.rts.local:8080/api/actuator/health`)
- [ ] MSI testé sur un poste vierge (VM Windows 10/11 sans Java installé)
- [ ] MSI signé avec le certificat RTS si disponible
- [ ] Procédure de rollback documentée (garder la version N-1 quelque part)
