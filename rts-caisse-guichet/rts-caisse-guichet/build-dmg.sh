#!/usr/bin/env bash
# =============================================================================
#  Build d'un installateur macOS (.dmg ou .pkg) pour le client JavaFX
#  RTS Caisse.
#
#  Pipeline (équivalent macOS de build-msi.ps1) :
#    1. Maven : génère le fat-JAR
#    2. jlink : runtime Java minimal avec les modules JavaFX
#    3. jpackage : génère le .dmg (ou .pkg) — repose sur hdiutil/pkgbuild
#                  fournis nativement par macOS, aucun outil tiers requis.
#
#  Limitations :
#    - jpackage NE FAIT PAS de cross-compile : ce script ne fonctionne que
#      sur macOS. Pour générer un installateur Windows, utilise build-msi.ps1
#      depuis un poste Windows.
#    - Sans signature/notarisation Apple, macOS Gatekeeper affichera un
#      avertissement à la première ouverture (l'utilisateur doit faire
#      Clic-droit > Ouvrir une fois). Pour une distribution propre,
#      utilise --sign avec un Developer ID Apple.
#
#  Exemples :
#    ./build-dmg.sh
#    ./build-dmg.sh --version 1.2.0 --clean
#    ./build-dmg.sh --backend-url "http://192.168.1.50:8080/api"
#    ./build-dmg.sh --type pkg
#    ./build-dmg.sh --jdk-home "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
#    ./build-dmg.sh --sign "Developer ID Application: RTS Senegal (ABC123XYZ)"
# =============================================================================

set -euo pipefail

# ============================================================
# Paramètres CLI
# ============================================================
VERSION="1.0.0"
BACKEND_URL="http://localhost:8090/api"
CLEAN=false
PKG_TYPE="dmg"   # dmg | pkg
JDK_HOME=""
SIGN_IDENTITY=""

print_usage() {
    cat <<USAGE
Usage : $0 [options]

Options :
  --version <x.y.z>        Version de l'application (défaut: 1.0.0)
  --backend-url <url>      URL de l'API backend (défaut: http://localhost:8090/api)
  --type <dmg|pkg>         Format installateur (défaut: dmg)
  --jdk-home <path>        Force JAVA_HOME sur ce chemin
  --sign <identity>        Identité de signature Apple Developer ID
  --clean                  Supprime build/ avant le build
  -h, --help               Affiche cette aide
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)      VERSION="$2"; shift 2 ;;
        --backend-url)  BACKEND_URL="$2"; shift 2 ;;
        --type)         PKG_TYPE="$2"; shift 2 ;;
        --jdk-home)     JDK_HOME="$2"; shift 2 ;;
        --sign)         SIGN_IDENTITY="$2"; shift 2 ;;
        --clean)        CLEAN=true; shift ;;
        -h|--help)      print_usage; exit 0 ;;
        *)              echo "Option inconnue : $1" >&2; print_usage; exit 1 ;;
    esac
done

if [[ "$PKG_TYPE" != "dmg" && "$PKG_TYPE" != "pkg" ]]; then
    echo "Erreur : --type doit valoir 'dmg' ou 'pkg' (reçu : $PKG_TYPE)" >&2
    exit 1
fi

# ============================================================
# Couleurs pour le terminal
# ============================================================
if [[ -t 1 ]]; then
    C_CYAN='\033[1;36m'
    C_GREEN='\033[1;32m'
    C_YELLOW='\033[1;33m'
    C_RED='\033[1;31m'
    C_GRAY='\033[0;90m'
    C_RESET='\033[0m'
else
    C_CYAN=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_GRAY=''; C_RESET=''
fi

log_info()  { printf "${C_CYAN}%s${C_RESET}\n" "$1"; }
log_step()  { printf "${C_YELLOW}%s${C_RESET}\n" "$1"; }
log_ok()    { printf "${C_GREEN}%s${C_RESET}\n" "$1"; }
log_warn()  { printf "${C_YELLOW}[WARN] %s${C_RESET}\n" "$1" >&2; }
log_err()   { printf "${C_RED}[ERREUR] %s${C_RESET}\n" "$1" >&2; }
log_dim()   { printf "${C_GRAY}%s${C_RESET}\n" "$1"; }

# ============================================================
# Sécurité plateforme : refuser tout ce qui n'est pas macOS
# ============================================================
if [[ "$(uname -s)" != "Darwin" ]]; then
    log_err "Ce script ne fonctionne que sur macOS (uname -s = $(uname -s))."
    log_err "jpackage ne supporte pas le cross-compile."
    log_err "Pour Windows : utilise build-msi.ps1 depuis un poste Windows."
    exit 1
fi

# ============================================================
# Configuration projet (alignée sur build-msi.ps1)
# ============================================================
APP_NAME="RTS Caisse Client"
APP_VENDOR="RTS Sénégal"
APP_COPYRIGHT="© $(date +%Y) RTS Sénégal"
APP_DESCRIPTION="Client de caisse pour les guichets RTS"
MAIN_JAR_NAME="rts-caisse-guichet.jar"
MAIN_CLASS="sn.rts.caisse.guichet.app.Launcher"
APP_BUNDLE_ID="sn.rts.caisse.guichet"

ICON_ICNS="src/main/resources/icons/rts.icns"
ICON_PNG="src/main/resources/icons/rts.png"
LICENSE_PATH="LICENSE.txt"

BUILD_DIR="build"
RUNTIME_DIR="$BUILD_DIR/runtime"
INPUT_DIR="$BUILD_DIR/input"
OUTPUT_DIR="$BUILD_DIR/$PKG_TYPE"

MODULES_LIST=(
    java.base
    java.desktop
    java.logging
    java.naming
    java.net.http
    java.prefs
    java.sql
    java.xml
    jdk.crypto.ec
    jdk.unsupported
    javafx.controls
    javafx.fxml
    javafx.graphics
)

# ============================================================
# Bannière
# ============================================================
echo ""
log_info "============================================"
log_info " RTS Caisse Client - Build $PKG_TYPE v$VERSION"
log_info "============================================"

# ============================================================
# JDK : préparer JAVA_HOME / outils
# ============================================================
if [[ -n "$JDK_HOME" ]]; then
    if [[ ! -d "$JDK_HOME" ]]; then
        log_err "JdkHome introuvable : $JDK_HOME"; exit 1
    fi
    export JAVA_HOME="$JDK_HOME"
    export PATH="$JDK_HOME/bin:$PATH"
    log_dim "[INFO] JAVA_HOME forcé sur : $JDK_HOME"
elif [[ -z "${JAVA_HOME:-}" ]]; then
    # Tente de résoudre via /usr/libexec/java_home (macOS)
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        if AUTO_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
            export JAVA_HOME="$AUTO_HOME"
            log_dim "[INFO] JAVA_HOME résolu automatiquement : $JAVA_HOME"
        fi
    fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    log_err "JAVA_HOME non défini. Utilise --jdk-home ou exporte JAVA_HOME."
    exit 1
fi

JAVA_EXE="$JAVA_HOME/bin/java"
JLINK_EXE="$JAVA_HOME/bin/jlink"
JPACKAGE_EXE="$JAVA_HOME/bin/jpackage"

[[ -x "$JAVA_EXE"     ]] || { log_err "java introuvable : $JAVA_EXE"; exit 1; }
[[ -x "$JLINK_EXE"    ]] || { log_err "jlink introuvable : $JLINK_EXE — JAVA_HOME pointe-t-il vers un JDK ?"; exit 1; }
[[ -x "$JPACKAGE_EXE" ]] || { log_err "jpackage introuvable : $JPACKAGE_EXE — JAVA_HOME pointe-t-il vers un JDK ?"; exit 1; }

# ============================================================
# Maven
# ============================================================
command -v mvn >/dev/null 2>&1 || {
    log_err "Maven manquant. Installe-le (brew install maven) et rajoute-le au PATH."
    exit 1
}

# ============================================================
# JavaFX JMods
# ============================================================
if [[ -z "${JAVAFX_JMODS:-}" ]]; then
    log_err "JAVAFX_JMODS non défini. Pointe-le vers le dossier des jmods JavaFX 21."
    log_dim "Téléchargement : https://gluonhq.com/products/javafx/ (Type = jmods)"
    log_dim "Puis : export JAVAFX_JMODS=\"/path/to/javafx-jmods-21\""
    exit 1
fi
[[ -d "$JAVAFX_JMODS" ]] || { log_err "JAVAFX_JMODS pointe vers un dossier inexistant : $JAVAFX_JMODS"; exit 1; }

# ============================================================
# Vérification version Java
# ============================================================
JAVA_VER_LINE="$("$JAVA_EXE" -version 2>&1 | head -1)"
if [[ "$JAVA_VER_LINE" != *'"21.'* ]]; then
    log_warn "Tu n'utilises pas un JDK 21 ($JAVA_VER_LINE)."
    log_warn "JavaFX JMods 21 est conçu pour le JDK 21."
    log_warn "Utilise --jdk-home '/path/to/jdk-21' pour forcer la version."
fi

log_ok "[OK] JDK       : $JAVA_VER_LINE"
log_ok "[OK] JAVA_HOME : $JAVA_HOME"
log_ok "[OK] jlink     : $JLINK_EXE"
log_ok "[OK] jpackage  : $JPACKAGE_EXE"
log_ok "[OK] JavaFX    : $JAVAFX_JMODS"
log_ok "[OK] MainClass : $MAIN_CLASS"
log_ok "[OK] Type      : $PKG_TYPE"
echo ""

# ============================================================
# 1. Nettoyage
# ============================================================
if $CLEAN && [[ -d "$BUILD_DIR" ]]; then
    log_step "[1/4] Nettoyage du dossier $BUILD_DIR..."
    rm -rf "$BUILD_DIR"
fi
mkdir -p "$BUILD_DIR" "$INPUT_DIR" "$OUTPUT_DIR"

# ============================================================
# 2. Maven : build du fat-JAR
# ============================================================
log_step "[2/4] Maven package..."
export RTS_BACKEND_URL="$BACKEND_URL"
mvn clean package -DskipTests "-Dbackend.url=$BACKEND_URL"

# Trouve le fat-JAR (exclut original/sources/javadoc)
SHADED_JAR="$(find target -maxdepth 1 -name "*.jar" \
    -not -name "*-original*" \
    -not -name "*-sources*" \
    -not -name "*-javadoc*" \
    | head -1 || true)"

[[ -n "$SHADED_JAR" ]] || { log_err "Aucun JAR trouvé dans target/. Vérifie ta config maven-shade-plugin."; exit 1; }

JAR_SIZE_MB=$(awk "BEGIN { printf \"%.2f\", $(stat -f%z "$SHADED_JAR") / 1048576 }")
if (( $(awk "BEGIN { print ($JAR_SIZE_MB < 5) }") )); then
    log_warn "JAR suspect : $JAR_SIZE_MB MB (attendu > 5 MB pour un fat-JAR JavaFX)."
    log_warn "Vérifie que maven-shade-plugin est bien configuré dans pom.xml."
fi

cp -f "$SHADED_JAR" "$INPUT_DIR/$MAIN_JAR_NAME"
log_dim "      JAR copié : $INPUT_DIR/$MAIN_JAR_NAME ($JAR_SIZE_MB MB)"

# ============================================================
# 3. jlink : runtime Java minimal
# ============================================================
log_step "[3/4] jlink : création du runtime Java custom..."

[[ -d "$RUNTIME_DIR" ]] && rm -rf "$RUNTIME_DIR"

MODULE_PATH="$JAVA_HOME/jmods:$JAVAFX_JMODS"
MODULES_CSV="$(IFS=','; echo "${MODULES_LIST[*]}")"

"$JLINK_EXE" \
    --module-path "$MODULE_PATH" \
    --add-modules "$MODULES_CSV" \
    --output "$RUNTIME_DIR" \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6

RUNTIME_SIZE_MB=$(du -sm "$RUNTIME_DIR" | awk '{print $1}')
log_dim "      Runtime généré : $RUNTIME_DIR (~${RUNTIME_SIZE_MB} MB)"

# ============================================================
# 4. Préparation icône (.icns obligatoire pour macOS)
# ============================================================
ICON_ARG=""
if [[ -f "$ICON_ICNS" ]]; then
    ICON_ARG="$ICON_ICNS"
    log_dim "      Icône .icns trouvée : $ICON_ICNS"
elif [[ -f "$ICON_PNG" ]] && command -v iconutil >/dev/null 2>&1 && command -v sips >/dev/null 2>&1; then
    log_step "      Génération .icns à la volée depuis $ICON_PNG..."
    ICONSET_DIR="$BUILD_DIR/rts.iconset"
    GENERATED_ICNS="$BUILD_DIR/rts.icns"
    rm -rf "$ICONSET_DIR" "$GENERATED_ICNS"
    mkdir -p "$ICONSET_DIR"

    # Tailles requises par iconutil
    for spec in "16,16" "32,16@2x" "32,32" "64,32@2x" "128,128" "256,128@2x" "256,256" "512,256@2x" "512,512" "1024,512@2x"; do
        size="${spec%%,*}"
        suffix="${spec##*,}"
        sips -z "$size" "$size" "$ICON_PNG" \
            --out "$ICONSET_DIR/icon_${suffix}.png" >/dev/null
    done

    iconutil -c icns "$ICONSET_DIR" -o "$GENERATED_ICNS"
    rm -rf "$ICONSET_DIR"
    ICON_ARG="$GENERATED_ICNS"
    log_dim "      .icns généré : $GENERATED_ICNS"
else
    log_warn "Pas de $ICON_ICNS et pas de $ICON_PNG : icône Java par défaut."
fi

# ============================================================
# 5. jpackage : génération du .dmg / .pkg
# ============================================================
log_step "[4/4] jpackage : génération du $PKG_TYPE..."

JPACKAGE_ARGS=(
    --type           "$PKG_TYPE"
    --name           "$APP_NAME"
    --app-version    "$VERSION"
    --vendor         "$APP_VENDOR"
    --copyright      "$APP_COPYRIGHT"
    --description    "$APP_DESCRIPTION"
    --input          "$INPUT_DIR"
    --main-jar       "$MAIN_JAR_NAME"
    --main-class     "$MAIN_CLASS"
    --runtime-image  "$RUNTIME_DIR"
    --dest           "$OUTPUT_DIR"
    --mac-package-identifier "$APP_BUNDLE_ID"
    --mac-package-name       "RTS Caisse"
)

if [[ "$PKG_TYPE" == "dmg" ]]; then
    JPACKAGE_ARGS+=(--mac-dmg-content "$INPUT_DIR/$MAIN_JAR_NAME")
fi

if [[ -n "$ICON_ARG" ]]; then
    JPACKAGE_ARGS+=(--icon "$ICON_ARG")
fi

if [[ -f "$LICENSE_PATH" ]]; then
    JPACKAGE_ARGS+=(--license-file "$LICENSE_PATH")
fi

if [[ -n "$SIGN_IDENTITY" ]]; then
    JPACKAGE_ARGS+=(
        --mac-sign
        --mac-signing-key-user-name "$SIGN_IDENTITY"
    )
    log_dim "      Signature avec : $SIGN_IDENTITY"
fi

"$JPACKAGE_EXE" "${JPACKAGE_ARGS[@]}"

# ============================================================
# Récap
# ============================================================
ARTIFACT="$(find "$OUTPUT_DIR" -maxdepth 1 -name "*.${PKG_TYPE}" | head -1 || true)"
[[ -n "$ARTIFACT" ]] || { log_err "Aucun .${PKG_TYPE} généré dans $OUTPUT_DIR."; exit 1; }

ARTIFACT_SIZE_MB=$(awk "BEGIN { printf \"%.1f\", $(stat -f%z "$ARTIFACT") / 1048576 }")

echo ""
log_ok "============================================"
log_ok " BUILD TERMINE AVEC SUCCES"
log_ok "============================================"
echo " Fichier   : $ARTIFACT"
echo " Taille    : $ARTIFACT_SIZE_MB MB"
echo " Version   : $VERSION"
echo " Backend   : $BACKEND_URL"
echo " Type      : $PKG_TYPE"
log_ok "============================================"
echo ""
echo "POUR INSTALLER :"
if [[ "$PKG_TYPE" == "dmg" ]]; then
    log_dim "  open \"$ARTIFACT\"      puis glisser-déposer dans Applications"
else
    log_dim "  sudo installer -pkg \"$ARTIFACT\" -target /"
fi

if [[ -z "$SIGN_IDENTITY" ]]; then
    echo ""
    log_warn "Application NON SIGNÉE : à la première ouverture, l'utilisateur"
    log_warn "verra un avertissement Gatekeeper. Solution :"
    log_dim "  Clic-droit sur l'app dans /Applications > Ouvrir > Confirmer"
    log_dim "Pour une distribution propre, relance avec --sign \"Developer ID Application: ...\""
fi
echo ""
