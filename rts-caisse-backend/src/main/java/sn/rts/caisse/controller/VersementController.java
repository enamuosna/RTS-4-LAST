package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import sn.rts.caisse.dto.VersementResponse;
import sn.rts.caisse.model.Versement;
import sn.rts.caisse.service.VersementService;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Endpoints REST pour les versements bancaires.
 *
 * <p>L'upload utilise {@code multipart/form-data} (le fichier bordereau est
 * envoyé en pièce jointe avec les autres champs). Le téléchargement renvoie
 * le contenu binaire avec le bon type MIME.</p>
 */
@RestController
@RequestMapping("/api/versements")
@RequiredArgsConstructor
@Tag(name = "Versements bancaires",
        description = "Dépôts d'espèces ou virements internes depuis les caisses RTS")
public class VersementController {

    private final VersementService service;

    // ==================================================================
    //  CREATION (upload multipart)
    // ==================================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Enregistre un versement bancaire avec son bordereau (PDF/JPG/PNG, max 5 Mo)")
    public ResponseEntity<VersementResponse> creer(
            @RequestParam Long caisseId,
            @RequestParam Long banqueId,
            @RequestParam(required = false) Long journalId,
            @RequestParam BigDecimal montant,
            @RequestParam String numeroBordereau,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateVersement,
            @RequestParam(required = false) String notes,
            @RequestParam("fichier") MultipartFile fichier,
            Authentication auth) {

        VersementResponse v = service.enregistrer(
                caisseId, banqueId, journalId, montant, numeroBordereau,
                dateVersement, notes, fichier, auth.getName());
        return ResponseEntity.ok(v);
    }

    // ==================================================================
    //  LECTURES
    // ==================================================================

    @GetMapping("/caisse/{caisseId}")
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Versements d'une caisse, filtrable par dates")
    public Page<VersementResponse> listerParCaisse(
            @PathVariable Long caisseId,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            Pageable pageable) {
        return service.listerParCaisseEtPeriode(caisseId, dateDebut, dateFin, pageable);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR','ADMIN')")
    @Operation(summary = "Tous les versements (vue admin/superviseur, paginee)")
    public Page<VersementResponse> listerTous(Pageable pageable) {
        return service.listerTous(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Detail d'un versement (sans le contenu du bordereau)")
    public VersementResponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    // Telechargement binaire classique. Bloque par Edge Tracking
    // Prevention sur les domaines DuckDNS pour certaines combinaisons
    // d'URL ; on garde les alias pour les contextes ou ca marche
    // (intranet, navigateurs sans protection agressive).
    @GetMapping({"/{id}/pdf", "/{id}/bordereau", "/{id}/fichier"})
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Telecharge le bordereau bancaire (PDF ou image)")
    public ResponseEntity<byte[]> telechargerBordereau(@PathVariable Long id,
                                                       Authentication auth) {
        Versement v = service.telechargerFichier(id, auth.getName());
        String nomEncode = URLEncoder.encode(
                v.getNomFichier(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, v.getTypeMime())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + nomEncode)
                .body(v.getFichierBordereau());
    }

    /**
     * Variante JSON+base64 du bordereau : utilisee par defaut depuis le
     * frontend web pour contourner Edge Tracking Prevention qui bloque
     * les telechargements binaires sur DuckDNS. Le client reconstitue le
     * Blob a partir du base64 et declenche le telechargement localement.
     *
     * <p>Pas de surcout reseau notable : base64 augmente de ~33%, mais
     * un bordereau scanne fait typiquement 500 Ko -&gt; 670 Ko en base64,
     * negligeable sur un reseau interne.</p>
     */
    @GetMapping("/{id}/donnees")
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Recupere le bordereau en base64 dans un JSON "
            + "(fallback navigateurs avec Tracking Prevention agressif)")
    public java.util.Map<String, Object> telechargerBase64(@PathVariable Long id,
                                                            Authentication auth) {
        Versement v = service.telechargerFichier(id, auth.getName());
        return java.util.Map.of(
                "nomFichier",    v.getNomFichier(),
                "typeMime",      v.getTypeMime(),
                "tailleFichier", v.getTailleFichier(),
                "contenuBase64", java.util.Base64.getEncoder()
                        .encodeToString(v.getFichierBordereau())
        );
    }

    // ==================================================================
    //  SUPPRESSION
    // ==================================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Supprime un versement (admin sans restriction, auteur "
            + "sinon, journal non cloture).")
    public ResponseEntity<Void> supprimer(@PathVariable Long id, Authentication auth) {
        service.supprimer(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
