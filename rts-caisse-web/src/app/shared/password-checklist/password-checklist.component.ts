import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import {
  PASSWORD_MIN_LENGTH,
  evaluerCriteres,
  tousLesCriteresOk
} from '../../core/validators/password-policy.validator';

/**
 * Petite checklist visuelle des criteres de mot de passe respectes /
 * manquants. A poser sous un champ mdp pour aider l'utilisateur a
 * comprendre quoi corriger.
 *
 * Usage :
 *   <rts-password-checklist [motDePasse]="form.controls.motDePasse.value" />
 */
@Component({
  selector: 'rts-password-checklist',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="checklist" [class.tout-ok]="tousOk()">
      <div class="header">
        @if (tousOk()) {
          <mat-icon class="header-icon ok">check_circle</mat-icon>
          <span>Mot de passe conforme</span>
        } @else {
          <mat-icon class="header-icon">info</mat-icon>
          <span>Le mot de passe doit contenir :</span>
        }
      </div>
      <ul>
        <li [class.ok]="criteres().longueurOk">
          <mat-icon>{{ criteres().longueurOk ? 'check' : 'close' }}</mat-icon>
          au moins {{ minLength }} caracteres
        </li>
        <li [class.ok]="criteres().minusculeOk">
          <mat-icon>{{ criteres().minusculeOk ? 'check' : 'close' }}</mat-icon>
          au moins 1 minuscule (a-z)
        </li>
        <li [class.ok]="criteres().majusculeOk">
          <mat-icon>{{ criteres().majusculeOk ? 'check' : 'close' }}</mat-icon>
          au moins 1 majuscule (A-Z)
        </li>
        <li [class.ok]="criteres().chiffreOk">
          <mat-icon>{{ criteres().chiffreOk ? 'check' : 'close' }}</mat-icon>
          au moins 1 chiffre (0-9)
        </li>
        <li [class.ok]="criteres().specialOk">
          <mat-icon>{{ criteres().specialOk ? 'check' : 'close' }}</mat-icon>
          au moins 1 caractere special (!&#64;#$%^&*...)
        </li>
        <li [class.ok]="criteres().sansEspaceOk">
          <mat-icon>{{ criteres().sansEspaceOk ? 'check' : 'close' }}</mat-icon>
          pas d'espace
        </li>
      </ul>
    </div>
  `,
  styles: [`
    .checklist {
      background: #fafafa;
      border: 1px solid #e0e0e0;
      border-radius: 8px;
      padding: 10px 14px;
      font-size: 12px;
      color: #555;
      margin-top: 4px;
      transition: background 0.18s, border-color 0.18s;
    }
    .checklist.tout-ok {
      background: #f0f9f4;
      border-color: #b7e3c4;
    }
    .header {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      margin-bottom: 6px;
      color: rgba(0, 0, 0, 0.65);
    }
    .header-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: #999;
    }
    .header-icon.ok {
      color: #2e7d32;
    }
    ul {
      list-style: none;
      padding: 0;
      margin: 0;
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 2px 12px;
    }
    li {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #c62828;
    }
    li.ok {
      color: #2e7d32;
    }
    li mat-icon {
      font-size: 14px;
      width: 14px;
      height: 14px;
      flex-shrink: 0;
    }
  `]
})
export class PasswordChecklistComponent {
  @Input({ required: true }) set motDePasse(value: string | null | undefined) {
    this._motDePasse.set(value ?? '');
  }
  get motDePasse(): string {
    return this._motDePasse();
  }

  protected readonly minLength = PASSWORD_MIN_LENGTH;
  private readonly _motDePasse = signal<string>('');

  protected readonly criteres = computed(() => evaluerCriteres(this._motDePasse()));
  protected readonly tousOk = computed(() => tousLesCriteresOk(this.criteres()));
}
