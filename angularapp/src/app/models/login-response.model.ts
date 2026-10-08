export interface LoginResponse {
  token: string;
  username: string;
  userRole: string;
  userId: number;
  mustChangePassword: boolean;
}