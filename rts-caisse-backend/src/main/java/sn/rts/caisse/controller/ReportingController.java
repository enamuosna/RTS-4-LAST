package sn.rts.caisse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.DashboardResponse;
import sn.rts.caisse.service.ReportingService;

import java.time.LocalDate;

/**
 * Reporting et tableaux de bord pour la supervision et la direction.
 *
 * <p>Accessible aux trois rôles avec filtrage automatique :
 * <ul>
 *   <li><b>ADMIN / SUPERVISEUR</b> : vue globale, toutes les caisses et tous
 *       les caissiers.</li>
 *   <li><b>CAISSIER</b> : vue restreinte à ses propres opérations
 *       (filtrage par {@code caissier.id} dans le service).</li>
 * </ul>
 *
 * <p>La plage de dates est facultative : si ni {@code dateDebut} ni
 * {@code dateFin} ne sont fournis, le rapport porte sur la journée courante.
 * Si une seule des deux est fournie, l'autre prend la même valeur (rapport
 * sur une journée précise). Si les deux sont fournies, le rapport agrège
 * toute la période [dateDebut, dateFin] inclus.
 */
@RestController
@RequestMapping("/api/reporting")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Dashboards et agrégats (ADMIN / SUPERVISEUR / CAISSIER)")
public class ReportingController {

    private final ReportingService reportingService;

    @GetMapping("/dashboard")
    @Operation(summary = "Tableau de bord sur une plage de dates (jour courant par défaut)",
               description = "Filtre optionnel par caisseId pour la page Supervision détaillée.")
    public ResponseEntity<DashboardResponse> dashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) Long caisseId,
            Authentication authentication) {
        return ResponseEntity.ok(
                reportingService.dashboard(dateDebut, dateFin,
                        authentication.getName(), caisseId));
    }
}
