package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.ConsolidationRecetteResponse;
import sn.rts.caisse.dto.VentilationRecetteResponse;
import sn.rts.caisse.service.RecetteHebdoPdfService;
import sn.rts.caisse.service.RecetteHebdoService;

import java.time.LocalDate;

/**
 * Rubrique « Recettes » : ventilation hebdomadaire des recettes par caisse,
 * avec double validation (Contrôle 1 — Chef Unité Finances, Contrôle 2 — Chef
 * de Département) et export PDF reproduisant la fiche papier RTS.
 */
@RestController
@RequestMapping("/api/recettes")
@RequiredArgsConstructor
@Tag(name = "Recettes", description = "Ventilation hebdomadaire des recettes et double validation")
public class RecetteController {

    private final RecetteHebdoService service;
    private final RecetteHebdoPdfService pdfService;

    @GetMapping("/ventilation")
    @Operation(summary = "Ventilation des recettes d'une caisse sur une période (DU/AU)",
               description = "Période libre ; par défaut la semaine courante (lundi→dimanche). "
                       + "Crée le brouillon si la période n'a pas encore été consultée.")
    public ResponseEntity<VentilationRecetteResponse> ventilation(
            @RequestParam Long caisseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.ventilation(caisseId, dateDebut, dateFin));
    }

    @PostMapping("/{id}/controle1")
    @PreAuthorize("hasRole('CHEF_UNITE_FINANCES')")
    @Operation(summary = "Contrôle 1 — signature du Chef Unité Finances")
    public ResponseEntity<VentilationRecetteResponse> controle1(@PathVariable Long id,
                                                                Authentication authentication) {
        return ResponseEntity.ok(service.controle1(id, authentication.getName()));
    }

    @PostMapping("/{id}/controle2")
    @PreAuthorize("hasRole('CHEF_DEPARTEMENT')")
    @Operation(summary = "Contrôle 2 — signature du Chef de Département (validation finale)")
    public ResponseEntity<VentilationRecetteResponse> controle2(@PathVariable Long id,
                                                                Authentication authentication) {
        return ResponseEntity.ok(service.controle2(id, authentication.getName()));
    }

    @GetMapping("/consolidation")
    @Operation(summary = "Consolidation des recettes de toutes les caisses sur une période",
               description = "Ventilation globale par produit + répartition par caisse. Lecture seule.")
    public ResponseEntity<ConsolidationRecetteResponse> consolidation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.consolidation(dateDebut, dateFin));
    }

    @GetMapping("/page")
    @Operation(summary = "Historique paginé des ventilations (filtre caisse optionnel)")
    public ResponseEntity<Page<VentilationRecetteResponse>> historique(
            @RequestParam(required = false) Long caisseId,
            Pageable pageable) {
        return ResponseEntity.ok(service.historique(caisseId, pageable));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "PDF de la ventilation (fiche officielle)")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        VentilationRecetteResponse v = service.obtenirParId(id);
        byte[] pdf = pdfService.genererPdf(v);

        String nom = "ventilation-" + v.caisseCode() + "-"
                + v.dateDebut() + "_" + v.dateFin() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(nom).build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
