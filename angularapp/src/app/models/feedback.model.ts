import { Investment } from "./investment.model";
import { User } from "./user.model";

export interface Feedback {
    feedbackId?: number;
  feedbackText: string;
  date: string;
  user?: User;
  investment?: Investment;
  category: string;
  subject?: string;
  /** Administrator response, populated only for the owner after an admin responds. */
  adminResponse?: string;
  responseDate?: string;
}
