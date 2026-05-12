package sn.rts.caisse.guichet.util;

import javafx.application.Platform;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Gestionnaire de thème centralisé.
 *
 * <h2>Utilisation</h2>
 * <pre>
 *   // Au démarrage de l'application, après création de la Scene :
 *   ThemeManager.getInstance().register(scene);
 *
 *   // Pour basculer entre clair/sombre :
 *   ThemeManager.getInstance().toggle();
 *
 *   // Pour forcer un thème :
 *   ThemeManager.getInstance().setTheme(ThemeManager.Theme.LIGHT);
 *
 *   // Pour interroger le thème courant :
 *   boolean dark = ThemeManager.getInstance().isDark();
 * </pre>
 *
 * <h2>Comportement</h2>
 * <ul>
 *   <li>La préférence est persistée via {@link Preferences} (par utilisateur,
 *       survit aux redémarrages).</li>
 *   <li>Toutes les scènes enregistrées via {@link #register(Scene)} sont
 *       automatiquement mises à jour lors d'un changement.</li>
 *   <li>Les références sont faibles : pas de fuite mémoire si une scène
 *       n'est plus utilisée.</li>
 * </ul>
 */
public final class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    // ──────────────────────────────────────────────────────────────────────
    //  Singleton
    // ──────────────────────────────────────────────────────────────────────
    private static final ThemeManager INSTANCE = new ThemeManager();
    public static ThemeManager getInstance() { return INSTANCE; }

    // ──────────────────────────────────────────────────────────────────────
    //  Énumération des thèmes
    // ──────────────────────────────────────────────────────────────────────
    public enum Theme {
        DARK("/css/style.css",        "Mode sombre"),
        LIGHT("/css/style-light.css", "Mode clair");

        public final String cssPath;
        public final String label;

        Theme(String cssPath, String label) {
            this.cssPath = cssPath;
            this.label = label;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  État
    // ──────────────────────────────────────────────────────────────────────
    private static final String PREF_KEY = "ui.theme";

    private final Preferences prefs =
            Preferences.userNodeForPackage(ThemeManager.class);

    /** Référence faible vers chaque Scene enregistrée. */
    private final List<WeakReference<Scene>> scenes = new ArrayList<>();

    /** Listeners notifiés à chaque changement (pour mettre à jour les icônes UI). */
    private final List<Runnable> listeners = new ArrayList<>();

    private Theme current;

    // ──────────────────────────────────────────────────────────────────────
    //  Constructeur — restaure la préférence
    // ──────────────────────────────────────────────────────────────────────
    private ThemeManager() {
        String saved = prefs.get(PREF_KEY, Theme.DARK.name());
        Theme initial;
        try {
            initial = Theme.valueOf(saved);
        } catch (IllegalArgumentException ex) {
            log.warn("Préférence de thème invalide '{}', fallback DARK", saved);
            initial = Theme.DARK;
        }
        this.current = initial;
        log.info("Thème initial : {}", current);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  API publique
    // ──────────────────────────────────────────────────────────────────────

    public Theme getCurrent() { return current; }
    public boolean isDark()    { return current == Theme.DARK; }
    public boolean isLight()   { return current == Theme.LIGHT; }

    /**
     * Enregistre une Scene auprès du gestionnaire. La feuille de style
     * du thème courant lui est appliquée immédiatement, et toute future
     * bascule la mettra à jour automatiquement.
     */
    public void register(Scene scene) {
        if (scene == null) return;
        purgeDeadReferences();
        scenes.add(new WeakReference<>(scene));
        applyTo(scene);
    }

    /**
     * Désenregistre une Scene (optionnel — utile si on veut figer son
     * thème ou si on garde la référence longue).
     */
    public void unregister(Scene scene) {
        if (scene == null) return;
        scenes.removeIf(ref -> {
            Scene s = ref.get();
            return s == null || s == scene;
        });
    }

    /**
     * Bascule entre clair et sombre.
     */
    public void toggle() {
        setTheme(current == Theme.DARK ? Theme.LIGHT : Theme.DARK);
    }

    /**
     * Force un thème spécifique.
     */
    public void setTheme(Theme theme) {
        if (theme == null || theme == current) return;

        Theme previous = this.current;
        this.current = theme;
        prefs.put(PREF_KEY, theme.name());
        log.info("Bascule de thème : {} → {}", previous, theme);

        // Appliquer à toutes les scènes vivantes
        Runnable apply = () -> {
            purgeDeadReferences();
            for (WeakReference<Scene> ref : scenes) {
                Scene s = ref.get();
                if (s != null) applyTo(s);
            }
            // Notifier les listeners (boutons toggle, etc.)
            for (Runnable l : listeners) {
                try { l.run(); } catch (Exception e) {
                    log.warn("Erreur dans un listener de thème", e);
                }
            }
        };

        if (Platform.isFxApplicationThread()) apply.run();
        else Platform.runLater(apply);
    }

    /**
     * Ajoute un callback exécuté à chaque changement de thème.
     * Utile pour mettre à jour l'icône d'un bouton soleil/lune.
     */
    public void addChangeListener(Runnable listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Implémentation interne
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Applique le CSS du thème courant à une Scene en remplaçant toute
     * feuille de style /css/style*.css déjà présente.
     */
    private void applyTo(Scene scene) {
        URL url = ThemeManager.class.getResource(current.cssPath);
        if (url == null) {
            log.error("Feuille de style introuvable : {}", current.cssPath);
            return;
        }
        String href = url.toExternalForm();

        // Retirer toutes les autres feuilles de thème éventuelles
        scene.getStylesheets().removeIf(s ->
                s.endsWith("/style.css") || s.endsWith("/style-light.css"));

        // Ajouter celle du thème courant si pas déjà là
        if (!scene.getStylesheets().contains(href)) {
            scene.getStylesheets().add(href);
        }
    }

    /** Nettoie les WeakReferences dont la Scene a été collectée. */
    private void purgeDeadReferences() {
        Iterator<WeakReference<Scene>> it = scenes.iterator();
        while (it.hasNext()) {
            if (it.next().get() == null) it.remove();
        }
    }
}
