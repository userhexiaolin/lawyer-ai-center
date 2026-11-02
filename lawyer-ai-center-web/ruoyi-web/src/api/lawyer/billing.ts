import { lawyerRequest } from './index';
export interface BillingPlan { code:string; name:string; price:number; chatCredits:number; reviewCredits:number; validDays:number; memberDays:number }
export interface BillingAccount { remainingChat:number; remainingReview:number; memberUntil:string|null; memberActive:boolean }
export interface BillingOrder { orderNo:string; planName:string; amount:number; status:'PENDING'|'PAID'|'CLOSED'|'REFUNDED'; channel:string; payUrl:string|null; createdAt:string; expiresAt:string; paidAt:string|null }
export const billingStatus={PENDING:'待支付',PAID:'已支付',CLOSED:'已关闭',REFUNDED:'已退款'};
export const billingPlans=()=>lawyerRequest<{data:{purchasingEnabled:boolean;plans:BillingPlan[]}}>('/lawyer/billing/plans');
export const billingAccount=()=>lawyerRequest<{data:BillingAccount}>('/lawyer/billing/account');
export const billingOrders=(page=1)=>lawyerRequest<{data:BillingOrder[]}>('/lawyer/billing/orders?page='+page);
export const billingOrder=(no:string,refresh=false)=>lawyerRequest<{data:BillingOrder}>('/lawyer/billing/orders/'+encodeURIComponent(no)+'?refresh='+refresh);
export const createBillingOrder=(planCode:string,idempotencyKey:string)=>lawyerRequest<{data:BillingOrder}>('/lawyer/billing/orders','POST',{planCode,idempotencyKey});
export const closeBillingOrder=(no:string)=>lawyerRequest<{data:BillingOrder}>('/lawyer/billing/orders/'+encodeURIComponent(no)+'/close','POST',{});
