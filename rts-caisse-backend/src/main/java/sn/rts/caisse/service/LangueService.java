package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.LangueDto;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Langue;
import sn.rts.caisse.repository.LangueRepository;

import java.util.List;

/** Gestion du référentiel des langues de diffusion (ADMIN). */
@Service
@RequiredArgsConstructor
@Transactional
public class LangueService {

    private final LangueRepository repository;

    @Transactional(readOnly = true)
    public List<LangueDto> lister(boolean activesSeulement) {
        List<Langue> langues = activesSeulement
                ? repository.findByActifTrueOrderByLibelleAsc()
                : repository.findAllByOrderByLibelleAsc();
        return langues.stream().map(LangueDto::from).toList();
    }

    public LangueDto creer(LangueDto dto) {
        if (repository.existsByCodeIgnoreCase(dto.code())) {
            throw new BusinessException("Une langue avec le code « " + dto.code() + " » existe déjà.");
        }
        Langue l = Langue.builder()
                .code(dto.code().trim())
                .libelle(dto.libelle().trim())
                .actif(dto.actif())
                .build();
        return LangueDto.from(repository.save(l));
    }

    public LangueDto modifier(Long id, LangueDto dto) {
        Langue l = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Langue", id));
        l.setLibelle(dto.libelle().trim());
        l.setActif(dto.actif());
        return LangueDto.from(repository.save(l));
    }

    public void supprimer(Long id) {
        if (!repository.existsById(id)) {
            throw ResourceNotFoundException.of("Langue", id);
        }
        repository.deleteById(id);
    }
}
