import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { Banque } from '../../core/models/models';
import { BanqueService } from '../../core/services/admin.services';
import { BanqueDialogComponent } from './dialogs/banque-dialog.component';

@Component({
    selector: 'rts-banques',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        MatTableModule,
        MatButtonModule,
        MatIconModule,
        MatMenuModule,
        MatFormFieldModule,
        MatInputModule,
        MatTooltipModule
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './banques.component.html',
    styleUrls: ['./banques.component.css']
})
export class BanquesComponent implements OnInit {
    private readonly service = inject(BanqueService);
    private readonly dialog = inject(MatDialog);
    private readonly snackBar = inject(MatSnackBar);

    readonly banques = signal<Banque[]>([]);
    readonly filtre = signal('');
    readonly chargement = signal(false);

    readonly colonnes = ['code', 'libelle', 'pays', 'codeEtablissement', 'siteInternet', 'actif', 'actions'];

    ngOnInit(): void {
        this.charger();
    }

    charger(): void {
        this.chargement.set(true);
        this.service.lister(false).subscribe({
            next: (data) => {
                this.banques.set(data);
                this.chargement.set(false);
            },
            error: () => {
                this.snackBar.open('Erreur de chargement des banques', 'OK', {
                    duration: 3500, panelClass: ['snackbar-error']
                });
                this.chargement.set(false);
            }
        });
    }

    banquesFiltrees(): Banque[] {
        const q = this.filtre().toLowerCase().trim();
        if (!q) return this.banques();
        return this.banques().filter(b =>
            b.code.toLowerCase().includes(q) ||
            b.libelle.toLowerCase().includes(q) ||
            (b.codeEtablissement ?? '').toLowerCase().includes(q) ||
            (b.pays ?? '').toLowerCase().includes(q)
        );
    }

    ouvrirDialog(banque?: Banque): void {
        const ref = this.dialog.open(BanqueDialogComponent, {
            data: banque ?? null,
            width: '520px',
            autoFocus: 'first-tabbable',
            disableClose: false
        });
        ref.afterClosed().subscribe(result => {
            if (result) this.charger();
        });
    }

    basculer(b: Banque): void {
        if (!b.id) return;
        this.service.basculerActif(b.id).subscribe(() => {
            this.snackBar.open(
                b.actif ? 'Banque désactivée' : 'Banque activée',
                'OK',
                { duration: 2500, panelClass: ['snackbar-info'] }
            );
            this.charger();
        });
    }

    supprimer(b: Banque): void {
        if (!b.id) return;
        if (!confirm(`Désactiver la banque "${b.libelle}" (${b.code}) ?`)) return;
        this.service.supprimer(b.id).subscribe(() => {
            this.snackBar.open('Banque désactivée', 'OK', {
                duration: 2500, panelClass: ['snackbar-info']
            });
            this.charger();
        });
    }
}