<template>
  <view class="mobile-shell">
    <view class="mobile-header" :style="{paddingTop:topInset+'px',paddingRight:rightInset+'px'}" :aria-hidden="drawerOpen?'true':'false'">
      <button class="shell-icon menu-trigger" aria-label="打开菜单" tabindex="0" @click="openDrawer"><view class="menu-lines"><view/><view/></view></button>
      <text class="shell-title">{{title}}</text>
      <button v-if="rightInset<30" class="shell-icon new-consult" aria-label="新咨询" tabindex="0" @click="$emit('new-consult')"><image src="/static/tabbar/legal-drafting.png"/></button>
    </view>
    <view class="mobile-body" :aria-hidden="drawerOpen?'true':'false'"><slot/></view>
    <view v-if="drawerOpen" class="drawer-layer">
      <view class="drawer-shade" aria-label="关闭菜单" @click="closeDrawer" @touchmove.stop.prevent/>
      <view class="mobile-drawer" role="dialog" aria-modal="true" aria-label="菜单与咨询历史" tabindex="-1" :style="{paddingTop:topInset+'px'}">
        <view class="drawer-heading"><view class="drawer-brand"><image src="/static/brand/logo-transparent.png" mode="aspectFit" aria-label="与律行 Logo"/><text>与律行</text></view><button class="shell-icon" aria-label="关闭菜单" tabindex="0" @click="closeDrawer">×</button></view>
        <scroll-view class="drawer-content" scroll-y @scrolltolower="loadMore">
        <view class="drawer-navigation">
          <button class="drawer-nav" :class="{current:current==='consult'}" tabindex="0" @click="choose('consult')">法律咨询<text v-if="current==='consult'" class="current-label">当前</text></button>
          <text class="drawer-group">工作台</text>
          <button class="drawer-nav" :class="{current:current==='templates'}" tabindex="0" @click="choose('templates')">模板管理<text v-if="current==='templates'" class="current-label">当前</text></button>
          <button class="drawer-nav" :class="{current:current==='drafting'}" tabindex="0" @click="choose('drafting')">文书起草<text v-if="current==='drafting'" class="current-label">当前</text></button>
          <button class="drawer-nav" tabindex="0" @click="choose('review')">合同快审</button>
          <button class="drawer-nav" tabindex="0" @click="choose('cases')">我的案件</button>
        </view>
        <view class="drawer-history-title"><text>咨询历史</text><button tabindex="0" :disabled="sessionLoading" @click="loadSessions(false)">刷新</button></view>
        <view class="drawer-history">
          <text v-if="!loggedIn" class="drawer-hint">登录后查看咨询历史</text>
          <button v-for="session in sessions" :key="session.id" class="drawer-session" tabindex="0" @click="openSession(session)"><text class="drawer-session-title">{{titleFor(session.sessionTitle||'未命名咨询')}}</text><text class="drawer-session-date">{{sessionDate(session)}}</text></button>
          <text v-if="sessionLoading" class="drawer-hint">正在加载…</text>
          <view v-else-if="sessionError" class="drawer-hint drawer-error"><text>{{sessionError}}</text><button tabindex="0" @click="loadSessions(sessionRetryMore)">重试</button></view>
          <text v-else-if="loggedIn&&!sessions.length" class="drawer-hint">暂无咨询记录</text>
          <button v-if="hasMoreSessions&&!sessionLoading&&!sessionError" class="drawer-more" tabindex="0" @click="loadMore">加载更早咨询</button>
        </view>
        </scroll-view>
        <button class="drawer-account" tabindex="0" @click="choose('mine')"><image v-if="user.avatar" :src="user.avatar" mode="aspectFill"/><view v-else class="account-initial">{{(user.nickName||user.userName||'律').slice(0,1)}}</view><view class="account-label"><text>{{user.nickName||user.userName||(loggedIn?'我的账号':'登录与律行')}}</text><text class="drawer-session-date">我的 · 账号与设置</text></view><text>›</text></button>
      </view>
    </view>
  </view>
</template>
<script>
import { getSessionListPage,normalizeSessionId,compareMessageIds } from '@/common/api/session'
import {messageTitle} from '@/common/message-presentation'
import { getLegalContext } from '@/common/api/legal-entry'
import { confirmAction } from '@/common/document-workflow'
export default {
  props:{title:{type:String,default:'与律行'},current:{type:String,default:'consult'},sessionId:{type:String,default:''},busy:{type:Boolean,default:false}},
  data(){return{drawerOpen:false,topInset:0,rightInset:12,user:{},loggedIn:false,sessions:[],sessionLoading:false,sessionError:'',sessionRetryMore:false,sessionPage:0,hasMoreSessions:false}},
  mounted(){
    try{const info=uni.getSystemInfoSync();this.topInset=Number(info.statusBarHeight)||0
      // #ifdef MP-WEIXIN
      const rect=uni.getMenuButtonBoundingClientRect();if(rect&&rect.left)this.rightInset=Math.max(12,info.windowWidth-rect.left+8)
      // #endif
    }catch(_){}
    // #ifdef H5
    this._keydown=event=>this.keydown(event);document.addEventListener('keydown',this._keydown)
    // #endif
  },
  beforeDestroy(){this._disposed=true;this._sessionsVersion=(this._sessionsVersion||0)+1
    // #ifdef H5
    document.removeEventListener('keydown',this._keydown);this.unlockBody()
    // #endif
  },
  methods:{
    titleFor:messageTitle,
    async openDrawer(){
      const user=uni.getStorageSync('userInfo')||{},token=uni.getStorageSync('token')
      if(this._sessionsToken!==token||this._sessionsOwner!==String(user.userId||'')){this.sessions=[];this.sessionPage=0;this.hasMoreSessions=false;this.sessionLoading=false;this._sessionsVersion=(this._sessionsVersion||0)+1}
      this.user=user;this.loggedIn=!!(token&&user.userId);this.drawerOpen=true
      // #ifdef H5
      this._previousFocus=document.activeElement;this._oldOverflow=document.body.style.overflow;document.body.style.overflow='hidden'
      this.$nextTick(()=>{const el=this.$el.querySelector('.mobile-drawer');if(el)el.focus()})
      // #endif
      return this.loadSessions(false)
    },
    closeDrawer(){
      if(!this.drawerOpen)return
      this.drawerOpen=false;this._sessionsVersion=(this._sessionsVersion||0)+1;this.sessionLoading=false
      // #ifdef H5
      this.unlockBody();const focus=this._previousFocus;this.$nextTick(()=>{if(focus&&focus.isConnected)focus.focus()})
      // #endif
    },
    unlockBody(){
      // #ifdef H5
      if(this._oldOverflow!==undefined){document.body.style.overflow=this._oldOverflow;this._oldOverflow=undefined}
      // #endif
    },
    keydown(event){
      // #ifdef H5
      if(!this.$el.contains(event.target))return
      if(this.drawerOpen&&event.key==='Escape'){event.preventDefault();this.closeDrawer();return}
      if(this.drawerOpen&&event.key==='Tab'){
        const list=Array.from(this.$el.querySelectorAll('.mobile-drawer [tabindex="0"],.mobile-drawer button')).filter(el=>!el.hasAttribute('disabled')&&el.getClientRects().length)
        if(!list.length){event.preventDefault();return}
        const at=list.indexOf(document.activeElement)
        if(event.shiftKey&&at<=0){event.preventDefault();list[list.length-1].focus()}
        else if(!event.shiftKey&&(at<0||at===list.length-1)){event.preventDefault();list[0].focus()}
      }
      if(['Enter',' '].includes(event.key)&&event.target.tagName==='UNI-BUTTON'&&!event.target.hasAttribute('disabled')&&event.target.getAttribute('aria-disabled')!=='true'){event.preventDefault();event.target.click()}
      // #endif
    },
    choose(section){this.$emit('navigate',section);this.closeDrawer()},
    loadMore(){if(this.hasMoreSessions&&!this.sessionLoading&&!this.sessionError)return this.loadSessions(true)},
    async loadSessions(more=false){
      const user=uni.getStorageSync('userInfo')||{},owner=String(user.userId||''),token=uni.getStorageSync('token')
      if(!owner||!token){this.sessions=[];this.sessionLoading=false;return}
      if(this.sessionLoading&&this._sessionsOwner===owner&&this._sessionsToken===token)return
      if(this._sessionsOwner!==owner||this._sessionsToken!==token){this.sessions=[];this.sessionPage=0;this.hasMoreSessions=false}
      if(more&&!this.hasMoreSessions)return
      this._sessionsOwner=owner;this._sessionsToken=token;const version=this._sessionsVersion=(this._sessionsVersion||0)+1,pageNum=more?this.sessionPage+1:1
      this.sessionRetryMore=more;this.sessionLoading=true;this.sessionError=''
      try{
        const page=await getSessionListPage(this.$request,{pageNum})
        if(!this.currentOwner(version,owner,token))return
        const sessions=new Map((more?this.sessions:[]).map(item=>[item.id,item]))
        page.rows.filter(item=>!/^合同(审查|快审)|^文书起草/.test(item.sessionTitle||'')).forEach(item=>sessions.set(item.id,item))
        this.sessions=Array.from(sessions.values()).sort((a,b)=>compareMessageIds(b,a));this.sessionPage=pageNum;this.hasMoreSessions=page.hasMore
      }catch(error){if(this.currentOwner(version,owner,token))this.sessionError=(error.message||'咨询历史加载失败')+'，请重试'}
      finally{if(this.currentOwner(version,owner,token))this.sessionLoading=false}
    },
    currentOwner(version,owner,token){return!this._disposed&&version===this._sessionsVersion&&token===uni.getStorageSync('token')&&owner===String((uni.getStorageSync('userInfo')||{}).userId||'')},
    async openSession(session){
      const id=normalizeSessionId(session.id),user=uni.getStorageSync('userInfo')||{},token=uni.getStorageSync('token')
      if(!id||String(user.userId||'')!==this._sessionsOwner||!token||token!==this._sessionsToken)return
      if(this.busy&&!await confirmAction(uni,'切换咨询？','当前正在生成的回复将停止在本页显示，已保存内容可从咨询历史继续。','切换咨询'))return
      if(token!==uni.getStorageSync('token'))return
      const context=getLegalContext(uni,user.userId,id);let url='/pages/index/index?sessionId='+encodeURIComponent(id)
      if(context)url+='&agentId='+encodeURIComponent(context.agentId)+'&appName='+encodeURIComponent(context.appName)
      this.closeDrawer();if(this.sessionId)uni.redirectTo({url});else uni.navigateTo({url})
    },
    sessionDate(session){const value=session.updateTime||session.createTime;return typeof value==='string'?value.replace('T',' ').slice(0,16):'继续咨询'}
  }
}
</script>
<style lang="scss">
.mobile-shell { min-height:100vh; background:var(--brand-surface); color:var(--brand-text); }
.mobile-header { box-sizing:content-box; position:sticky; top:0; z-index:5; height:56px; display:flex; align-items:center; gap:12px; padding-left:10px; background:var(--brand-surface); }
.shell-icon { display:flex; align-items:center; justify-content:center; flex-shrink:0; width:44px; height:44px; margin:0; padding:0; border:0; border-radius:12px; background:transparent; color:var(--brand-text); font-size:30px; line-height:1; }
.shell-icon::after { border:0; }
.shell-icon image { width:22px; height:22px; }
.menu-lines { width:20px; }
.menu-lines view { height:2px; margin:5px 0; border-radius:2px; background:var(--brand-text); }
.menu-lines view:last-child { width:14px; }
.shell-title { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:17px; font-weight:500; }
.drawer-layer { position:fixed; inset:0; z-index:10020; }
.drawer-shade { position:absolute; inset:0; background:rgba(0,0,0,.32); }
.mobile-drawer { box-sizing:border-box; position:absolute; top:0; bottom:0; left:0; display:flex; flex-direction:column; width:86vw; max-width:320px; background:var(--brand-surface); }
.drawer-heading { flex-shrink:0; display:flex; align-items:center; justify-content:space-between; height:56px; padding:0 12px 0 20px; font-size:18px; font-weight:600; }
.drawer-brand {display:flex; align-items:center; gap:10px;}
.drawer-brand image {width:30px; height:30px; flex-shrink:0;}
.drawer-navigation { flex-shrink:0; padding:4px 12px 8px; }
.drawer-nav { display:flex; justify-content:space-between; align-items:center; min-height:44px; margin:2px 0; padding:10px 14px; text-align:left; font-size:15px; font-weight:400; line-height:1.5; border-radius:10px; background:transparent; color:var(--brand-text); }
.drawer-nav::after,.drawer-secondary button::after,.drawer-history-title button::after,.drawer-session::after,.drawer-more::after,.drawer-account::after { border:0; }
.drawer-nav.current { background:var(--brand-background); font-weight:600; }
.current-label { color:var(--brand-muted); font-size:14px; font-weight:400; }
.drawer-group { display:block; padding:16px 14px 6px; color:var(--brand-muted); font-size:14px; }
.drawer-history-title { display:flex; align-items:center; justify-content:space-between; flex-shrink:0; margin:0 20px; padding-top:8px; border-top:1px solid var(--brand-border); font-size:14px; color:var(--brand-muted); }
.drawer-history-title button { min-height:40px; margin:0; padding:8px 0 8px 12px; font-size:14px; line-height:2; background:transparent; }
.drawer-content {flex:1; height:0; min-height:0;}
.drawer-history {padding:0 12px; box-sizing:border-box;}
.drawer-session { display:block; width:100%; min-height:52px; margin:0; padding:9px 14px; text-align:left; background:transparent; line-height:1.5; font-size:14px; border-radius:8px; }
.drawer-session-title { display:block; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.drawer-session-date { display:block; margin-top:3px; font-size:14px; color:var(--brand-muted); font-weight:400; }
.drawer-hint { display:block; padding:12px 14px; font-size:14px; color:var(--brand-muted); line-height:1.8; }
.drawer-error { color:var(--brand-danger-text); }
.drawer-hint button,.drawer-more { min-height:44px; background:transparent; font-size:14px; }
.drawer-account { display:flex; align-items:center; gap:12px; flex-shrink:0; width:100%; box-sizing:border-box; padding:14px 20px calc(14px + env(safe-area-inset-bottom)); margin:0; border-top:1px solid var(--brand-border); border-radius:0; background:var(--brand-surface); text-align:left; line-height:1.5; font-size:14px; }
.drawer-account image,.account-initial { width:36px; height:36px; border-radius:50%; flex-shrink:0; }
.account-initial { text-align:center; line-height:36px; background:var(--brand-background); font-size:16px; }
.account-label { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mobile-header button:active,.mobile-drawer button:active { background:var(--brand-background); }
/* #ifdef H5 */
.mobile-header button:focus-visible,.mobile-drawer button:focus-visible { outline:2px solid var(--brand-link); outline-offset:2px; }
.mobile-drawer:focus { outline:none; }
.mobile-drawer { height:100dvh; }
/* #endif */
</style>
