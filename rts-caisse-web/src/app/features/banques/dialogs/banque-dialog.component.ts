import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Banque } from '../../../core/models/models';
import { BanqueService } from '../../../core/services/admin.services';

@Component({
    selector: 'rts-banque-dialog',
    standalone: true,
    imports: [
        CommonModule,
        ReactiveFormsModule,
        MatDialogModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatIconModule,
        MatSlideToggleModule
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './banque-dialog.component.html',
    styleUrls: ['./banque-dialog.component.css']
})
export class BanqueDialogComponent {
    readonly dialogRef = inject(MatDialogRef<BanqueDialogComponent>);
    readonly data = inject<Banque | null>(MAT_DIALOG_DATA, { optional: true });
    private readonly fb = inject(FormBuilder);
    private readonly service = inject(BanqueService);
    private readonly snackBar = inject(MatSnackBar);

    readonly form = this.fb.nonNullable.group({
        code: [
            this.data?.code ?? '',
            [Validators.required, Validators.maxLength(10), Validators.pattern(/^[A-Z0-9]+$/)]
        ],
        libelle: [this.data?.libelle ?? '', [Validators.required, Validators.maxLength(200)]],
        pays: [this.data?.pays ?? 'SÉNÉGAL', [Validators.required, Validators.maxLength(80)]],
        codeEtablissement: [this.data?.codeEtablissement ?? '', Validators.maxLength(20)],
        siteInternet: [this.data?.siteInternet ?? '', Validators.maxLength(255)],
        actif: [this.data?.actif ?? true]
    });

    valider(): void {
        if (this.form.invalid) {
            this.form.markAllAsTouched();
            return;
        }
        const dto = this.form.getRawValue() as Banque;
        const obs = this.data?.id
            ? this.service.modifier(this.data.id, dto)
            : this.service.creer(dto);

        obs.subscribe({
            next: (b) => {
                this.snackBar.open(
                    this.data ? 'Banque modifiée' : 'Banque créée',
                    'OK',
                    { duration: 2500, panelClass: ['snackbar-success'] }
                );
                this.dialogRef.close(b);
            },
            error: (err) => {
                this.snackBar.open(
                    err?.error?.message ?? 'Erreur lors de l\'enregistrement',
                    'OK',
                    { duration: 4000, panelClass: ['snackbar-error'] }
                );
            }
        });
    }

    annuler(): void {
        this.dialogRef.close();
    }
}