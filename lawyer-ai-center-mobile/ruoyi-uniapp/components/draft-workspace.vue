<template>
  <view class="doc-page workspace-panel">
    <text class="doc-caption">选择模板 → 填写要素 → 核对并保存文书</text>
    <view v-if="!loggedIn" class="doc-panel doc-empty"><text>登录后开始起草</text><button @click="login">去登录</button></view>
    <template v-else>
      <view v-if="loading" class="doc-note">正在加载文书类型与模板…</view>
      <view v-if="error" class="doc-error">{{error}}<button v-if="!types.length&&!loading" @click="initialize">重新加载</button></view>
      <view v-if="notice" class="doc-note">{{notice}}</view>
      <view class="doc-tabs"><button :class="{active:stage==='form'}" @click="stage='form'">填写要素</button><button :class="{active:stage==='document'}" :disabled="!content" @click="stage='document'">文书草稿</button></view>
      <view v-show="stage==='form'">
        <view class="doc-panel">
          <label class="doc-label">文书类型</label><picker :range="types" range-key="label" :disabled="busy||loading" @change="chooseType"><view class="doc-select">{{type||'暂无可用类型'}}</view></picker>
          <label class="doc-label">文书模板</label><picker :range="templateOptions" range-key="name" :disabled="busy||loading" @change="chooseTemplate"><view class="doc-select">{{templateLabel}}</view></picker>
          <view class="doc-actions"><button :disabled="busy||loading" @click="refreshOptions">刷新模板</button><button @click="manage">管理模板</button></view>
          <text v-if="selectedOption&&selectedOption.preservesLayout===false" class="doc-note">此模板仅保存正文，将参考其结构起草；下载采用标准版式。保留排版需补传原件。</text>
          <text v-if="selectedOption&&selectedOption.warning" class="doc-note">{{selectedOption.warning}}</text>

        </view>
        <view v-if="isTemplate&&schema" class="doc-panel">
          <text class="doc-panel-title">{{schema.templateName}} · 填写内容</text>
          <text class="doc-muted">模板版本 {{schema.version}} · 共 {{schema.fields.length}} 项，已填写 {{filled}} 项</text>
          <text class="doc-note">只填写真实已知信息，不确定请留空；金额、日期、当事人及来源须人工核对。生成后仍可补充未填项。</text>
          <view v-for="(field,index) in schema.fields" :key="field.id">
            <label class="doc-label">{{index+1}}. {{field.label}}{{field.unit?'（'+field.unit+'）':''}}</label>
            <input v-model="values[index].value" class="doc-input" :disabled="busy||blocked" maxlength="20000" :aria-label="field.label"/>
            <text class="doc-muted">原文：{{field.context}}</text>
          </view>
        </view>
        <view v-else-if="selectedId&&!blocked" class="doc-panel">
          <text class="doc-panel-title">核对起草要素</text>
          <label class="doc-label">当事人 / 收件人</label><textarea v-model="form.parties" class="doc-input" maxlength="2000" :disabled="busy" aria-label="当事人或收件人"/>
          <label class="doc-label">事由与事实</label><textarea v-model="form.facts" class="doc-input" maxlength="10000" :disabled="busy" aria-label="事由与事实"/>
          <label class="doc-label">起草目的 / 诉求</label><textarea v-model="form.purpose" class="doc-input" maxlength="3000" :disabled="busy" aria-label="起草目的或诉求"/>
          <label class="doc-label">期限与其他要求（选填）</label><input v-model="form.deadline" class="doc-input" maxlength="2000" :disabled="busy" aria-label="期限与其他要求"/>
        </view>
        <view class="doc-panel"><button :disabled="busy" :aria-expanded="caseOpen" @click="caseOpen=!caseOpen">{{caseOpen?'收起案件关联':'查看或关联案件（选填）'}}</button><case-association v-if="caseOpen" :key="owner" v-model="caseId" :session-id="sessionId" :disabled="busy"/></view>
        <view class="doc-actions doc-savebar"><button class="primary" :loading="composing" :disabled="busy||loading||blocked||!selectedId||(isTemplate&&!schema)" @click="generate">{{isTemplate?'按模板生成文书':'按要素起草'}}</button><button v-if="sessionId" :disabled="busy||loading" @click="recoverCurrent">恢复当前文书</button></view>
      </view>
      <view v-if="stage==='document'&&content" class="doc-panel">
        <view class="doc-row"><text class="doc-panel-title">{{type}} · 草稿</text><text class="doc-tag">待人工核对</text></view>
        <text class="doc-note" v-if="fillDescription">{{fillDescription}}</text>
        <text v-for="issue in issues" :key="issue" class="doc-error">{{issue}}</text>
        <view class="doc-row"><text class="doc-muted">{{saving?'正在保存…':documentDirty?'正文有未保存修改':'正文无新修改'}}</text><switch :checked="editing" :disabled="busy||protocolOnly" @change="editing=$event.detail.value"/><text>编辑正文</text></view>
        <template v-if="structured">
          <view v-for="block in structured.blocks" :key="block.id" class="doc-record">
            <text class="doc-muted">{{block.editable?'模板填充区域':'固定原文（只读）'}}</text>
            <textarea v-if="editing&&block.editable" :value="block.text" auto-height class="doc-input" maxlength="-1" :disabled="busy" @input="editBlock(block.id,$event.detail.value)"/>
            <text v-else class="doc-original" selectable>{{block.text}}</text>
          </view>
        </template>
        <textarea v-else-if="editing&&!protocolOnly" v-model="content" class="doc-input document-editor" maxlength="200000" :disabled="busy" aria-label="编辑文书正文"/>
        <text v-else class="doc-original" selectable>{{visibleContent}}</text>
        <view class="doc-savebar doc-actions"><button class="primary" :loading="saving" :disabled="busy||!draft" @click="save">保存草稿</button><button :disabled="busy||issues.length>0" @click="exportFile('docx')">导出 Word</button><button :disabled="busy||issues.length>0" @click="exportFile('pdf')">导出 PDF</button><button :disabled="busy||issues.length>0" @click="copy">复制正文</button></view>
        <text class="doc-muted">Word/PDF沿用服务端原模板与版本校验；页面仅作内容预览。编辑后请先保存并复核，不能以生成成功代替法律核验。</text>
      </view>
      <view class="doc-panel">
        <view class="doc-row"><text class="doc-panel-title">最近起草</text><button size="mini" :disabled="busy" @click="loadRecent">刷新</button></view>
        <text v-if="recentError" class="doc-error">{{recentError}}</text>
        <button v-for="item in recent" :key="item.id" class="recent-draft" :disabled="busy" @click="recover(item)">{{titleFor(item.sessionTitle)}}</button>
        <text v-if="!recent.length&&!recentError" class="doc-muted">暂无最近文书。已保存的文书会保留在当前账号会话中。</text>
      </view>
    </template>
  </view>
</template>
<script>
import { normalizeSessionId,createSession } from '@/common/api/session'
import {presentMessage,messageTitle} from '@/common/message-presentation'
import { createLegalSession } from '@/common/api/legal-entry'
import { templateTypes,enabledTemplates,templateSchema,composeDraft,loadDraft,saveDraft,documentRequest } from '@/common/api/documents'
import { takeTemplate,confirmAction,scopeCurrent,parseTemplateDocument,displayDocument,documentIssues,updateDocumentBlock,standardPrompt,readFormSnapshot } from '@/common/document-workflow'
import { requestDocumentFile,openDocumentFile } from '@/common/document-files'
const STANDARD='__standard__'
export default {
  props:{active:{type:Boolean,default:false}},
  data(){return{owner:'',loggedIn:false,types:[],type:'',templates:[],selectedId:'',schema:null,values:[],form:{parties:'',facts:'',purpose:'',deadline:''},caseId:'',caseOpen:false,sessionId:'',draft:null,content:'',savedContent:'',inputBaseline:'',stage:'form',editing:false,loading:false,composing:false,saving:false,exporting:false,blocked:false,error:'',notice:'',recent:[],recentError:''}},
  computed:{
    busy(){return this.composing||this.saving||this.exporting},templateOptions(){return[{id:STANDARD,name:'标准起草（不使用原件模板）'}].concat(this.templates)},
    selectedOption(){return this.templates.find(item=>item.id===this.selectedId)},templateLabel(){const item=this.templateOptions.find(item=>item.id===this.selectedId);return item?item.name:'请选择模板'},
    isTemplate(){return this.selectedId&&this.selectedId!==STANDARD&&(!this.selectedOption||this.selectedOption.preservesLayout!==false)},
    filled(){return this.values.filter(item=>item.value.trim()).length},structured(){return parseTemplateDocument(this.content)},issues(){return this.protocolOnly?['该条记录暂不能作为可编辑的文书正文，请先核对起草结果。']:documentIssues(this.content)},protocolOnly(){return presentMessage(this.content).structured&&!this.structured},visibleContent(){return presentMessage(this.content).text},
    documentDirty(){return this.content!==this.savedContent},
    fillDescription(){const result=this.structured&&this.structured.fillReport;return result&&result.status==='partial'?'部分填写内容仍为空，请核对来源并补充；未填处保留模板空白。':''}
  },
  mounted(){if(this.active)this.activate()},
  beforeDestroy(){this._disposed=true;this._version=(this._version||0)+1},
  watch:{active(value){if(value)this.activate()}},
  methods:{
    titleFor:messageTitle,
    async activate(){
    this._disposed=false;const owner=uni.getStorageSync('token');this.loggedIn=!!owner
    if(this.owner!==owner){this._version=(this._version||0)+1;this.owner=owner;this.types=[];this.type='';this.templates=[];this.clearDraft();this.recent=[];this.error=this.notice='';this.loading=this.composing=this.saving=this.exporting=false}
    if(!owner)return
    const pending=takeTemplate(uni)
    if(pending&&this.dirty()&&!await confirmAction(uni,'切换到所选模板？','当前未保存填写或正文会被替换。已保存的文书保留。','切换模板'))return
    if(this.busy)return
    await this.initialize(pending)
    if(this._resumeOnShow&&this.sessionId){this._resumeOnShow=false;await this.recoverCurrent()}
    this.loadRecent()
  },
    refresh(){if(!this.busy&&!this.loading)return this.refreshOptions()},

    login(){uni.navigateTo({url:'/pages/h5/login'})},manage(){this.$emit('templates')},
    snapshot(){return JSON.stringify([this.type,this.selectedId,this.values,this.form,this.sessionId?'':this.caseId])},
    dirty(){return this.documentDirty||!!this.inputBaseline&&this.snapshot()!==this.inputBaseline},
    clearDraft(){this.selectedId='';this.schema=null;this.values=[];this.form={parties:'',facts:'',purpose:'',deadline:''};this.caseId='';this.caseOpen=false;this.sessionId='';this.draft=null;this.content=this.savedContent='';this.stage='form';this.editing=false;this.blocked=false;this.inputBaseline=''},
    async initialize(preference){
      if(this.loading)return
      const owner=this.owner,version=this._version;this.loading=true;this.error=''
      try{
        const types=await templateTypes(this.$request);if(!scopeCurrent(this,version,owner))return
        this.types=types
        const type=preference?preference.type:(types.some(item=>item.value===this.type)?this.type:types[0]?.value||'')
        if(!types.some(item=>item.value===type)){this.blocked=true;throw new Error('所选模板分类已不可用，请在模板管理中核对')}
        if(preference){this.clearDraft();this.type=type}else this.type=type
        await this.readOptions(preference&&preference.id)
      }catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.loading=false}
    },
    async readOptions(requested){
      const owner=this.owner,version=this._version,request=this._fieldRequest=(this._fieldRequest||0)+1
      const values=await enabledTemplates(this.$request,this.type)
      if(!scopeCurrent(this,version,owner)||request!==this._fieldRequest)return
      this.templates=values
      if(requested&&!values.some(item=>item.id===requested)){this.selectedId='';this.blocked=true;throw new Error('所选模板已停用、删除或移类。请明确重新选择，当前不会换用其他模板。')}
      const selected=requested||this.selectedId
      if(selected&&selected!==STANDARD&&!values.some(item=>item.id===selected)&&this.dirty()){this.blocked=true;throw new Error('当前模板已不可用，填写已保留；请核对后重新选择')}
      this.selectedId=requested|| (selected===STANDARD||values.some(item=>item.id===selected)?selected:values[0]?.id||STANDARD)
      this.blocked=false
      if(this.isTemplate){
        const schema=await templateSchema(this.$request,this.type,this.selectedId)
        if(!scopeCurrent(this,version,owner)||request!==this._fieldRequest)return
        if(this.schema&&this.schema.version!==schema.version&&this.values.some(item=>item.value.trim())){this.blocked=true;throw new Error('模板字段版本已变化，旧填写已保留。请复制核对后重新选择模板。')}
        const old=this.schema&&this.schema.templateId===schema.templateId?this.values:[]
        this.schema=schema;this.values=schema.fields.map(field=>old.find(item=>item.fieldId===field.id)||{fieldId:field.id,value:'',quote:'',origin:'manual'})
      }else{this.schema=null;this.values=[]}
      if(!this.inputBaseline)this.inputBaseline=this.snapshot()
    },
    async refreshOptions(){if(this.busy||this.loading)return;this.loading=true;this.error='';try{await this.readOptions()}catch(error){this.error=error.message}finally{this.loading=false}},
    async chooseType(event){
      if(this.busy||this.loading)return
      const type=this.types[Number(event.detail.value)]?.value;if(!type||type===this.type)return
      if(this.dirty()&&!await confirmAction(uni,'切换文书类型？','未保存的填写和正文将清除，已保存文书保留。','切换类型'))return
      this._fieldRequest=(this._fieldRequest||0)+1;this.clearDraft();this.type=type;await this.refreshOptions()
    },
    async chooseTemplate(event){
      if(this.busy||this.loading)return
      const id=this.templateOptions[Number(event.detail.value)]?.id
      if(!id)return
      if(id===this.selectedId&&!this.blocked)return
      if(this.dirty()&&!await confirmAction(uni,'切换文书模板？','当前未保存填写和正文将清除，已保存文书保留。','切换模板'))return
      const type=this.type;this.clearDraft();this.type=type;this.selectedId=id;await this.refreshOptions()
    },
    async generate(){
      if(this.busy||this.loading||this.blocked||!this.selectedId)return
      if(this.content&&!await confirmAction(uni,'重新生成文书？','将使用当前填写生成新的正文；正文中的人工编辑不会自动回填到要素。请先保存需要保留的版本。','重新生成'))return
      const owner=this.owner,version=this._version;this.composing=true;this.error=''
      try{
        if(this.isTemplate){
          if(!this.schema)throw new Error('请先加载模板填写字段')
          if(!this.sessionId)this.sessionId=await createSession(this.$request,{sessionTitle:'文书起草 - '+this.type,...(this.caseId?{caseId:this.caseId}:{})})
          if(!scopeCurrent(this,version,owner))return
          const result=await composeDraft(this.$request,this.sessionId,{type:this.type,templateId:this.selectedId,version:this.schema.version,values:this.values.map(value=>({...value,origin:'manual',quote:''})),sourceText:'',sourceName:''})
          if(!scopeCurrent(this,version,owner))return
          if(normalizeSessionId(result.sessionId)!==this.sessionId||!normalizeSessionId(result.messageId))throw new Error('合成返回的会话异常，请恢复当前文书核对')
          this.draft=result;this.content=this.savedContent=result.content;this.inputBaseline=this.snapshot();this.stage='document';this.notice='文书已按模板合成并保存，请人工核对后使用。'
        }else{
          const prompt=standardPrompt(this.type,this.form)
          const response=await documentRequest(this.$request,'/legal/doc-template/prompt','POST',{type:this.type,templateId:this.selectedId===STANDARD?undefined:this.selectedId,useTemplate:this.selectedId!==STANDARD,content:prompt})
          if(!scopeCurrent(this,version,owner))return
          const entry=await createLegalSession(this.$request,uni,{agentName:'文书起草助手',caseId:this.caseId,sessionTitle:'文书起草 - '+this.type,question:response.data.content})
          if(!scopeCurrent(this,version,owner))return
          this.sessionId=entry.sessionId;this._resumeOnShow=true;this.notice='已进入文书助手。生成后返回此页可恢复、编辑和导出当前文书。'
          uni.navigateTo({url:entry.url})
        }
      }catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.composing=false}
    },
    async loadRecent(){const owner=this.owner,version=this._version;this.recentError='';try{const result=await documentRequest(this.$request,'/system/session/list','GET',{sessionTitle:'文书起草 - ',pageNum:1,pageSize:20,orderByColumn:'id',isAsc:'desc'});if(scopeCurrent(this,version,owner))this.recent=(result.rows||[]).filter(row=>normalizeSessionId(row.id)&&/^文书起草 - /.test(row.sessionTitle||'')).slice(0,8)}catch(error){if(scopeCurrent(this,version,owner))this.recentError=error.message}},
    recoverCurrent(){return this.recover({id:this.sessionId,sessionTitle:'文书起草 - '+this.type})},
    async recover(item){
      if(this.busy||!normalizeSessionId(item.id))return
      if(this.dirty()&&!await confirmAction(uni,'恢复此文书？','未保存的编辑会被替换，请确认已保留需要的内容。','恢复文书'))return
      const owner=this.owner,version=this._version;this.saving=true;this.error=''
      try{
        const draft=await loadDraft(this.$request,item.id);if(!scopeCurrent(this,version,owner))return
        const snapshot=readFormSnapshot(draft.input),originalType=snapshot?.type||String(item.sessionTitle||'').replace(/^文书起草 - /,'')
        const type=this.types.some(row=>row.value===originalType)?originalType:originalType||this.types[0]?.value||''
        let templates=[],schema=null,values=[],blocked=false,notice=''
        try{
          templates=await enabledTemplates(this.$request,type);if(!scopeCurrent(this,version,owner))return
          if(snapshot){
            schema=await templateSchema(this.$request,type,snapshot.templateId);if(!scopeCurrent(this,version,owner))return
            const sameVersion=schema.version===snapshot.version
            blocked=!sameVersion||!templates.some(row=>row.id===snapshot.templateId)
            values=sameVersion?schema.fields.map(field=>snapshot.values.find(value=>value.fieldId===field.id)||{fieldId:field.id,value:'',quote:'',origin:'manual'}):snapshot.values.map(value=>({...value}))
            if(blocked)notice='原文书已恢复，模板版本或可用状态已变化；旧文书可核对，重新填写前请重选模板。'
            if(!sameVersion)schema=null
          }
        }catch(error){
          if(!scopeCurrent(this,version,owner))return
          blocked=!!snapshot;schema=null;values=snapshot?snapshot.values.map(value=>({...value})):[]
          notice='原文书已恢复，但当前模板暂不可读取：'+error.message+'。正文仍可核对；重新填写前请重选模板。'
        }
        if(!scopeCurrent(this,version,owner))return
        this.clearDraft();this.type=type;this.templates=templates;this.schema=schema;this.values=values;this.blocked=blocked;this.notice=notice
        this.sessionId=normalizeSessionId(item.id);this.draft=draft;this.content=this.savedContent=draft.content;this.selectedId=snapshot?.templateId||STANDARD
        this.inputBaseline=this.snapshot();this.stage='document'
      }catch(error){if(scopeCurrent(this,version,owner))this.error='恢复未完成，当前填写和正文已保留：'+error.message}
      finally{if(scopeCurrent(this,version,owner))this.saving=false}
    },
    editBlock(id,value){try{this.content=updateDocumentBlock(this.content,id,value)}catch(error){this.error=error.message}},
    async save(){if(this.busy||this.protocolOnly||!this.draft)return;const owner=this.owner,version=this._version,snapshot=this.content;this.saving=true;this.error='';try{const saved=await saveDraft(this.$request,this.sessionId,this.draft,snapshot);if(!scopeCurrent(this,version,owner))return;this.draft=saved;this.content=this.savedContent=saved.content;this.notice='草稿已保存，仍需人工核对';uni.showToast({title:'草稿已保存',icon:'none'})}catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}finally{if(scopeCurrent(this,version,owner))this.saving=false}},
    async exportFile(format){if(this.busy||this.issues.length)return;const owner=this.owner,version=this._version;this.exporting=true;this.error='';try{const data=await requestDocumentFile('/legal/document/export',{title:this.type,content:this.content,sessionId:this.sessionId,format},format);if(scopeCurrent(this,version,owner))await openDocumentFile(data,format,this.type)}catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}finally{if(scopeCurrent(this,version,owner))this.exporting=false}},
    copy(){if(this.issues.length)return;uni.setClipboardData({data:displayDocument(this.content)})}
  }
}
</script>
<style lang="scss">@import '@/common/document-workspace.scss';.document-editor{height:600rpx}.recent-draft{text-align:left;font-size:16px;margin:16rpx 0;background:var(--brand-background);white-space:normal}</style>
