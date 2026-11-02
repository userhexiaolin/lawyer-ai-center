import router from '@/routers';
import {useUserStore} from '@/stores';
export function checkAuthExpiry(status:number,body?:{code?:number;msg?:string}){
 if(status===401||Number(body?.code)===401){useUserStore().handleAuthExpired(router.currentRoute.value.fullPath,body?.msg||'登录状态已失效，请重新登录');throw Error(body?.msg||'登录状态已失效，请重新登录');}
}
export async function checkDownloadAuth(response:Response,token?:string){if(token&&useUserStore().token!==token)throw Error('登录用户已变化，请重试');let body;const type=response.headers.get('content-type')||'';if(type.includes('json')){try{body=await response.clone().json();}catch{/* Other validation reports malformed responses. */}}checkAuthExpiry(response.status,body);}
