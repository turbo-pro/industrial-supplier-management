import { createRouter, createWebHistory } from 'vue-router';
import SupplierMasterPage from './views/SupplierMasterPage.vue';
import SupplierBlacklistPage from './views/SupplierBlacklistPage.vue';
import SupplierAdmissionPage from './views/SupplierAdmissionPage.vue';
import SupplierQualificationPage from './views/SupplierQualificationPage.vue';
import ContractProjectPage from './views/ContractProjectPage.vue';
import SupplierResourcePage from './views/SupplierResourcePage.vue';
import SafetyIssuePage from './views/SafetyIssuePage.vue';
import SafetyCredentialPage from './views/SafetyCredentialPage.vue';
import SafetyAttendancePage from './views/SafetyAttendancePage.vue';
import QualityNonconformancePage from './views/QualityNonconformancePage.vue';
import PerformanceEvaluationPage from './views/PerformanceEvaluationPage.vue';
import ComingSoonPage from './views/ComingSoonPage.vue';
import TenantSettingPage from './views/TenantSettingPage.vue';
import UserManagementPage from './views/UserManagementPage.vue';
import InboxPage from './views/InboxPage.vue';
import ExitTaskPage from './views/ExitTaskPage.vue';
import ExitMonitorPage from './views/ExitMonitorPage.vue';
import BusinessSearchPage from './views/BusinessSearchPage.vue';
export default createRouter({history:createWebHistory(),routes:[
  {path:'/',redirect:'/suppliers/master'},
  {path:'/messages/inbox',component:InboxPage,meta:{title:'我的消息'}},
  {path:'/search',component:BusinessSearchPage,meta:{title:'业务全局搜索'}},
  {path:'/resources/search',component:BusinessSearchPage,meta:{title:'业务全局搜索'}},
  {path:'/suppliers/exit-tasks',component:ExitTaskPage,meta:{title:'我的退出待办'}},
  {path:'/suppliers/exit-monitor',component:ExitMonitorPage,meta:{title:'退出处置监控'}},
  {path:'/suppliers/master',component:SupplierMasterPage,meta:{title:'供应商档案'}},
  {path:'/suppliers/blacklist',component:SupplierBlacklistPage,meta:{title:'供应商黑名单'}},
  {path:'/suppliers/admissions',component:SupplierAdmissionPage,meta:{title:'供应商准入'}},
  {path:'/suppliers/qualifications',component:SupplierQualificationPage,meta:{title:'资质证照'}},
  {path:'/projects/contracts',component:ContractProjectPage,meta:{title:'合同台账',tab:'contracts'}},
  {path:'/projects/ledger',component:ContractProjectPage,meta:{title:'项目台账',tab:'projects'}},
  {path:'/resources/persons',component:SupplierResourcePage,meta:{title:'供应商人员',tab:'persons'}},
  {path:'/resources/assets',component:SupplierResourcePage,meta:{title:'车辆设备',tab:'assets'}},
  {path:'/safety/issues',component:SafetyIssuePage,meta:{title:'安全隐患整改'}},
  {path:'/safety/credentials',component:SafetyCredentialPage,meta:{title:'培训与作业凭证'}},
  {path:'/safety/attendance',component:SafetyAttendancePage,meta:{title:'现场出入'}},
  {path:'/quality/nonconformances',component:QualityNonconformancePage,meta:{title:'质量不符合项'}},
  {path:'/performance/evaluations',component:PerformanceEvaluationPage,meta:{title:'供应商绩效'}},
  {path:'/system/settings',component:TenantSettingPage,meta:{title:'租户配置'}},
  {path:'/system/users',component:UserManagementPage,meta:{title:'用户管理'}},
  {path:'/coming-soon',component:ComingSoonPage,meta:{title:'功能建设中'}},
  {path:'/:pathMatch(.*)*',component:ComingSoonPage,meta:{title:'功能建设中'}}
]});
