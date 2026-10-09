export interface Notification {
  notificationId: number;
  type: 'INQUIRY' | 'FEEDBACK' | 'GENERAL';
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
  relatedInquiryId?: number | null;
  relatedFeedbackId?: number | null;
}

export interface NotificationList {
  items: Notification[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}
