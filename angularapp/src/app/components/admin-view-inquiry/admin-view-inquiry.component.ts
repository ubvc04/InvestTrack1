import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { InvestmentInquiry } from 'src/app/models/investment-inquiry.model';
import { InvestmentInquiryService } from 'src/app/services/investment-inquiry.service';

@Component({
  selector: 'app-admin-view-inquiry',
  templateUrl: './admin-view-inquiry.component.html',
  styleUrls: ['./admin-view-inquiry.component.css']
})
export class AdminViewInquiryComponent {

  inquiries: InvestmentInquiry[] = [];
  filtered: InvestmentInquiry[] = [];

  searchName = '';
  filterPriority = '';
  filterStatus = '';

  showDeleteModal = false;
  deleteId: number | null = null;

  showResponseModal = false;
  selectedInquiry: InvestmentInquiry | null = null;
  adminResponseText = '';
  newStatus = '';

  constructor(
    private inquiryService: InvestmentInquiryService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadInquiries();
  }

  loadInquiries(): void {
    this.inquiryService.getAllInquiries().subscribe({
      next: (data) => {
        this.inquiries = data
          .sort((a, b) =>
            new Date(b.inquiryDate).getTime() -
            new Date(a.inquiryDate).getTime()
          );
  
        this.filtered = [...this.inquiries];
      },
      error: () => this.router.navigate(['/error'])
    });
  }

  applyFilter(): void {
    this.filtered = this.inquiries.filter(inq => {

      const nameMatch =
        !this.searchName ||
        (inq.subject || inq.investment?.name)
          ?.toLowerCase()
          .includes(this.searchName.toLowerCase());

      const priorityMatch =
        !this.filterPriority ||
        inq.priority === this.filterPriority;

      const statusMatch =
        !this.filterStatus ||
        inq.status === this.filterStatus;

      return nameMatch && priorityMatch && statusMatch;
    });
  }

  openResponseModal(inq: InvestmentInquiry): void {
    this.selectedInquiry = inq;
    this.adminResponseText = inq.adminResponse || '';
    this.newStatus = inq.status || 'PENDING';
    this.showResponseModal = true;
  }

  submitResponse(): void {
    if (!this.selectedInquiry?.inquiryId) return;

    const updated: InvestmentInquiry = {
      ...this.selectedInquiry,
      adminResponse: this.adminResponseText,
      status: this.newStatus
    };

    this.inquiryService.updateInquiry(
      this.selectedInquiry.inquiryId,
      updated
    ).subscribe({
      next: () => {
        this.showResponseModal = false;
        this.loadInquiries();
      },
      error: () => {
        this.showResponseModal = false;
      }
    });
  }

  onStatusChange(inq: InvestmentInquiry, newStatus: string): void {
    if (!inq.inquiryId) return;

    this.inquiryService.updateInquiry(
      inq.inquiryId,
      {
        ...inq,
        status: newStatus
      }
    ).subscribe({
      next: () => this.loadInquiries()
    });
  }

  confirmDelete(id: number): void {
    this.deleteId = id;
    this.showDeleteModal = true;
  }

  onDelete(): void {

    if (this.deleteId === null) return;

    const inquiry = this.inquiries.find(
      inq => inq.inquiryId === this.deleteId
    );

    if (!inquiry) return;

    this.inquiryService.deleteInquiry(this.deleteId).subscribe({
      next: () => {
        this.showDeleteModal = false;
        this.deleteId = null;
        this.loadInquiries();
      },
      error: () => {
        this.showDeleteModal = false;
      }
    });
  }

  cancelDelete(): void {
    this.showDeleteModal = false;
    this.deleteId = null;
  }

  cancelResponse(): void {
    this.showResponseModal = false;
  }

  getPriorityClass(priority: string): string {
    return priority === 'High'
      ? 'bg-danger'
      : priority === 'Medium'
      ? 'bg-warning text-dark'
      : 'bg-success';
  }

  getStatusClass(status: string): string {
    return status === 'RESOLVED'
      ? 'bg-success'
      : status === 'PENDING'
      ? 'bg-warning text-dark'
      : 'bg-secondary';
  }
}