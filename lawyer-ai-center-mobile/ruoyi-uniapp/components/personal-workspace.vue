<template>
  <view class="mine">
    <button v-if="!loggedIn" @click="login">登录后查看案件与历史</button>
    <template v-else>
      <usage-summary :refresh-key="usageVersion" /><work-timer :key="$store.state.token" :refresh-key="usageVersion" />
      <case-reminders :key="$store.state.token" :refresh-key="usageVersion" />
      <view class="card"><button @click="cases">我的案件</button></view>
      <view v-for="kind in ['review']" :key="kind" class="card">
        <text class="heading">{{ kind==='consult' ? '我的咨询历史' : '我的审查历史' }}</text>
        <text v-if="loading[kind]" class="empty">正在加载历史…</text>
        <view v-for="record in records[kind]" :key="record.id" class="record" @click="open(record,kind)"><view class="record-main"><text>{{ record.readableTitle || titleFor(record.sessionTitle||'未命名会话') }}</text><text class="preview">{{ record.preview || '暂无消息，点击继续' }}</text></view><text v-if="record.caseId" @click.stop="openCase(record.caseId)">所属案件 ›</text><text>›</text></view>
        <text v-if="!records[kind].length && !loading[kind] && !errors[kind]" class="empty">暂无记录，可从咨询或快审页开始</text>
        <button v-if="!records[kind].length && !loading[kind] && !errors[kind]" size="mini" @click="startEmpty(kind)">{{kind==='review'?'上传第一份合同':'开始法律咨询'}}</button>
        <text v-if="errors[kind]" class="error">{{ errors[kind] }}</text>
        <button v-if="hasMore[kind] || errors[kind]" :loading="loading[kind]" :disabled="loading[kind]" @click="loadRecords(kind,errors[kind] ? retryMore[kind] : true)">{{ errors[kind] ? '重试' : '加载更多' }}</button>
      </view>
    </template>
    <view class="card"><button @click="help">帮助与反馈</button></view>
    <button v-if="loggedIn" class="logout" :loading="logoutBusy" :disabled="logoutBusy" @click="confirmLogout">退出登录</button>

  </view>
</template>
<script>
import { normalizeSessionId } from '@/common/api/session';
import {messageTitle} from '@/common/message-presentation';
import { withSessionPreviews } from '@/common/session-preview';
import CaseReminders from '@/components/case-reminders.vue';
import { getLegalContext, legalPageTop } from '@/common/api/legal-entry';
export default {
  components:{CaseReminders},
  props:{active:{type:Boolean,default:false}},
  data() { return { topSpacing:legalPageTop(uni),user:{},loggedIn:false,usageVersion:0,logoutBusy:false,records:{consult:[],review:[]},loading:{consult:false,review:false},errors:{consult:'',review:''},pages:{consult:0,review:0},hasMore:{consult:false,review:false},retryMore:{consult:false,review:false} }; },
  mounted(){if(this.active)this.refresh()},
  beforeDestroy(){this._version=(this._version||0)+1},
  watch:{active(value){if(value)this.refresh()}},
  methods: {
    titleFor:messageTitle,
    login() { uni.navigateTo({url:'/pages/h5/login'}); },
    help() { uni.navigateTo({url:'/pages/content/help'}); },
    openCase(id) { uni.navigateTo({url:'/pages/cases/index?id='+encodeURIComponent(id)}); },
    cases() { uni.navigateTo({url:'/pages/cases/index'}); },
    startEmpty(kind){if(kind==='review')uni.navigateTo({url:'/pages/index/quick-review'});else this.$emit('consult');},
    async refresh() {
      this._version=(this._version||0)+1; this.user=uni.getStorageSync('userInfo')||{};
      this._owner=String(this.user.userId||'');this._token=uni.getStorageSync('token');this.loggedIn=Boolean(this._owner&&this._token);this.usageVersion++;this.topSpacing=legalPageTop(uni);
      this.records={consult:[],review:[]};this.pages={consult:0,review:0};this.loading={consult:false,review:false};this.errors={consult:'',review:''};this.hasMore={consult:false,review:false};
      if(this.loggedIn)await Promise.all([this.loadRecords('review'),this.loadProfile(this._version)]);
    },
    current(version) { return this._version===version&&this._token===uni.getStorageSync('token')&&this._owner===String((uni.getStorageSync('userInfo')||{}).userId||''); },
    async loadProfile(version) {
      try {const res=await this.$request('/system/user/profile','GET',{}, {'content-type':'application/json'});
        if(this.current(version)&&res.code===200&&res.data&&res.data.user){this.user=res.data.user;uni.setStorageSync('userInfo',this.user);this.$store.commit('login',{userInfo:this.user,token:this._token});}
      }catch(_){/* Keep cached identity when profile cannot refresh. */}
    },
    async loadRecords(kind,more=false) {
      if(!this.loggedIn||this.loading[kind])return;
      const version=this._version,page=more?this.pages[kind]+1:1;this.loading[kind]=true;this.errors[kind]='';
      try {
        const res=await this.$request('/system/session/list','GET',{pageNum:page,pageSize:20,reviewOnly:kind==='review',orderByColumn:'id',isAsc:'desc'}, {'content-type':'application/json'});
        if(!this.current(version))return;
        if(res.code!==200||!Array.isArray(res.rows))throw new Error(res.msg||'历史加载失败');
        const rows=await withSessionPreviews(res.rows.filter(r=>normalizeSessionId(r.id)&&(kind==='review'||!/^合同(审查|快审)/.test(r.sessionTitle||''))),this.$request,this._owner);
        if(!this.current(version))return;
        const merged=more?this.records[kind].concat(rows):rows,seen={};this.records[kind]=merged.filter(r=>{const id=String(r.id);if(seen[id])return false;seen[id]=true;return true;});
        this.pages[kind]=page;this.hasMore[kind]=res.rows.length===20&&page*20<Number(res.total);
      }catch(e){if(this.current(version)){this.errors[kind]=e.message||'历史加载失败，请重试';this.retryMore[kind]=more;}}
      finally{if(this.current(version))this.loading[kind]=false;}
    },
    open(record,kind) {
      if(!normalizeSessionId(record.id))return;
      if(kind==='review'){uni.navigateTo({url:'/pages/index/quick-review?sessionId='+encodeURIComponent(record.id)});return;}
      const context=getLegalContext(uni,this._owner,record.id);let url='/pages/index/index?sessionId='+encodeURIComponent(record.id);
      if(context)url+='&agentId='+encodeURIComponent(context.agentId)+'&appName='+encodeURIComponent(context.appName);uni.navigateTo({url});
    },
    confirmLogout(){if(!this.logoutBusy)uni.showModal({title:'退出登录',content:'确定退出当前账号吗？',success:r=>{if(r.confirm)this.logout();}});},
    async logout(){
      if(this.logoutBusy)return;this.logoutBusy=true;const token=uni.getStorageSync('token');let failed=false;
      try{const res=await this.$request('/auth/logout','POST',{}, {'content-type':'application/json'});failed=Number(res.code)!==200;}catch(_){failed=true;}
      if(uni.getStorageSync('token')!==token){this.logoutBusy=false;return;}
      this._version=(this._version||0)+1;
      const keys=(uni.getStorageInfoSync().keys||[]).filter(key=>/^(mobileChatSession:|mobileLegalContexts:|mobileLegalFirstMessage:)/.test(key));
      keys.concat(['token','clientId','userInfo']).forEach(key=>uni.removeStorageSync(key));this.$store.commit('logout');this.user={};this.loggedIn=false;this.records={consult:[],review:[]};this.logoutBusy=false;
      uni.reLaunch({url:'/pages/h5/login'});if(failed)uni.showToast({title:'网络异常，已清除本机登录状态',icon:'none'});
    }
  }
};
</script>
<style scoped>
.record-main{min-width:0;flex:1}.record-main>text{display:block}.record-main .preview{font-size:14px;color:var(--brand-muted);margin-top:8rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mine{box-sizing:border-box;min-height:100vh;padding:40rpx 30rpx calc(50rpx + env(safe-area-inset-bottom));background:var(--brand-background);color:var(--brand-text)}.identity{display:flex;gap:24rpx;align-items:center;margin:10rpx 0 30rpx}.identity image{width:104rpx;height:104rpx;border-radius:50%}.title,.caption,.heading,.empty,.error{display:block}.title{font-size:20px;font-weight:600}.caption,.empty{color:var(--brand-muted);font-size:14px;margin-top:14rpx}.card{padding:26rpx;margin:22rpx 0;background:white;border-radius:22rpx}.heading{font-size:16px;font-weight:600;margin-bottom:16rpx}.record{display:flex;justify-content:space-between;gap:12rpx;padding:24rpx 0;border-bottom:1px solid #eee;font-size:16px}.record text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mine button{margin-top:16rpx;font-size:16px;background:#fff}.error{color:var(--brand-danger);margin:14rpx 0;font-size:16px}.mine .logout{margin-top:32rpx;color:var(--brand-danger)}
</style>
