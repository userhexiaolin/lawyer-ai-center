<template>
  <mobile-shell ref="shell" :title="pageTitle" :current="section" :busy="entryBusy" @navigate="selectSection" @new-consult="newConsult">
    <view v-show="section==='consult'" class="consult-home">
      <view class="consult-welcome"><text>有什么法律问题？</text></view>
      <view class="consult-compose-area">
        <view v-if="modelLoading" class="consult-model-status" role="status">正在加载可用模型…</view>
        <view v-else-if="modelError" class="consult-error" role="alert"><text>{{modelError}}</text><button tabindex="0" @click="loadInitialModel">重试加载</button></view>
        <view v-if="entryError" class="consult-error"><text>{{entryError}}</text><button tabindex="0" @click="startConsultation(prompt)">重试</button></view>
        <view class="consult-composer consultation-composer">
          <textarea v-model="prompt" class="consult-input composer-input" :disabled="entryBusy" :focus="focusPrompt" auto-height maxlength="20000" placeholder="描述你的问题…" aria-label="咨询问题" :show-confirm-bar="false" :cursor-spacing="20" placeholder-style="color:#606266;font-size:16px;"/>
          <view class="consult-compose-tools composer-toolbar"><consultation-model-select ref="modelSelect" :value="consultationModel" :active="shown&&section==='consult'" :disabled="entryBusy||modelLoading" @input="selectModel"/><text v-if="prompt.length>18000" class="consult-limit">{{prompt.length}} / 20000</text><button class="consult-send composer-send" tabindex="0" aria-label="发送问题" :loading="entryBusy" :disabled="entryBusy||modelLoading||!consultationModel||!prompt.trim()" @click="startConsultation(prompt)"><text v-if="!entryBusy">↑</text></button></view>
        </view>
        <text class="consult-footnote composer-footnote">AI 内容需人工核对</text>
      </view>
    </view>
    <template-library v-if="visited.templates" ref="library" v-show="section==='templates'" :active="shown&&section==='templates'" @draft="startDraft"/>
    <draft-workspace v-if="visited.drafting" ref="drafting" v-show="section==='drafting'" :active="shown&&section==='drafting'" @templates="selectSection('templates')"/>
    <personal-workspace v-if="visited.mine" ref="mine" v-show="section==='mine'" :active="shown&&section==='mine'"/>
  </mobile-shell>
</template>
<script>
import MobileShell from '@/components/mobile-shell.vue'
import ConsultationModelSelect from '@/components/consultation-model-select.vue'
import { getConsultationModels } from '@/common/api/consultation-model'
import TemplateLibrary from '@/components/template-library.vue'
import DraftWorkspace from '@/components/draft-workspace.vue'
import PersonalWorkspace from '@/components/personal-workspace.vue'
import { LEGAL_QUESTIONS,createLegalSession } from '@/common/api/legal-entry'
import { takeMobileSection } from '@/common/mobile-navigation'
import { confirmAction } from '@/common/document-workflow'
export default {
  components:{MobileShell,ConsultationModelSelect,TemplateLibrary,DraftWorkspace,PersonalWorkspace},
  data(){return{section:'consult',shown:false,visited:{templates:false,drafting:false,mine:false},positions:{consult:0,templates:0,drafting:0,mine:0},owner:'',consultationModel:null,modelLoading:false,modelError:'',prompt:'',focusPrompt:false,caseId:'',questions:LEGAL_QUESTIONS,loggedIn:false,entryBusy:false,entryError:''}},
  computed:{pageTitle(){return{consult:'与律行',templates:'模板管理',drafting:'文书起草',mine:'我的'}[this.section]||'与律行'}},
  onLoad(){},
  onShow(){
    this.shown=true;this._entryActive=true
    const token=uni.getStorageSync('token')||'',userId=String((uni.getStorageSync('userInfo')||{}).userId||''),owner=token?userId+':'+token:''
    this.loggedIn=!!(token&&userId)
    if(this.owner!==owner){this.owner=owner;this._modelVersion=(this._modelVersion||0)+1;this.modelLoading=false;this.modelError='';this.consultationModel=null;this.prompt='';this.entryError='';this.entryBusy=false;this.caseId='';this.section='consult';this.visited={templates:false,drafting:false,mine:false};this.positions={consult:0,templates:0,drafting:0,mine:0}}
    const pending=takeMobileSection(uni);if(pending)this.selectSection(pending)
    if(this.loggedIn&&!this.consultationModel)this.loadInitialModel()
  },
  onHide(){this.shown=false;this._entryActive=false;this._modelVersion=(this._modelVersion||0)+1;this.modelLoading=false;if(this.$refs.shell)this.$refs.shell.closeDrawer()},
  onUnload(){this._entryActive=false;this.shown=false;this._modelVersion=(this._modelVersion||0)+1},
  onBackPress(){if(this.$refs.modelSelect&&this.$refs.modelSelect.opened){this.$refs.modelSelect.close();return true}if(this.$refs.shell&&this.$refs.shell.drawerOpen){this.$refs.shell.closeDrawer();return true}if(this.section!=='consult'){this.selectSection('consult');return true}return false},
  onPageScroll(event){this.positions[this.section]=event.scrollTop||0},
  async onPullDownRefresh(){try{const child=this.$refs[{templates:'library',drafting:'drafting',mine:'mine'}[this.section]];if(child&&child.refresh)await child.refresh()}finally{uni.stopPullDownRefresh()}},
  onReachBottom(){if(this.section==='templates'&&this.$refs.library)this.$refs.library.loadMore()},
  methods:{
    selectModel(model){if(this.entryBusy)return;this.consultationModel=model;this.modelError='';this.entryError=''},
    async loadInitialModel(){
      if(this.modelLoading||!this.loggedIn)return
      const version=this._modelVersion=(this._modelVersion||0)+1,token=uni.getStorageSync('token'),userId=String((uni.getStorageSync('userInfo')||{}).userId||'')
      const current=()=>this._entryActive!==false&&version===this._modelVersion&&token===uni.getStorageSync('token')&&userId===String((uni.getStorageSync('userInfo')||{}).userId||'')
      this.modelLoading=true;this.modelError=''
      try{const models=await getConsultationModels(this.$request);if(!current())return;if(!this.consultationModel)this.consultationModel=models[0]||null;if(!models.length)this.modelError='暂无可用模型，请刷新列表或联系管理员。'}
      catch(error){if(current())this.modelError=Number(error.code)===403?'当前账号无权选择模型，请联系管理员。':error.message||'模型加载失败，请检查网络后重试'}
      finally{if(current())this.modelLoading=false}
    },
    goLogin(){uni.navigateTo({url:'/pages/h5/login'})},
    selectSection(section){
      if(this.entryBusy){uni.showToast({title:'咨询正在准备，请稍候',icon:'none'});return}
      if(section==='review'||section==='cases'){uni.navigateTo({url:section==='review'?'/pages/index/home':'/pages/cases/index'});return}
      if(!['consult','templates','drafting','mine'].includes(section))return
      if(section==='mine'&&!uni.getStorageSync('token')){this.goLogin();return}
      if(this.section==='drafting'&&this.$refs.drafting&&this.$refs.drafting.busy){uni.showToast({title:'当前文书正在处理，请稍候',icon:'none'});return}
      if(section===this.section)return
      this.section=section;if(section!=='consult')this.$set(this.visited,section,true)
      this.$nextTick(()=>uni.pageScrollTo({scrollTop:this.positions[section]||0,duration:0}))
    },
    startDraft(){this.positions.drafting=0;this.selectSection('drafting')},
    async newConsult(){
      if(this.entryBusy)return
      if(this.prompt.trim()&&!await confirmAction(uni,'新建咨询？','当前尚未发送的问题将清空。','新建咨询'))return
      this.prompt='';this.entryError='';this.selectSection('consult');this.focusPrompt=false;this.$nextTick(()=>{this.focusPrompt=true})
    },
    async startConsultation(question=''){
      if(this.entryBusy)return
      if(!uni.getStorageSync('token')||!(uni.getStorageSync('userInfo')||{}).userId){this.goLogin();return}
      const content=String(question||'').trim()
      this.entryBusy=true;this.entryError=''
      try{
        const entry=await createLegalSession(this.$request,uni,{question:content,caseId:this.caseId,consultationModel:this.consultationModel})
        if(this._entryActive===false)return
        await new Promise((resolve,reject)=>uni.navigateTo({url:entry.url,success:resolve,fail:reject}))
        this.prompt=''
      }catch(error){if(this._entryActive===false)return;this.entryError=error.message||error.msg||'咨询暂时无法开始，请重试'}
      finally{this.entryBusy=false}
    }
  }
}
</script>
<style lang="scss">
.consult-home { box-sizing:border-box; display:flex; flex-direction:column; min-height:calc(100vh - 56px - var(--status-bar-height,0px)); max-width:760px; margin:0 auto; padding:0 20px; background:var(--brand-surface); }
.consult-welcome { display:flex; flex:1; min-height:180px; align-items:center; justify-content:center; padding:32px 0; font-size:26px; font-weight:500; letter-spacing:.5px; color:var(--brand-text); }
.consult-compose-area { position:sticky; bottom:0; z-index:10; padding:0 0 calc(12px + env(safe-area-inset-bottom)); background:var(--brand-surface); }
.consult-composer { padding:16px 16px 10px; border:1px solid var(--brand-border); border-radius:22px; background:var(--brand-background); }
.consult-input { box-sizing:border-box; width:100%; min-height:58px; max-height:220px; font-size:16px; line-height:1.7; color:var(--brand-text); background:transparent; }
.consult-compose-tools { display:flex; align-items:center; justify-content:flex-end; min-height:40px; gap:12px; }
.consult-compose-tools>.consultation-model-select { flex:1; min-width:0; }
.consult-limit { flex-shrink:0; font-size:14px; color:var(--brand-muted); }
.consult-send { flex-shrink:0; display:flex; align-items:center; justify-content:center; width:40px; min-height:40px; margin:0; padding:0; border-radius:50%; background:var(--brand-text); color:var(--brand-surface); font-size:25px; line-height:1; }
.consult-send::after { border:0; }
.consult-send[disabled] { background:var(--brand-border); color:var(--brand-muted); }
.consult-footnote { display:block; margin-top:10px; text-align:center; font-size:14px; color:var(--brand-muted); }
.consult-error { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:10px 0; color:var(--brand-danger-text); font-size:14px; line-height:1.7; }
.consult-error button { flex-shrink:0; min-height:44px; margin:0; background:transparent; font-size:14px; }
.consult-model-status { padding:10px 0; font-size:14px; color:var(--brand-muted); }
.mobile-body>.workspace-panel { min-height:calc(100vh - 56px); }
@media (min-width:960px) { .mobile-body>.workspace-panel { max-width:960px; margin:0 auto; } }
/* #ifdef H5 */
.consult-home { min-height:calc(100dvh - 56px); }
.consult-send:focus-visible,.consult-input:focus-visible { outline:2px solid var(--brand-link); outline-offset:3px; }
/* #endif */
</style>

<style lang="scss">
@import '../../components/css/consultation-composer.scss';
.consult-composer.consultation-composer { padding:14px 14px 10px; }
.consult-home { padding-left:16px; padding-right:16px; }
</style>
