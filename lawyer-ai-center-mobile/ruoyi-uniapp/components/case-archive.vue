<template>
 <view class="archive">
  <view class="archive-heading"><text class="title">任务与检查项</text><button size="mini" :disabled="loading||busy" @click="load">刷新</button></view>
  <text v-if="loading">正在加载案件任务与材料…</text>
  <text v-if="taskError" class="error">{{taskError}}</text>
  <template v-if="summary">
   <text class="summary">任务完成 {{summary.doneTasks}} / {{summary.totalTasks}} · 检查项 {{summary.checkedItems}} / {{summary.totalItems}}</text>
   <text class="summary">任务登记工时：{{summary.workMinutes}} 分钟</text>
   <view v-for="task in summary.tasks" :key="task.id" class="task"><text class="task-title">{{task.title}}</text><text>{{statuses[task.status] || task.status}} · {{task.workMinutes}} 分钟</text><text v-if="task.dueAt">截止：{{task.dueAt.replace('T',' ')}}</text><text>检查项 {{checkedCount(task)}} / {{task.checklist.length}}</text><text v-for="item in task.checklist" :key="item.id" :class="{checked:item.checked}">{{item.checked?'☑':'☐'}} {{item.content}}</text></view>
   <text v-if="!summary.tasks.length">暂无任务</text>
  </template>
  <text class="title">材料</text><text v-if="fileError" class="error">{{fileError}}</text>
  <view v-for="file in files" :key="file.ossId" class="material"><text class="task-title">{{file.originalName || file.fileName || '未命名材料'}}</text><text>上传时间：{{file.uploadedAt ? file.uploadedAt.replace('T',' ') : '暂无记录'}}</text><button size="mini" :disabled="busy||loading" @click="open(file)">查看 / 下载</button></view>
  <text v-if="!files.length&&!loading&&!fileError">暂无材料，请在 PC 案件详情上传归档</text>
 </view>
</template>
<script>
import {caseApi} from '@/common/api/cases';
export default {
 props:{caseId:{type:String,required:true}},
 data(){return {summary:null,files:[],loading:false,busy:false,taskError:'',fileError:'',statuses:{todo:'待办',doing:'进行中',done:'已完成'}};},
 mounted(){this.load();},beforeDestroy(){this._disposed=true;this._version=(this._version||0)+1;},
 watch:{caseId(){this.summary=null;this.files=[];this.busy=false;this.load();}},
 methods:{
  checkedCount(task){return task.checklist.filter(item=>item.checked).length;},
  async load(){
   if(this._disposed||this.busy)return;
   const version=this._version=(this._version||0)+1,owner=uni.getStorageSync('token'),id=this.caseId;
   this.loading=true;this.taskError='';this.fileError='';this.summary=null;this.files=[];
   const active=()=>!this._disposed&&version===this._version&&owner===uni.getStorageSync('token');
   try{await Promise.all([
    caseApi(this.$request,'/'+id+'/tasks').then(r=>{if(active())this.summary=r.data;}).catch(e=>{if(active())this.taskError=e.message;}),
    caseApi(this.$request,'/'+id+'/files').then(r=>{if(active())this.files=r.data;}).catch(e=>{if(active())this.fileError=e.message;})
   ]);}finally{if(active())this.loading=false;}
  },
  async open(file){
   if(this.busy||this.loading||this._disposed)return;this.busy=true;const owner=uni.getStorageSync('token'),version=this._version;
   const active=()=>!this._disposed&&version===this._version&&owner===uni.getStorageSync('token');
   let popup;
   // #ifdef H5
   popup=window.open('about:blank','_blank');if(popup)popup.opener=null;
   // #endif
   try{
    const r=await caseApi(this.$request,'/'+this.caseId+'/files/'+encodeURIComponent(file.ossId));
    if(!active()){if(popup)popup.close();return;}
    if(!r.data.url||!/^https?:\/\//i.test(r.data.url))throw Error('材料地址暂不可用，请刷新后重试');
    // #ifdef H5
    if(!popup)throw Error('请允许浏览器打开窗口，或稍后重试');popup.location.href=r.data.url;return;
    // #endif
    // #ifndef H5
    const result=await new Promise((resolve,reject)=>uni.downloadFile({url:r.data.url,success:res=>res.statusCode===200?resolve(res):reject(Error('材料下载失败，请刷新地址后重试')),fail:()=>reject(Error('材料下载失败，请检查网络'))}));
    if(!active())return;
    const suffix=(file.fileSuffix||'').replace(/^\./,'').toLowerCase();
    if(['png','jpg','jpeg','webp','bmp','gif'].includes(suffix))uni.previewImage({urls:[result.tempFilePath]});
    else uni.openDocument({filePath:result.tempFilePath,showMenu:true,fail:()=>uni.showToast({title:'文件已下载，当前设备不支持预览此格式',icon:'none'})});
    // #endif
   }catch(e){if(popup)popup.close();if(active())uni.showToast({title:e.message||'材料打开失败',icon:'none'});}finally{if(active())this.busy=false;}
  }
 }
};
</script>
<style scoped>.archive{margin:26rpx 0;padding:26rpx;background:#fff;border-radius:18rpx}.archive-heading{display:flex;align-items:center;justify-content:space-between}.title{display:block;font-size:16px;font-weight:600;margin:20rpx 0}.summary{display:block;margin:12rpx 0;font-size:16px}.task,.material{padding:20rpx 0;border-top:1px solid #eee}.task text,.material text{display:block;margin:10rpx 0;font-size:16px;word-break:break-word}.task .task-title,.material .task-title{font-size:16px;font-weight:600}.checked{color:#687769;text-decoration:line-through}.error{display:block;color:#b43d3d;margin:18rpx 0}.archive button{font-size:14px}</style>
