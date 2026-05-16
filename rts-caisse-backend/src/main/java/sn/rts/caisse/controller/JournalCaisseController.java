package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.ClotureCaisseRequest;
import sn.rts.caisse.dto.JournalCaisseResponse;
import sn.rts.caisse.dto.OuvertureCaisseRequest;
import sn.rts.caisse.service.JournalCaisseService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/journaux")
@RequiredArgsConstructor
@Tag(name = "Journal de caisse", description = "Ouverture, clôture et validation journalières")
public class JournalCaisseController {

    private final JournalCaisseService service;

    @PostMapping("/caisse/{caisseId}/ouvrir")
    @Operation(summary = "Ouvrir la caisse pour la journée (crée un journal)")
    public ResponseEntity<JournalCaisseResponse> ouvrir(@PathVariable Long caisseId,
                                                        @Valid @RequestBody OuvertureCaisseRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(service.ouvrir(caisseId, request, authentication.getName()));
    }

    @PostMapping("/caisse/{caisseId}/cloturer")
    @Operation(summary = "Clôturer la caisse : fige les totaux et calcule l'écart")
    public ResponseEntity<JournalCaisseResponse> cloturer(@PathVariable Long caisseId,
                                                          @Valid @RequestBody ClotureCaisseRequest request,
                                                          Authentication authentication) {
        return ResponseEntity.ok(service.cloturer(caisseId, request, authentication.getName()));
    }

    @PostMapping("/{journalId}/valider")
    @Operation(summary = "Validation par un superviseur (ADMIN/SUPERVISEUR)")
    public ResponseEntity<JournalCaisseResponse> valider(@PathVariable Long journalId,
                                                         Authentication authentication) {
        return ResponseEntity.ok(service.valider(journalId, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalCaisseResponse> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    @GetMapping("/caisse/{caisseId}")
    public ResponseEntity<List<JournalCaisseResponse>> parCaisse(
            @PathVariable Long caisseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.journauxParCaisse(caisseId, dateDebut, dateFin));
    }

    @GetMapping("/jour")
    public ResponseEntity<List<JournalCaisseResponse>> duJour(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.journauxDuJour(date));
    }

    /**
     * Recherche unifiee des journaux : plage de dates + filtre optionnel
     * sur une caisse. Utilise par la page Journaux pour offrir un filtre
     * complet (selecteur de caisse + 2 dates).
     */
    @GetMapping
    public ResponseEntity<List<JournalCaisseResponse>> lister(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) Long caisseId) {
        return ResponseEntity.ok(service.journaux(dateDebut, dateFin, caisseId));
    }
}
