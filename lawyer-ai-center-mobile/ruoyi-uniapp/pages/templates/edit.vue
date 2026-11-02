<template>
  <view class="doc-page">
    <text class="doc-title">{{canWrite?(id?'编辑模板':'新增模板'):'模板详情'}}</text>
    <text class="doc-caption">基本信息 → 原文与填写字段 → 保存后用于起草</text>
    <view v-if="loading" class="doc-note">正在读取模板与权限…</view>
    <view v-if="error" class="doc-error">{{error}}<button v-if="!ready&&!loading" @click="initialize">重新加载</button></view>
    <view v-if="ready&&!canRead" class="doc-panel doc-empty">暂无此模板的查看权限</view>
    <template v-if="ready&&canRead">
      <view v-if="!canWrite" class="doc-note">当前为只读详情；编辑、原件预览和保存需要对应管理权限。</view>
      <view class="doc-panel">
        <label class="doc-label">模板名称</label><input v-model="form.name" class="doc-input" :disabled="locked||!canWrite" maxlength="100" aria-label="模板名称"/>
        <label class="doc-label">模板分类</label><picker :range="types" range-key="label" :disabled="locked||!canWrite||!!form.sourceVersion" @change="pickType"><view class="doc-select">{{form.documentType||'请选择分类'}}</view></picker>
        <text v-if="form.sourceVersion" class="doc-muted">原件分类随版本保存；需要调整时先核对分类及原文件。</text>
        <label class="doc-label">排序号</label><input v-model="form.sortOrder" class="doc-input" type="number" :disabled="locked||!canWrite" aria-label="模板排序号"/>
        <view class="doc-row doc-label"><text>启用模板</text><switch :checked="form.status==='0'" :disabled="locked||!canWrite" @change="form.status=$event.detail.value?'0':'1'"/></view>
        <label class="doc-label">备注（选填）</label><textarea v-model="form.remark" class="doc-input" :disabled="locked||!canWrite" maxlength="500" aria-label="模板备注"/>
      </view>
      <view class="doc-panel">
        <text class="doc-panel-title">模板原件与正文</text>
        <text class="doc-muted">{{stateLabel(form)}}</text>
        <text v-if="form.sourceFilename" class="doc-muted">{{form.sourceFilename}} · 版本 {{form.sourceVersion}}</text>
        <text v-if="form.analysisWarning" class="doc-note">{{form.analysisWarning}}</text>
        <template-file-picker v-if="canWrite" :type="form.documentType" :disabled="busy||!form.documentType||!categoryEnabled" @busy="working=$event" @uploaded="uploaded" @error="error=$event"/>
        <text class="doc-muted">Word、PDF、TXT、Markdown，单文件不超过10MB。上传只生成规则候选，必须人工核对后才能用于填写。替换原件会替换本页未保存字段。</text>
        <view v-if="form.sourceVersion&&canWrite" class="doc-actions">
          <button :disabled="locked" @click="preview">查看原件 PDF</button>
          <button v-if="form.parseStatus==='uploaded'" :disabled="locked" @click="parse">解析填写字段</button>
        </view>
        <textarea v-if="!form.sourceVersion" v-model="form.content" class="doc-input" :disabled="locked||!canWrite" maxlength="40000" placeholder="粘贴常用文书正文；需要保留原排版请上传原件" aria-label="正文模板内容" placeholder-style="color:#606266;font-size:16px;"/>
      </view>
      <view v-if="form.sourceVersion" class="doc-panel">
        <text class="doc-panel-title">原文与填写字段核对</text><text class="doc-muted">原文、字段位置和单位保持原件定义。只调整允许填写的段落、字段名称和类型，任何调整都会使本页确认失效。</text>
        <view v-for="block in blocks" :key="block.id" class="doc-record">
          <text class="doc-original" selectable>{{block.text}}</text>
          <view class="doc-row doc-label"><text>允许本段填写</text><switch :checked="form.editableBlocks.indexOf(block.id)>=0" :disabled="locked||!canWrite" @change="toggleBlock(block.id,$event.detail.value)"/></view>
          <view v-for="field in block.fields" :key="field.id">
            <view class="doc-row"><text>{{field.original}} · 单位：{{field.unit||'无'}}</text><switch :checked="field.enabled" :disabled="locked||!canWrite" @change="field.enabled=$event.detail.value;invalidate()"/></view>
            <input v-model="field.label" class="doc-input" maxlength="100" :disabled="locked||!canWrite" placeholder="字段名称" :aria-label="'字段名称 '+field.id" @input="invalidate" placeholder-style="color:#606266;font-size:16px;"/>
            <picker :range="kindLabels" :disabled="locked||!canWrite" @change="field.kind=fieldKinds[Number($event.detail.value)];invalidate()"><view class="doc-select">类型：{{kindLabels[fieldKinds.indexOf(field.kind)]||'文本'}}</view></picker>
          </view>
          <text v-if="!block.fields.length" class="doc-muted">本段没有已识别的填写字段，不会自行增加或改写固定文字。</text>
        </view>
        <checkbox-group @change="form.fieldsConfirmed=$event.detail.value.indexOf('confirmed')>=0">
          <label class="doc-check"><checkbox value="confirmed" :checked="form.fieldsConfirmed" :disabled="locked||!canWrite"/><text>我已核对原文、填写位置、字段名称、类型及单位，固定文字保留。</text></label>
        </checkbox-group>
      </view>
      <view v-if="canWrite" class="doc-savebar doc-actions">
        <button class="primary" :loading="busy" :disabled="locked" @click="save(false)">{{form.sourceVersion?'确认字段并保存':'保存模板'}}</button>
        <button v-if="form.sourceVersion" :disabled="locked" @click="save(true)">保存待核模板</button>
        <button :disabled="locked" @click="leave">取消</button>
      </view>
    </template>
  </view>
</template>
<script>
import TemplateFilePicker from '@/components/template-file-picker.vue'
import { normalizeSessionId } from '@/common/api/session'
import { documentPermissions,canManage,templateTypes,templateDetail,saveTemplate,documentRequest } from '@/common/api/documents'
import { newTemplate,validateMetadata,sourceFields,templateStatus,scopeCurrent,confirmAction } from '@/common/document-workflow'
import { requestDocumentFile,openDocumentFile } from '@/common/document-files'
export default {
  components:{TemplateFilePicker},
  data(){return{id:'',ready:false,loading:false,busy:false,working:false,error:'',permissions:[],types:[],form:newTemplate(),baseline:'',fieldKinds:['text','money','number','date'],kindLabels:['文本','金额','数字','日期']}},
  computed:{
    canRead(){return this.id?canManage(this.permissions,'template','query'):canManage(this.permissions,'template','add')},
    canWrite(){return canManage(this.permissions,'template',this.id?'edit':'add')},
    locked(){return this.loading||this.busy||this.working},
    categoryEnabled(){return this.types.some(type=>type.value===this.form.documentType&&!type.disabled)},
    blocks(){return (this.form.blocks||[]).map(block=>({...block,fields:(this.form.fields||[]).filter(field=>field.blockId===block.id)}))}
  },
  onLoad(options){this.id=normalizeSessionId(options.id);this._owner=uni.getStorageSync('token');this.initialize()},
  onShow(){if(this._owner&&this._owner!==uni.getStorageSync('token')){this._version=(this._version||0)+1;this.form=newTemplate();this.permissions=[];this.ready=false;this.loading=this.busy=this.working=false;this.error='账号已变化，请返回重新打开模板'}},
  onUnload(){this._disposed=true;this._version=(this._version||0)+1},
  onBackPress(){if(this._allowLeave)return false;if(this.locked)return true;if(this.dirty()&&!this._allowLeave){this.leave();return true}return false},
  methods:{
    stateLabel:templateStatus,dirty(){return this.ready&&JSON.stringify(this.form)!==this.baseline},
    async initialize(){
      const owner=uni.getStorageSync('token'),version=this._version=(this._version||0)+1;this._disposed=false;this.loading=true;this.error=''
      if(!owner){this.loading=false;uni.navigateTo({url:'/pages/h5/login'});return}
      try{
        const permissions=await documentPermissions(this.$request);if(!scopeCurrent(this,version,owner))return;this.permissions=permissions
        if(!this.canRead){this.ready=true;return}
        const result=await Promise.all([templateTypes(this.$request,true),this.id?templateDetail(this.$request,this.id):Promise.resolve(null)])
        if(!scopeCurrent(this,version,owner))return
        this.types=result[0];const value=result[1]
        this.form=value?{...newTemplate(),...value,fields:value.blocks&&value.blocks.some(block=>Array.isArray(block.fields))?sourceFields(value):value.fields||[],editableBlocks:value.editableBlocks||[]} :newTemplate(this.types.find(type=>!type.disabled)?.value||'')
        this._originalType=this.form.documentType;this._originalStatus=this.form.status;this.ready=true;this.baseline=JSON.stringify(this.form)
      }catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.loading=false}
    },
    pickType(event){const type=this.types[Number(event.detail.value)];if(type&&!type.disabled){this.form.documentType=type.value;this.invalidate()}},
    invalidate(){this.form.fieldsConfirmed=false},
    toggleBlock(id,enabled){this.form.editableBlocks=enabled?Array.from(new Set(this.form.editableBlocks.concat(id))):this.form.editableBlocks.filter(value=>value!==id);this.invalidate()},
    async uploaded(result){
      if(this._disposed||this._owner!==uni.getStorageSync('token'))return
      if(this.form.sourceVersion&&!await confirmAction(uni,'替换当前工作原件？','新文件已解析；应用后会替换本页未保存的字段配置。保存前不影响已保存模板或历史文书。','应用新原件'))return
      if(this._disposed||this._owner!==uni.getStorageSync('token'))return
      const old=this.form;this.form={...old,...result,id:old.id,expectedRevision:old.expectedRevision,name:old.name||String(result.sourceFilename||'').replace(/\.[^.]+$/,''),documentType:old.documentType,fields:sourceFields(result),editableBlocks:result.editableBlocks||[],fieldsConfirmed:false}
      this.error=''
    },
    async parse(){
      if(this.locked)return
      const owner=uni.getStorageSync('token'),version=this._version;this.busy=true;this.error=''
      try{const result=await documentRequest(this.$request,'/legal/doc-template/parse/'+encodeURIComponent(this.form.sourceVersion)+'?type='+encodeURIComponent(this.form.documentType),'POST');if(scopeCurrent(this,version,owner))await this.uploaded(result.data)}
      catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.busy=false}
    },
    async preview(){
      if(this.locked)return
      const owner=uni.getStorageSync('token'),version=this._version;this.busy=true;this.error=''
      try{const data=await requestDocumentFile('/legal/doc-template/preview/'+encodeURIComponent(this.form.sourceVersion),{},'pdf','GET');if(scopeCurrent(this,version,owner))await openDocumentFile(data,'pdf',this.form.name+'原件')}
      catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.busy=false}
    },
    async save(draft=false){
      if(this.locked||!this.canWrite)return
      this.error=validateMetadata(this.form);if(this.error)return
      if(!this.categoryEnabled&&(!this.id||this.form.documentType!==this._originalType||this.form.status==='0'&&this._originalStatus==='1')){this.error='请选择启用的分类';return}
      if(!this.form.sourceVersion&&!this.form.content.trim()){this.error='请填写模板正文或上传原件';return}
      if(this.form.sourceVersion&&!draft&&(!this.form.fieldsConfirmed||this.form.parseStatus==='uploaded'||!this.form.fields.some(field=>field.enabled&&this.form.editableBlocks.indexOf(field.blockId)>=0))){this.error='请核对至少一个填写字段并勾选确认，或先保存待核模板';return}
      const owner=uni.getStorageSync('token'),version=this._version;this.busy=true
      try{
        if(this.id&&this.form.status!==this._originalStatus&&!await confirmAction(uni,'确认修改模板使用状态？','停用后不供新起草选择；历史文书保留。启用仍需分类和填写字段满足条件。'))return
        if(!scopeCurrent(this,version,owner))return
        await saveTemplate(this.$request,{...this.form,name:this.form.name.trim(),sortOrder:Number(this.form.sortOrder),fieldsConfirmed:!draft&&!!this.form.fieldsConfirmed,saveDraft:!!draft})
        if(!scopeCurrent(this,version,owner))return
        this.baseline=JSON.stringify(this.form);this._allowLeave=true;uni.showToast({title:draft?'待核模板已保存':'模板已保存',icon:'none'});uni.navigateBack()
      }catch(error){if(scopeCurrent(this,version,owner))this.error=(Number(error.code)===409?'版本已变化，当前编辑已保留；请返回重新核对。':'')+error.message}
      finally{if(scopeCurrent(this,version,owner))this.busy=false}
    },
    async leave(){if(this.locked)return;if(this.dirty()&&!await confirmAction(uni,'放弃未保存的模板？','当前原件或字段修改尚未保存。','放弃修改'))return;this._allowLeave=true;uni.navigateBack()}
  },
  watch:{ready(value){if(value){this._originalType=this.form.documentType;this._originalStatus=this.form.status}}}
}
</script>
<style lang="scss">@import '@/common/document-workspace.scss';</style>
