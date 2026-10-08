export interface Investment {
    ol?: string;
    symbol?: string;
  exchange?: string;
  market?: string;
  assetClass?: string;
  currency?: string;
  investmentId:number;
  name:string;
  description: string;
  type: string;
  purchasePrice: number;
  currentPrice: number;
  quantity: number;
  purchaseDate: string;
  status: string;
}
