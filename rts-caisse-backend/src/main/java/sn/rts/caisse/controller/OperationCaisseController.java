package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.OperationCaisseRequest;
import sn.rts.caisse.dto.OperationCaisseResponse;
import sn.rts.caisse.dto.PurgeFilter;
import sn.rts.caisse.dto.PurgePreviewResponse;
import sn.rts.caisse.dto.PurgeResult;
import sn.rts.caisse.service.OperationCaisseService;
import sn.rts.caisse.service.OperationPurgeService;

import jakarta.validation.Valid;
import sn.rts.caisse.dto.EnvoiWhatsAppRequest;
import sn.rts.caisse.dto.EnvoiWhatsAppResponse;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Opérations de caisse", description = "Encaissements et décaissements")
public class OperationCaisseController {

    private final OperationCaisseService service;
    private final OperationPurgeService  purgeService;
    private final sn.rts.caisse.service.RecuPdfService recuPdfService;

    @PostMapping
    @Operation(summary = "Enregistrer une nouvelle opération (encaissement / décaissement)")
    public ResponseEntity<OperationCaisseResponse> enregistrer(
            @Valid @RequestBody OperationCaisseRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.enregistrer(request, authentication.getName()));
    }

    @PatchMapping("/{id}/annuler")
    @Operation(summary = "Annuler une opération (contre-passation, pas de suppression)")
    public ResponseEntity<OperationCaisseResponse> annuler(@PathVariable Long id,
                                                           @RequestParam String motif,
                                                           Authentication authentication) {
        return ResponseEntity.ok(service.annuler(id, motif, authentication.getName()));
    }

    @PatchMapping("/{id}/reactiver")
    @Operation(summary = "Réactiver une opération annulée par erreur",
               description = "Défait la contre-passation : l'opération repasse à "
                       + "annulee=false et le solde est ré-ajusté. Réservé aux ADMIN, "
                       + "SUPERVISEUR et AGENT_RECETTE de la caisse concernée.")
    public ResponseEntity<OperationCaisseResponse> reactiver(@PathVariable Long id,
                                                              Authentication authentication) {
        return ResponseEntity.ok(service.reactiver(id, authentication.getName()));
    }

    @GetMapping(value = "/{id}/pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Renvoie le reçu PDF d'une opération",
               description = "Alias neutre de GET /api/recus/operation/{id} pour eviter les "
                       + "extensions anti-tracking qui bloquent les URL contenant le mot "
                       + "'recus' (uBlock / Brave Shields / Edge Tracking Prevention).")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] pdf = recuPdfService.genererRecu(id);
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        h.setContentDisposition(org.springframework.http.ContentDisposition
                .inline().filename("recu-" + id + ".pdf").build());
        h.setCacheControl("no-store, no-cache, must-revalidate");
        h.setContentLength(pdf.length);
        return org.springframework.http.ResponseEntity.ok().headers(h).body(pdf);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @Operation(summary = "Modifier une opération (correction d'erreur de saisie)",
               description = "Recalcule automatiquement le solde de la caisse. "
                       + "Refusé si l'opération est annulée ou si la journée est clôturée.")
    public ResponseEntity<OperationCaisseResponse> modifier(
            @PathVariable Long id,
            @Valid @RequestBody OperationCaisseRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                service.modifier(id, request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperationCaisseResponse> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @GetMapping("/caisse/{caisseId}")
    @Operation(summary = "Historique paginé des opérations pour une caisse",
               description = "Filtrage optionnel sur une plage [dateDebut, dateFin] "
                       + "au format ISO yyyy-MM-dd. Sans dates → toutes les opérations.")
    public ResponseEntity<Page<OperationCaisseResponse>> historique(
            @PathVariable Long caisseId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate dateDebut,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate dateFin,
            Pageable pageable) {
        return ResponseEntity.ok(
                service.historiqueParCaisse(caisseId, dateDebut, dateFin, pageable));
    }

    @GetMapping("/caisse/{caisseId}/jour")
    @Operation(summary = "Opérations de la journée en cours pour une caisse")
    public ResponseEntity<List<OperationCaisseResponse>> historiqueJour(@PathVariable Long caisseId) {
        return ResponseEntity.ok(service.historiqueDuJour(caisseId));
    }

    @GetMapping("/caisse/{caisseId}/session")
    @Operation(
            summary = "Opérations de la session de caisse en cours (journal non clôturé)",
            description = "Renvoie uniquement les opérations dont le journal de clôture "
                    + "n'a pas encore été rattaché (journal_id IS NULL). Utilisé par "
                    + "le guichet JavaFX : après clôture, la liste devient vide côté "
                    + "guichet ; les opérations restent consultables côté admin via "
                    + "l'historique paginé."
    )
    public ResponseEntity<List<OperationCaisseResponse>> historiqueSession(@PathVariable Long caisseId) {
        return ResponseEntity.ok(service.historiqueSessionCourante(caisseId));
    }

    // ==================================================================
    //  JUSTIFICATIF (PDF/JPG/PNG joint a une operation)
    // ==================================================================

    @PostMapping(value = "/{id}/justificatif",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Attache (ou remplace) le justificatif d'une operation "
            + "(PDF/JPG/PNG, max 5 Mo). La categorie doit avoir "
            + "accepteJustificatif=true.")
    public ResponseEntity<OperationCaisseResponse> uploaderJustificatif(
            @PathVariable Long id,
            @RequestParam("fichier") org.springframework.web.multipart.MultipartFile fichier,
            Authentication auth) {
        return ResponseEntity.ok(
                service.uploaderJustificatif(id, fichier, auth.getName()));
    }

    /**
     * Endpoint JSON+base64 pour le telechargement du justificatif.
     * Meme pattern que /api/versements/{id}/donnees : on encode le binaire
     * en base64 dans une reponse JSON pour contourner Edge Tracking
     * Prevention qui bloque les telechargements directs sur DuckDNS.
     */
    @GetMapping("/{id}/justificatif-donnees")
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Recupere le justificatif en base64 dans un JSON")
    public java.util.Map<String, Object> telechargerJustificatif(@PathVariable Long id) {
        sn.rts.caisse.model.OperationCaisse op = service.chargerJustificatif(id);
        return java.util.Map.of(
                "nomFichier",    op.getJustificatifNomFichier(),
                "typeMime",      op.getJustificatifTypeMime(),
                "tailleFichier", op.getJustificatifTailleFichier(),
                "contenuBase64", java.util.Base64.getEncoder()
                        .encodeToString(op.getJustificatifFichier())
        );
    }

    @DeleteMapping("/{id}/justificatif")
    @org.springframework.security.access.prepost.PreAuthorize(
            "hasAnyRole('CAISSIER','AGENT_RECETTE','SUPERVISEUR','ADMIN')")
    @Operation(summary = "Detache le justificatif d'une operation (sans annuler l'operation).")
    public ResponseEntity<Void> supprimerJustificatif(@PathVariable Long id,
                                                      Authentication auth) {
        service.supprimerJustificatif(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/whatsapp")
    @Operation(
            summary = "Envoie le reçu PDF d'une opération par WhatsApp",
            description = "Le PDF est généré côté serveur, uploadé chez Meta, puis envoyé "
                    + "via l'API WhatsApp Business Cloud. Aucune ouverture de WhatsApp "
                    + "Web ou Desktop côté caissier."
    )
    public ResponseEntity<EnvoiWhatsAppResponse> envoyerWhatsApp(
            @PathVariable Long id,
            @Valid @RequestBody EnvoiWhatsAppRequest request) {

        EnvoiWhatsAppResponse reponse = service.envoyerWhatsApp(id, request.telephone());
        return ResponseEntity.ok(reponse);
    }

    // ==================================================================
    //  PURGE (suppression définitive) - réservé aux ADMIN
    //
    //  Flux UX recommandé :
    //    1. POST /purger/preview  -> affiche compteur + sommes impactées
    //    2. POST /purger/csv      -> télécharge un snapshot CSV
    //    3. POST /purger          -> exécute la purge (réplique exacte du
    //                                filtre du preview)
    //  OU pour une suppression unitaire (bouton corbeille admin) :
    //    DELETE /{id}/definitif
    // ==================================================================

    @DeleteMapping("/{id}/definitif")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Supprime DÉFINITIVEMENT une opération (ADMIN uniquement)",
            description = "Si l'opération n'est pas annulée et que son journal n'est "
                    + "pas clôturé, le solde de la caisse est contre-passé "
                    + "automatiquement avant suppression. Un audit log "
                    + "SUPPRIMER_OPERATION_DEFINITIVEMENT est créé.")
    public ResponseEntity<PurgeResult> supprimerDefinitivement(@PathVariable Long id,
                                                                Authentication auth) {
        return ResponseEntity.ok(purgeService.supprimerUneOp(id, auth.getName()));
    }

    @PostMapping("/purger/preview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Prévisualise l'impact d'une purge (compteur, sommes, dates)")
    public ResponseEntity<PurgePreviewResponse> previewPurge(
            @Valid @RequestBody PurgeFilter filter) {
        return ResponseEntity.ok(purgeService.previewPurge(filter));
    }

    @PostMapping(value = "/purger/csv",
            produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exporte en CSV les opérations qui seraient supprimées par la purge")
    public ResponseEntity<byte[]> exporterPurgeCsv(@Valid @RequestBody PurgeFilter filter) {
        byte[] csv = purgeService.exportCsv(filter);
        String nom = "operations-a-purger-"
                + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @PostMapping("/purger")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exécute la purge en masse selon le filtre",
            description = "Limite de sécurité : "
                    + OperationPurgeService.LIMITE_PURGE_BULK
                    + " opérations max. Chaque suppression est dans sa propre "
                    + "transaction (un échec sur une op n'annule pas les autres).")
    public ResponseEntity<PurgeResult> purgerEnMasse(@Valid @RequestBody PurgeFilter filter,
                                                      Authentication auth) {
        return ResponseEntity.ok(
                purgeService.purgerEnMasse(filter, auth.getName()));
    }
}
