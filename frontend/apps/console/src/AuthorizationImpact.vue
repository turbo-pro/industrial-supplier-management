<script setup lang="ts">
import type {components} from '@ism/api-client';
defineProps<{impact?:components['schemas']['AuthorizationImpact']|null}>();
const kinds:Record<string,string>={MENU:'页面目录',ACTION:'操作权限',FIELD:'字段权限'};
</script>
<template><section class="authorization-impact" aria-label="菜单与操作授权影响"><h4>菜单与操作授权影响</h4>
<template v-if="impact"><p>套餐能力失效：{{impact.removed.menus}} 个页面目录、{{impact.removed.actions}} 个操作权限、{{impact.removed.fields}} 个字段权限。</p><p>套餐能力恢复：{{impact.added.menus}} 个页面目录、{{impact.added.actions}} 个操作权限、{{impact.added.fields}} 个字段权限。</p>
<p>这是预览时刻的活动目录比较，不含租户账号或业务正文。实际菜单与操作仍需角色授权、父菜单和数据范围；恢复套餐不自动授予角色。基础系统、公共、消息和工作流权限不随套餐撤回。</p>
<p v-if="impact.total===0">本次没有活动页面目录或权限能力变化。</p>
<details v-else><summary>查看目录影响明细（共 {{impact.total}} 项）</summary><p v-if="impact.truncated" role="status">仅展示前 {{impact.limit}} 项，合计数量包含未展示项。请按失效模块复核完整授权目录。</p><div class="impact-scroll" tabindex="0" aria-label="授权目录影响明细滚动区域"><table><thead><tr><th>变化</th><th>模块</th><th>类型</th><th>名称</th><th>编码</th></tr></thead><tbody><tr v-for="item in impact.items" :key="`${item.kind}:${item.code}`"><td>{{item.change==='REMOVED'?'失效':'恢复'}}</td><td>{{item.moduleCode}}</td><td>{{kinds[item.kind]??item.kind}}</td><td>{{item.name}}</td><td>{{item.code}}</td></tr></tbody></table></div></details>
<p>提交会重新校验当前权限、订阅版本及额度。未来计划的实际菜单以生效时的目录和角色为准；此处未模拟工作流实例处理、全部资源用量或数据保留策略。</p></template>
<p v-else role="alert">授权目录影响尚未读取，请重新预览；不要将缺失信息当作无变化。</p></section></template>
<style scoped>.authorization-impact{padding:14px;margin:16px 0;border:1px solid #c8d2da;border-radius:6px;background:#f6f9fa;min-width:0}.authorization-impact p{line-height:1.7;overflow-wrap:anywhere}summary{cursor:pointer;min-height:44px;display:list-item;padding:10px 0;box-sizing:border-box}.impact-scroll{max-width:100%;max-height:420px;overflow:auto}.impact-scroll table{width:100%;min-width:680px;border-collapse:collapse}.impact-scroll td,.impact-scroll th{padding:10px;text-align:left;border-bottom:1px solid #d5dfe5;overflow-wrap:anywhere}summary:focus-visible,.impact-scroll:focus-visible{outline:2px solid #087c82;outline-offset:3px}</style>
