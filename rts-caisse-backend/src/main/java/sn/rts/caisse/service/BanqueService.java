package sn.rts.caisse.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.rts.caisse.dto.BanqueDTO;
import sn.rts.caisse.exception.BusinessException;
import sn.rts.caisse.exception.ResourceNotFoundException;
import sn.rts.caisse.model.Banque;
import sn.rts.caisse.repository.BanqueRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BanqueService {

    private final BanqueRepository repository;

    public BanqueDTO creer(BanqueDTO dto) {
        String code = dto.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw new BusinessException("Code banque déjà utilisé : " + code);
        }
        Banque b = Banque.builder()
                .code(code)
                .libelle(dto.libelle().trim())
                .pays(dto.pays() == null || dto.pays().isBlank() ? "SÉNÉGAL" : dto.pays().trim())
                .codeEtablissement(dto.codeEtablissement() == null ? null : dto.codeEtablissement().trim())
                .siteInternet(dto.siteInternet() == null ? null : dto.siteInternet().trim())
                .actif(dto.actif() == null ? Boolean.TRUE : dto.actif())
                .build();
        return BanqueDTO.from(repository.save(b));
    }

    public BanqueDTO modifier(Long id, BanqueDTO dto) {
        Banque b = trouver(id);
        String code = dto.code().trim().toUpperCase();
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("Code banque déjà utilisé : " + code);
        }
        b.setCode(code);
        b.setLibelle(dto.libelle().trim());
        b.setPays(dto.pays() == null || dto.pays().isBlank() ? "SÉNÉGAL" : dto.pays().trim());
        b.setCodeEtablissement(dto.codeEtablissement() == null ? null : dto.codeEtablissement().trim());
        b.setSiteInternet(dto.siteInternet() == null ? null : dto.siteInternet().trim());
        if (dto.actif() != null) b.setActif(dto.actif());
        return BanqueDTO.from(repository.save(b));
    }

    public BanqueDTO basculerActif(Long id) {
        Banque b = trouver(id);
        b.setActif(!Boolean.TRUE.equals(b.getActif()));
        return BanqueDTO.from(repository.save(b));
    }

    public void supprimer(Long id) {
        Banque b = trouver(id);
        // Suppression logique uniquement : on désactive
        b.setActif(false);
        repository.save(b);
    }

    @Transactional(readOnly = true)
    public List<BanqueDTO> lister(boolean uniquementActives) {
        List<Banque> items = uniquementActives
                ? repository.findAllByActifTrueOrderByLibelleAsc()
                : repository.findAllByOrderByCodeAsc();
        return items.stream().map(BanqueDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public BanqueDTO obtenir(Long id) {
        return BanqueDTO.from(trouver(id));
    }

    private Banque trouver(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Banque", id));
    }
}