<template>
  <view class="doc-page">
    <text class="doc-title">模板分类</text><text class="doc-caption">先归类模板，再用于文书起草</text>
    <view v-if="loading" class="doc-note">正在加载分类…</view>
    <view v-if="error" class="doc-error">{{error}}<button v-if="!editing" @click="initialize">重试</button></view>
    <view v-if="ready&&!allowed('list')" class="doc-panel doc-empty">暂无分类管理权限</view>
    <template v-if="ready&&allowed('list')">
      <view v-if="!editing" class="doc-actions"><input v-model="name" class="doc-input" placeholder="搜索分类名称" aria-label="搜索分类名称" @confirm="load" placeholder-style="color:#606266;font-size:16px;"/><button :disabled="busy" @click="load">查询</button><button v-if="allowed('add')" class="primary" :disabled="busy" @click="edit()">新增分类</button></view>
      <view v-if="editing" class="doc-panel">
        <text class="doc-panel-title">{{form.id?'编辑分类':'新增分类'}}</text>
        <label class="doc-label">分类名称{{form.builtin?'（系统内置，不可改名）':''}}</label><input v-model="form.name" class="doc-input" :disabled="busy||form.builtin" maxlength="50" aria-label="分类名称"/>
        <label class="doc-label">排序号</label><input v-model="form.sortOrder" class="doc-input" type="number" :disabled="busy" aria-label="分类排序号"/>
        <view class="doc-row doc-label"><text>启用分类</text><switch :checked="form.status==='0'" :disabled="busy" @change="form.status=$event.detail.value?'0':'1'"/></view>
        <label class="doc-label">备注（选填）</label><textarea v-model="form.remark" class="doc-input" aria-label="分类备注" :disabled="busy" maxlength="500"/>
        <view class="doc-actions"><button class="primary" :loading="busy" :disabled="busy" @click="save">保存分类</button><button :disabled="busy" @click="cancel()">取消</button></view>
      </view>
      <view v-else class="doc-panel">
        <view v-for="row in rows" :key="row.id" class="doc-record">
          <view class="doc-row"><text class="doc-record-title">{{row.name}}</text><text class="doc-tag">{{row.status==='0'?'启用':'停用'}}</text></view>
          <text class="doc-muted">{{row.builtin?'系统内置':'自建分类'}} · 排序 {{row.sortOrder}}</text>
          <text v-if="row.remark" class="doc-muted">{{row.remark}}</text>
          <view class="doc-actions"><button v-if="allowed('edit')" :disabled="busy" @click="edit(row)">编辑</button><button v-if="allowed('remove')&&!row.builtin" :disabled="busy" @click="remove(row)">删除</button></view>
        </view>
        <view v-if="!loading&&!rows.length" class="doc-empty">暂无匹配分类</view>
      </view>
    </template>
  </view>
</template>
<script>
import { documentPermissions,canManage,categoryList,saveCategory,removeManaged } from '@/common/api/documents'
import { validateMetadata,confirmAction,scopeCurrent } from '@/common/document-workflow'
export default {
  data(){return{permissions:[],ready:false,loading:false,busy:false,error:'',name:'',rows:[],editing:false,form:{},baseline:''}},
  onShow(){this._disposed=false;this.initialize()},onUnload(){this._disposed=true;this._version=(this._version||0)+1},
  onBackPress(){if(this.busy)return true;if(this.editing&&JSON.stringify(this.form)!==this.baseline&&!this._allowLeave){this.cancel(true);return true}return false},
  methods:{
    allowed(action){return canManage(this.permissions,'category',action)},
    async initialize(){
      const owner=uni.getStorageSync('token'),version=this._version=(this._version||0)+1
      if(this._owner&&this._owner!==owner){this.rows=[];this.editing=false;this.form={};this.permissions=[];this.busy=false;this.ready=false}this._owner=owner
      if(!owner){uni.navigateTo({url:'/pages/h5/login'});return}
      this.loading=true;this.error=''
      try{const permissions=await documentPermissions(this.$request);if(!scopeCurrent(this,version,owner))return;this.permissions=permissions;this.ready=true;if(this.allowed('list'))await this.load()}
      catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.loading=false}
    },
    async load(){const owner=uni.getStorageSync('token'),version=this._version;this.loading=true;this.error='';try{const rows=await categoryList(this.$request,this.name.trim());if(scopeCurrent(this,version,owner))this.rows=rows}catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}finally{if(scopeCurrent(this,version,owner))this.loading=false}},
    edit(row){if(!this.allowed(row?'edit':'add')||this.busy)return;this.form=row?{...row}:{name:'',sortOrder:0,status:'0',builtin:false,remark:''};this.editing=true;this.error='';this.baseline=JSON.stringify(this.form)},
    async cancel(back=false){if(this.busy)return;if(JSON.stringify(this.form)!==this.baseline&&!await confirmAction(uni,'放弃未保存的分类？','当前分类填写尚未保存。','放弃填写'))return;this.editing=false;this.error='';if(back){this._allowLeave=true;uni.navigateBack()}},
    async save(){
      if(this.busy||!this.allowed(this.form.id?'edit':'add'))return
      this.error=validateMetadata(this.form,true);if(this.error)return
      const owner=uni.getStorageSync('token'),version=this._version;this.busy=true
      try{await saveCategory(this.$request,{...this.form,name:this.form.name.trim(),sortOrder:Number(this.form.sortOrder)});if(!scopeCurrent(this,version,owner))return;this.baseline=JSON.stringify(this.form);this.editing=false;await this.load()}
      catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.busy=false}
    },
    async remove(row){
      if(this.busy||row.builtin||!this.allowed('remove'))return
      const owner=uni.getStorageSync('token'),version=this._version;this.busy=true;this.error=''
      try{if(!await confirmAction(uni,'删除“'+row.name+'”？','删除不可撤销；仍有关联模板时服务端会拒绝删除。','确认删除'))return;if(!scopeCurrent(this,version,owner))return;await removeManaged(this.$request,'category',row.id);if(scopeCurrent(this,version,owner))await this.load()}
      catch(error){if(scopeCurrent(this,version,owner))this.error=error.message}
      finally{if(scopeCurrent(this,version,owner))this.busy=false}
    }
  }
}
</script>
<style lang="scss">@import '@/common/document-workspace.scss';</style>
