import { createRouter, createWebHistory } from 'vue-router';
import SupplierMasterPage from './views/SupplierMasterPage.vue';
import SupplierAdmissionPage from './views/SupplierAdmissionPage.vue';
import SupplierQualificationPage from './views/SupplierQualificationPage.vue';
import ComingSoonPage from './views/ComingSoonPage.vue';
export default createRouter({history:createWebHistory(),routes:[{path:'/',redirect:'/suppliers/master'},{path:'/suppliers/master',component:SupplierMasterPage,meta:{title:'供应商档案'}},{path:'/suppliers/admissions',component:SupplierAdmissionPage,meta:{title:'供应商准入'}},{path:'/suppliers/qualifications',component:SupplierQualificationPage,meta:{title:'资质证照'}},{path:'/coming-soon',component:ComingSoonPage,meta:{title:'功能建设中'}},{path:'/:pathMatch(.*)*',component:ComingSoonPage,meta:{title:'功能建设中'}}]});
