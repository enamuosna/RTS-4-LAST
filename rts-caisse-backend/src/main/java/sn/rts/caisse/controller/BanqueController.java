package sn.rts.caisse.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import sn.rts.caisse.dto.BanqueDTO;
import sn.rts.caisse.service.BanqueService;

import java.util.List;

@RestController
@RequestMapping("/api/banques")
@RequiredArgsConstructor
public class BanqueController {

    private final BanqueService service;

    /** Liste accessible à tous les utilisateurs authentifiés (pour les sélecteurs). */
    @GetMapping
    public ResponseEntity<List<BanqueDTO>> lister(
            @RequestParam(name = "actives", defaultValue = "false") boolean uniquementActives) {
        return ResponseEntity.ok(service.lister(uniquementActives));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BanqueDTO> obtenir(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenir(id));
    }

    /** Mutations réservées aux ADMIN. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BanqueDTO> creer(@Valid @RequestBody BanqueDTO dto) {
        return ResponseEntity.ok(service.creer(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BanqueDTO> modifier(@PathVariable Long id, @Valid @RequestBody BanqueDTO dto) {
        return ResponseEntity.ok(service.modifier(id, dto));
    }

    @PatchMapping("/{id}/toggle-actif")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BanqueDTO> basculer(@PathVariable Long id) {
        return ResponseEntity.ok(service.basculerActif(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}