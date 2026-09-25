import { createRouter, createWebHistory } from 'vue-router';
import SupplierMasterPage from './views/SupplierMasterPage.vue';
import SupplierAdmissionPage from './views/SupplierAdmissionPage.vue';
import SupplierQualificationPage from './views/SupplierQualificationPage.vue';
import ContractProjectPage from './views/ContractProjectPage.vue';
import SupplierResourcePage from './views/SupplierResourcePage.vue';
import SafetyIssuePage from './views/SafetyIssuePage.vue';
import ComingSoonPage from './views/ComingSoonPage.vue';
export default createRouter({history:createWebHistory(),routes:[{path:'/',redirect:'/suppliers/master'},{path:'/suppliers/master',component:SupplierMasterPage,meta:{title:'供应商档案'}},{path:'/suppliers/admissions',component:SupplierAdmissionPage,meta:{title:'供应商准入'}},{path:'/suppliers/qualifications',component:SupplierQualificationPage,meta:{title:'资质证照'}},{path:'/projects/contracts',component:ContractProjectPage,meta:{title:'合同台账',tab:'contracts'}},{path:'/projects/ledger',component:ContractProjectPage,meta:{title:'项目台账',tab:'projects'}},{path:'/resources/persons',component:SupplierResourcePage,meta:{title:'供应商人员',tab:'persons'}},{path:'/resources/assets',component:SupplierResourcePage,meta:{title:'车辆设备',tab:'assets'}},{path:'/safety/issues',component:SafetyIssuePage,meta:{title:'安全隐患整改'}},{path:'/coming-soon',component:ComingSoonPage,meta:{title:'功能建设中'}},{path:'/:pathMatch(.*)*',component:ComingSoonPage,meta:{title:'功能建设中'}}]});
