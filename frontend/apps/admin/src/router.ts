import { createRouter, createWebHistory } from 'vue-router';
import SupplierMasterPage from './views/SupplierMasterPage.vue';
import ComingSoonPage from './views/ComingSoonPage.vue';
export default createRouter({history:createWebHistory(),routes:[{path:'/',redirect:'/suppliers/master'},{path:'/suppliers/master',component:SupplierMasterPage,meta:{title:'供应商档案'}},{path:'/coming-soon',component:ComingSoonPage,meta:{title:'功能建设中'}},{path:'/:pathMatch(.*)*',component:ComingSoonPage,meta:{title:'功能建设中'}}]});
