import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Politique de mot de passe RTS — duplique exactement la regle backend
 * (PasswordPolicyService.java).
 *
 * Permet d'afficher au front une checklist en temps reel des criteres
 * respectes / manquants, pour que l'utilisateur sache quoi corriger.
 * Le backend reste l'autorite finale : on refait toujours la validation
 * cote serveur.
 */
export const PASSWORD_MIN_LENGTH = 12;

/** Etat de chacun des criteres pour affichage UI. */
export interface PasswordCriteria {
  longueurOk: boolean;
  minusculeOk: boolean;
  majusculeOk: boolean;
  chiffreOk: boolean;
  specialOk: boolean;
  sansEspaceOk: boolean;
}

/** Verifie chaque critere individuellement. */
export function evaluerCriteres(motDePasse: string | null | undefined): PasswordCriteria {
  const v = motDePasse ?? '';
  return {
    longueurOk:   v.length >= PASSWORD_MIN_LENGTH,
    minusculeOk:  /[a-z]/.test(v),
    majusculeOk:  /[A-Z]/.test(v),
    chiffreOk:    /[0-9]/.test(v),
    specialOk:    /[!@#$%^&*()_+\-=\[\]{};':",.<>/?\\|`~]/.test(v),
    sansEspaceOk: v.length > 0 && !/\s/.test(v)
  };
}

/** Tous les criteres sont-ils respectes ? */
export function tousLesCriteresOk(c: PasswordCriteria): boolean {
  return c.longueurOk && c.minusculeOk && c.majusculeOk
      && c.chiffreOk && c.specialOk && c.sansEspaceOk;
}

/**
 * Validator Angular Reactive Forms a poser sur le champ mot de passe.
 * Si non conforme, renvoie {@code { passwordPolicy: { criteres: ... } }}
 * que le template peut consommer pour afficher la checklist.
 */
export function passwordPolicyValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const criteres = evaluerCriteres(control.value);
    if (tousLesCriteresOk(criteres)) return null;
    return { passwordPolicy: { criteres } };
  };
}
