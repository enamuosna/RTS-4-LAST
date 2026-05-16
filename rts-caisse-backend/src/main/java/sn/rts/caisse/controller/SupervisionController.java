package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.rts.caisse.dto.SupervisionSnapshotResponse;
import sn.rts.caisse.service.SupervisionService;

/**
 * Endpoint « vue d'ensemble temps réel » pour le Responsable des caisses RTS.
 *
 * <p>Réservé aux rôles ADMIN et SUPERVISEUR. La page Angular polle
 * {@code GET /api/supervision/snapshot} toutes les 10 secondes pour rafraîchir
 * son affichage (état des caisses + flux d'activité).</p>
 */
@RestController
@RequestMapping("/api/supervision")
@RequiredArgsConstructor
@Tag(name = "Supervision", description = "Vue d'ensemble temps réel (ADMIN / SUPERVISEUR)")
public class SupervisionController {

    private final SupervisionService service;

    @GetMapping("/snapshot")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISEUR', 'AGENT_RECETTE')")
    @Operation(summary = "Cliché temps réel : caisses + agrégats du jour + activité récente",
               description = "À appeler en polling régulier (10s) côté UI. "
                       + "Ouvert aux ADMIN, SUPERVISEUR et AGENT_RECETTE.")
    public ResponseEntity<SupervisionSnapshotResponse> snapshot() {
        return ResponseEntity.ok(service.snapshot());
    }
}
