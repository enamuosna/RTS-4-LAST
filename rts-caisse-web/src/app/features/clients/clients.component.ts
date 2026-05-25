import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { Client } from '../../core/models/models';
import { ClientService } from '../../core/services/admin.services';
import { ClientDialogComponent } from './dialogs/client-dialog.component';

@Component({
  selector: 'rts-clients',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './clients.component.html',
  styleUrls: ['./clients.component.css']
})
export class ClientsComponent implements OnInit {
  private readonly service = inject(ClientService);
  private readonly dialog = inject(MatDialog);

  readonly clients = signal<Client[]>([]);
  readonly totalElements = signal(0);
  readonly colonnes = ['raisonSociale', 'ninea', 'telephone', 'email', 'actif', 'actions'];
  readonly recherche$ = new Subject<string>();

  terme = '';
  pageIndex = 0;
  pageSize = 20;

  ngOnInit(): void {
    this.charger();
    this.recherche$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => {
          this.pageIndex = 0;
          return this.service.listerPaginee({
            q: q || undefined,
            page: 0,
            size: this.pageSize
          });
        })
      )
      .subscribe((page) => {
        this.clients.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }

  charger(): void {
    this.service.listerPaginee({
      q: this.terme || undefined,
      page: this.pageIndex,
      size: this.pageSize
    }).subscribe((page) => {
      this.clients.set(page.content);
      this.totalElements.set(page.totalElements);
    });
  }

  changerPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.charger();
  }

  creer(): void {
    this.dialog
      .open(ClientDialogComponent, { width: '620px' })
      .afterClosed()
      .subscribe((c) => {
        if (c) this.charger();
      });
  }

  modifier(c: Client): void {
    this.dialog
      .open(ClientDialogComponent, { width: '620px', data: c })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) this.charger();
      });
  }
}
