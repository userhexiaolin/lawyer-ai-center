import {defineStore} from 'pinia';
import {ref,watch} from 'vue';
import {useUserStore} from './user';
import {useAgentStore} from './agent';
import {useChatStore,type WorkflowBinding} from './chat';
import {useModelStore} from './model';
import {lawyerRequest} from '@/api/lawyer';
import {getWorkflowDetail} from '@/api/chat';
import {getStartInputs} from '@/utils/workflow';
export interface SessionBinding {mode:'model'|'agent'|'workflow';agentId?:string;model?:string;workflow?:WorkflowBinding}
export const useSessionBindingStore=defineStore('sessionBinding',()=>{
 const user=useUserStore(),agent=useAgentStore(),chat=useChatStore(),model=useModelStore();
 const loading=ref(false),error=ref('');let epoch=0;
 watch(()=>user.token,()=>{epoch++;loading.value=false;error.value='';},{flush:'sync'});
 function snapshot():SessionBinding{return chat.currentWorkflow?{mode:'workflow',workflow:chat.currentWorkflow}:{mode:agent.currentAgentInfo.id?'agent':'model',agentId:agent.currentAgentInfo.id?String(agent.currentAgentInfo.id):undefined,model:model.currentModelInfo.modelName};}
 async function save(id:string){if(!/^[1-9]\d*$/.test(id))throw Error('会话标识无效');await lawyerRequest('/legal/session/'+id+'/binding','PUT',snapshot());}
 async function restore(id:string){
  const v=++epoch,token=user.token;loading.value=true;error.value='';
  agent.clearCurrentAgentInfo();chat.clearCurrentWorkflow();
  try{
   const r=await lawyerRequest<{data:SessionBinding}>('/legal/session/'+id+'/binding');
   if(v!==epoch||token!==user.token)return;
   if(r.data.mode==='agent'){
    await agent.requestAgentList();if(v!==epoch||token!==user.token)return;
    const selected=agent.agentList.find(a=>String(a.id)===r.data.agentId);
    if(!selected)throw Error('本会话的助手已停用，请重新选择后再发送');agent.setCurrentAgentInfo(selected);
   }else if(r.data.mode==='workflow'){
    const saved=r.data.workflow;if(!saved?.uuid)throw Error('会话流程信息不完整');
    const res=await getWorkflowDetail(saved.uuid);if(v!==epoch||token!==user.token)return;
    const detail=(res as any)?.data??res;
    chat.setCurrentWorkflow({uuid:saved.uuid,title:saved.title||detail.title,startInputs:getStartInputs(detail),inputs:saved.inputs||[]});
   }else if(r.data.model){
    await model.requestModelList();if(v!==epoch||token!==user.token)return;
    const selected=model.modelList.find(m=>m.modelName===r.data.model);if(selected)model.setCurrentModelInfo(selected);
   }
  }catch(e){if(v===epoch&&token===user.token)error.value=(e as Error).message||'会话设置恢复失败，请重试';}
  finally{if(v===epoch)loading.value=false;}
 }
 return {loading,error,snapshot,save,restore};
});
