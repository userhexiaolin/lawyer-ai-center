import {useChatStore} from '@/stores/modules/chat';
import {checkDownloadAuth} from '@/utils/authExpiry';
import {lawyerRequest} from '@/api/lawyer';
import {useUserStore} from '@/stores';
export interface DocumentDraft {messageId:string;sessionId:string;content:string;input:string}
export const loadDocumentDraft=async(id:string)=>(await lawyerRequest<{data:DocumentDraft}>('/legal/document/draft/'+encodeURIComponent(id))).data;
export async function saveDocumentDraft(id:string,content:string){let messageId:string|undefined;try{messageId=(await loadDocumentDraft(id)).messageId;}catch(e){if(!(e instanceof Error)||!e.message.includes('尚无草稿'))throw e;}await lawyerRequest('/legal/document/draft/'+encodeURIComponent(id),'PUT',{messageId,content});delete useChatStore().chatMap[id];}
export type DocumentFormat='docx'|'pdf'|'md'|'txt';
export interface DocumentDownload {title:string;content:string;sessionId?:string;format?:DocumentFormat;layout?:'template'|'standard'}
export async function documentBlob(input:DocumentDownload){
 const user=useUserStore(),token=user.token;if(!token){user.ensureLogin();throw Error('请先登录');}
 const format=input.format||'docx';
 const response=await fetch(import.meta.env.VITE_API_URL+'/legal/document/export',{method:'POST',headers:{Authorization:'Bearer '+token,clientid:import.meta.env.VITE_CLIENT_ID,'content-type':'application/json'},body:JSON.stringify({...input,format}),signal:AbortSignal.timeout(660000)});
 await checkDownloadAuth(response,token);
 const blob=await response.blob(),prefix=new Uint8Array(await blob.slice(0,5).arrayBuffer()),mime=response.headers.get('content-type')||'';
 const valid=format==='docx'?prefix[0]===0x50&&prefix[1]===0x4b&&prefix[2]===3&&prefix[3]===4:format==='pdf'?new TextDecoder().decode(prefix)==='%PDF-':mime.toLowerCase().startsWith(format==='md'?'text/markdown':'text/plain');
 if(!response.ok||!valid){let message='下载失败，未收到有效的所选格式文档';if(blob.size<65536)try{message=JSON.parse(await blob.text()).msg||message;}catch{/* Preserve safe default. */}throw Error(message);}
 if(user.token!==token)throw Error('登录用户已变化，请重试');return blob;
}
export async function downloadDocument(input:DocumentDownload){
 const blob=await documentBlob(input),format=input.format||'docx';
 const title=input.title.replace(/[<>:"/\\|?*\x00-\x1f]/g,'_').slice(0,100)||'法律文书';
 const date=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
 const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=title+'-'+date+'.'+format;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
