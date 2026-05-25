import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
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
        MatTooltipModule,
        MatPaginatorModule
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
    readonly totalElements = signal(0);
    readonly chargement = signal(false);
    readonly recherche$ = new Subject<string>();

    terme = '';
    pageIndex = 0;
    pageSize  = 20;

    readonly colonnes = ['code', 'libelle', 'pays', 'codeEtablissement', 'siteInternet', 'actif', 'actions'];

    ngOnInit(): void {
        this.charger();
        // Recherche : reset a la page 0 + debounce 300ms
        this.recherche$
          .pipe(
            debounceTime(300),
            distinctUntilChanged(),
            switchMap((q) => {
              this.pageIndex = 0;
              return this.service.listerPaginee({
                q: q || undefined,
                uniquementActives: false,
                page: 0,
                size: this.pageSize
              });
            })
          )
          .subscribe((page) => {
            this.banques.set(page.content);
            this.totalElements.set(page.totalElements);
          });
    }

    charger(): void {
        this.chargement.set(true);
        this.service.listerPaginee({
            q: this.terme || undefined,
            uniquementActives: false,
            page: this.pageIndex,
            size: this.pageSize
        }).subscribe({
            next: (page) => {
                this.banques.set(page.content);
                this.totalElements.set(page.totalElements);
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

    changerPage(event: PageEvent): void {
        this.pageIndex = event.pageIndex;
        this.pageSize  = event.pageSize;
        this.charger();
    }

    /**
     * Filtre desormais delegue au backend (via le terme recherche).
     * La methode reste exposee pour le template qui itere sur banquesFiltrees().
     * Avec la pagination, on rend simplement la page courante (deja filtree
     * par le backend).
     */
    banquesFiltrees(): Banque[] {
        return this.banques();
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
