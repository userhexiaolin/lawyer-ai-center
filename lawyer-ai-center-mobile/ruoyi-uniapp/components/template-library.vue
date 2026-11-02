<template>
  <view class="doc-page workspace-panel">
    <text class="doc-caption">先维护模板，再填写并起草文书</text>
    <view class="doc-actions">
      <button v-if="allowed('category','list')" @click="openCategories">模板分类</button>
      <button v-if="allowed('template','add')" @click="edit()">新增模板</button>
      <button v-if="allowed('template','add')" class="primary" @click="edit(null,true)">上传原件</button>
    </view>
    <view v-if="!loggedIn" class="doc-panel doc-empty"><text>登录后管理您的模板</text><button @click="login">去登录</button></view>
    <view v-else-if="identityLoading" class="doc-panel doc-empty">正在核对模板权限…</view>
    <view v-else-if="identityError" class="doc-error">{{identityError}}<button @click="initialize">重新加载权限</button></view>
    <view v-else-if="!allowed('template','list')" class="doc-panel doc-empty"><text>暂无模板管理权限，请联系管理员配置。</text><text class="doc-muted">仍可在“文书起草”使用已启用模板。</text><button @click="openDraft">进入文书起草</button></view>
    <template v-else>
      <view class="doc-panel">
        <view class="doc-row"><text class="doc-panel-title">查找模板</text><button size="mini" @click="filtersOpen=!filtersOpen">{{filtersOpen?'收起筛选':'展开筛选'}}</button></view>
        <view v-if="filtersOpen">
          <input v-model="name" class="doc-input" maxlength="100" placeholder="模板名称" aria-label="模板名称" @confirm="query" placeholder-style="color:#606266;font-size:16px;"/>
          <picker :range="filterTypes" range-key="label" @change="pickType"><view class="doc-select">{{filterTypeLabel}}</view></picker>
          <view class="doc-actions"><button :disabled="loading||!!pending" @click="query">查询</button><button :disabled="loading||!!pending" @click="reset">重置</button></view>
        </view>
        <text class="doc-muted" v-if="!loading">共 {{total}} 个模板{{applied.name?' · 名称：'+applied.name:''}}{{applied.type?' · 分类：'+applied.type:''}}</text>
        <text v-if="typeError" class="doc-error">{{typeError}}<text class="doc-link" @click="loadTypes"> 重新加载分类</text></text>
        <text v-if="pending" class="doc-muted">正在处理所选模板…</text>
        <text v-if="actionError" class="doc-error">{{actionError}}</text>
        <view v-if="loading && !rows.length" class="doc-empty">正在加载模板…</view>
        <view v-if="error" class="doc-error">{{error}}<button @click="load(false)">重新加载模板</button></view>
        <view v-for="row in rows" :key="row.id" class="doc-record">
          <view class="doc-row"><text class="doc-record-title">{{row.name}}</text><text class="doc-tag">{{row.status==='0'?'启用':'停用'}}</text></view>
          <text class="doc-muted">{{row.documentType}} · {{stateLabel(row)}} · 排序 {{row.sortOrder}}</text>
          <text v-if="row.sourceFilename" class="doc-muted">{{row.sourceFilename}}</text>
          <text v-if="row.sourceVersion" class="doc-muted">原件版本 {{row.sourceVersion}}</text>
          <view class="doc-actions">
            <button v-if="canUse(row)" class="primary" :disabled="!!pending" @click="use(row)">按此模板起草</button>
            <button v-if="allowed('template','query')" :disabled="!!pending" @click="edit(row)">{{allowed('template','edit')?'编辑 / 核对':'详情'}}</button>
            <button v-if="allowed('template','edit')" :disabled="!!pending" @click="changeStatus(row)">{{row.status==='0'?'停用':'启用'}}</button>
            <button v-if="allowed('template','remove')" :disabled="!!pending" @click="remove(row)">删除</button>
          </view>
        </view>
        <view v-if="!loading&&!error&&!rows.length" class="doc-empty">{{applied.name||applied.type?'没有匹配的模板，可调整筛选或重置':'暂无模板，可上传原件或新增正文模板'}}</view>
        <button v-if="more&&!error" :loading="loading" :disabled="loading||!!pending" @click="load(true)">加载更多模板</button>
      </view>
    </template>
    <view class="doc-note">上传、解析与人工核对是独立步骤。原件字段确认后才可用于填写；正文模板使用标准版式。</view>
  </view>
</template>
<script>
import { documentPermissions, canManage, templateList, templateTypes, templateDetail, saveTemplate, removeManaged } from '@/common/api/documents'
import { offerTemplate, confirmAction, scopeCurrent, templateStatus, canUseTemplate } from '@/common/document-workflow'
export default {
  props:{active:{type:Boolean,default:false}},
  data(){return{loggedIn:false,permissions:[],identityLoading:false,identityError:'',name:'',type:'',applied:{name:'',type:''},types:[],typeError:'',rows:[],total:0,page:0,more:false,loading:false,error:'',actionError:'',pending:'',filtersOpen:false}},
  computed:{
    filterTypes(){return [{value:'',label:'全部分类'}].concat(this.types.map(item=>({...item,label:item.label+(item.disabled?'（停用）':'')})))},
    filterTypeLabel(){const item=this.filterTypes.find(row=>row.value===this.type);return item?item.label:'全部分类'}
  },
  mounted(){if(this.active)this.activate()},
  beforeDestroy(){this._disposed=true;this._version=(this._version||0)+1},
  watch:{active(value){if(value)this.activate();else this.deactivate()}},
  methods:{
    activate(){this._disposed=false;return this.initialize()},
    deactivate(){this._version=(this._version||0)+1;this.loading=false;this.identityLoading=false;this.pending=''},
    refresh(){if(!this.loading&&!this.pending)return this.initialize()},
    loadMore(){if(this.more&&!this.loading&&!this.error)this.load(true)},

    allowed(area,action){return canManage(this.permissions,area,action)},stateLabel:templateStatus,canUse(row){return canUseTemplate(row,this.types)},
    login(){uni.navigateTo({url:'/pages/h5/login'})},openCategories(){uni.navigateTo({url:'/pages/templates/categories'})},openDraft(){this.$emit('draft')},
    async initialize(){
      const owner=uni.getStorageSync('token'),version=this._version=(this._version||0)+1
      this.loggedIn=!!owner;this.identityLoading=!!owner;this.identityError=''
      if(this._owner!==owner){this.permissions=[];this.rows=[];this.types=[];this.total=0;this.page=0;this.more=false;this.name=this.type='';this.applied={name:'',type:''};this._owner=owner}
      if(!owner)return
      try{
        const permissions=await documentPermissions(this.$request)
        if(!scopeCurrent(this,version,owner))return
        this.permissions=permissions;this.identityLoading=false
        if(this.allowed('template','list'))await Promise.all([this.load(false),this.loadTypes()])
      }catch(error){if(scopeCurrent(this,version,owner))this.identityError=error.message}
      finally{if(scopeCurrent(this,version,owner))this.identityLoading=false}
    },
    async loadTypes(){const owner=uni.getStorageSync('token');this.typeError='';try{const value=await templateTypes(this.$request,true);if(owner===uni.getStorageSync('token')&&!this._disposed)this.types=value}catch(error){if(owner===uni.getStorageSync('token'))this.typeError=error.message}},
    pickType(event){this.type=this.filterTypes[Number(event.detail.value)].value},
    query(){if(this.loading||this.pending)return;this.applied={name:this.name.trim(),type:this.type};this.rows=[];this.total=0;return this.load(false)},reset(){if(this.loading||this.pending)return;this.name=this.type='';return this.query()},
    async load(append=false){
      if(this.loading||!this.allowed('template','list'))return
      const version=this._version,owner=uni.getStorageSync('token'),page=append?this.page+1:1
      this.loading=true;this.error=''
      try{const result=await templateList(this.$request,{...this.applied,pageNum:page,pageSize:20});if(!scopeCurrent(this,version,owner))return
        this.rows=append?this.rows.concat(result.rows):result.rows;this.total=Number(result.total)||0;this.page=page;this.more=page*20<this.total
      }catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.loading=false}
    },
    edit(row,upload=false){uni.navigateTo({url:'/pages/templates/edit'+(row?'?id='+encodeURIComponent(row.id):upload?'?upload=1':'')})},
    use(row){try{offerTemplate(uni,row);this.openDraft()}catch(error){this.actionError=error.message}},
    async changeStatus(row){
      if(this.pending||!this.allowed('template','edit'))return
      const version=this._version,owner=uni.getStorageSync('token');this.pending=row.id;this.actionError=''
      try{
        if(!await confirmAction(uni,row.status==='0'?'停用此模板？':'启用此模板？',row.status==='0'?'停用后不再供新起草选择，已有文书及原件版本保留。':'启用后仍须分类启用、字段已确认，才可用于新起草。'))return
        if(!scopeCurrent(this,version,owner))return
        const fresh=await templateDetail(this.$request,row.id)
        if(!scopeCurrent(this,version,owner))return
        if(fresh.status!==row.status)throw new Error('模板状态已变化，请刷新核对')
        await saveTemplate(this.$request,{...fresh,status:row.status==='0'?'1':'0'},true)
        if(scopeCurrent(this,version,owner))await this.load(false)
      }catch(error){if(scopeCurrent(this,version,owner))this.actionError=error.message}
      finally{if(scopeCurrent(this,version,owner))this.pending=''}
    },
    async remove(row){
      if(this.pending||!this.allowed('template','remove'))return
      const version=this._version,owner=uni.getStorageSync('token');this.pending=row.id;this.actionError=''
      try{if(!await confirmAction(uni,'删除“'+row.name+'”？','删除不可撤销。该模板不再用于新起草，历史文书版本保留。','确认删除'))return
        if(!scopeCurrent(this,version,owner))return
        await removeManaged(this.$request,'template',row.id)
        if(scopeCurrent(this,version,owner))await this.load(false)
      }catch(error){if(scopeCurrent(this,version,owner))this.actionError=error.message}
      finally{if(scopeCurrent(this,version,owner))this.pending=''}
    }
  }
}
</script>
<style lang="scss">@import '@/common/document-workspace.scss';</style>
