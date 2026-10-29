# B1-07/12 · SITE-CONTEXT 请求级场站令牌与选择预览

2026-10-29；承接个人首选闭环，本切片验收通过。范围仅签发/校验请求级场站上下文及预览，不宣称全业务切换完成。

POST /site-context 请求siteId可选：显式选择→有效个人首选→唯一有效候选；原首选已失效则要求明确选择，多候选且无首选不得猜测。需iam:site:view，复用组织归属与场站数据范围交集。返回site/contextToken/expiresAt/selectionReason；令牌仅内存使用，不进存储/URL/日志或普通业务客户端。

GET /site-context 须同一有效Bearer登录凭据及X-Site-Context。令牌独立签名用途/issuer/audience，绑定租户、用户及原Bearer摘要，默认5分钟且不超过原登录令牌到期；每请求重新核对会话、操作权限、组织归属、范围与场站/祖先有效。用作Bearer、换登录、换用户/租户、篡改、过期或撤权均拒绝。现有JWT入口仅对这个已接入GET开启请求级SiteContext；其他接口携带场站头明确422拒绝，不静默忽略筛选条件。

CTX-001显式/首选/唯一选择和多候选歧义；CTX-002签名/用途/身份/当前登录绑定及严格到期上限；CTX-003撤权/成员变更/祖先停用即时拒绝及请求结束清理；CTX-004登录退出/新登录绑定、未接入接口拒绝、无头旧入口回归；CTX-005签发审计不记录令牌，失败不交付；CTX-006真实管理端预览选取→服务端校验→清除，未知失败不假报全局切换。

无迁移、无新增部署服务。改公共请求上下文、IAM令牌/签发/认证入口、契约客户端和个人首选页内独立预览组件、针对性单测/真实HTTP与浏览器。保留用户视觉/打印草稿；共享契约仅暂存本轮。回滚停止签发并移除页面预览，旧应用不能消费该请求头；不删除历史审计、不改首选/组织/业务归属，不repair V25。

最近使用、登录自动进入UI、全局切换器和业务过滤继续后续；预览不调用普通业务接口或替换当前组织，不通过技术令牌伪装业务闭环。

## 实现与部署约束

场站签名密钥由现有JWT密钥按独立用途派生，issuer/audience与登录令牌分开。`ism.security.site-context-ttl`默认PT5M，启动校验1秒至15分钟，签发期限还受原Bearer的exp限制。验证无时间宽限，绝不把场站令牌当Bearer。新登录或刷新后的不同Bearer须重新签发，清除预览只是丢弃页面内存，不是吊销复制出的令牌；所有使用仍受父登录、到期和实时授权约束。节点须使用一致JWT配置和同步时钟，不据本轮结果宣称混版/时钟故障发布Gate完成。

GET消费者在认证上下文内打开SiteContext并在finally关闭，校验租户/用户匹配。显式路由白名单目前仅GET/HEAD /api/site-context；租户过滤器和MVC边界同时拒绝旧入口及Console/Portal入口误带场站头。无头原接口保持原有角色/数据规则；没有任何普通业务客户端自动附加令牌。签发按安全事件审计，只存场站/选择来源/期限，不存Bearer、指纹或场站令牌。不会写首选、最近使用、当前组织或业务归属。

管理端在首选页增加独立预览卡：多候选无首选时要求明确选择，获取后单独向服务端校验，可清除；过期或校验失败清除已校验状态。仅组件内存保存令牌、不进sessionStorage/localStorage/URL，不假报全局已切换。最近使用和实际登录自动进入须待业务工作区接入，不以预览行为伪造使用记录。

## 实际验收

最终隔离MySQL命令（JDK21）`./mvnw -q -pl backend/ism-bootstrap -am -Pintegration-test '-Dit.test=SiteContextIT,SitePreferenceIT' -Dfailsafe.failIfNoSpecifiedTests=false verify` 退出0：上下文6/6、原首选9/9，共15项。真实HTTP覆盖显式/有效首选/唯一候选、多候选与无效首选拒绝、不同登录/用户/租户、令牌用途互换、篡改、超过长度/重复头、退出登录、范围/成员/操作权限/祖先撤回、旧入口与有效Console身份附头422、无头旧入口正常，以及审计失败500不交付凭据。

令牌单测4/4、请求作用域1/1；最后定向 `-pl backend/ism-iam -am '-Dtest=SiteContextTokensTest,SiteContextScopeTest' -Dsurefire.failIfNoSpecifiedTests=false test` 退出0。包含正确用途签名的正向控制后，错误issuer/audience仍拒绝，避免只测坏签名；另有严格到期边界、父到期上限/不足一秒拒绝、原始Bearer绑定、配置边界及嵌套恢复/结束清理。正常Maven API/架构单测通过。最初测试依赖装配缺失已修正：公共类的单测放到已有测试依赖的IAM消费者模块，MVC切片补模拟新增服务；未将生产依赖改成可选或关闭校验。

管理端类型与构建、客户端10/10、暂存OpenAPI单独生成与暂存类型逐字节一致通过。模拟UI5/5覆盖校验/本地清除、撤权清除、计时过期、既有首选只读/不可用/读取失败与四档宽度和焦点；HEAD加本轮暂存源码的独立临时副本同样5/5，排除用户未提交视觉/打印草稿依赖，临时端口/副本清理，375截图已查看。

真实Chrome `node scripts/verify-reminder-e2e.mjs --site-context` 退出0：无首选多候选拒绝→明确选择→签发/校验→旧接口附头422且无头仍返回原总部→不入浏览器存储→清除→重新获取→隔离夹具撤回范围→校验403并移除已校验状态，最终库1/1。范围撤回是自建测试夹具控制的SQL变更，未伪称产品允许编辑内置管理员授权。最终仅两次签发审计、首选表为空、全部iam_user_context保持不变，桌面/375/撤权截图已查看。

原 `--site-preference` 真实浏览器及最终库回归1/1通过；新卡引入第二个下拉框后，旧脚本选择器收敛到原choice-form，不靠强制点击。签发本身不使用业务幂等回执，重试可能新增安全签发事件，不保证只有一个令牌，但不改变业务数据。

证据目录`output/playwright/site-context/`：context-tests.txt、preference-regression.txt、token-unit-tests.txt、request-scope-unit-tests.txt、protocol-unit-tests.log、frontend-build.log、client-tests.log、ui-tests.log、staged-contract-check.txt、browser-database-assertions.txt、preference-browser-regression-tests.txt、desktop.png/mobile.png/revoked.png、clean-ui-evidence/。真实运行原始日志`site-context-e2e-1793280941693/`与`site-preference-e2e-1793281258779/`。场景已加既有GitHub Actions矩阵，未宣称云端CI已运行。测试资源全部自清理，Docker仅剩原开发MySQL，旧库/V25未操作。

下一项SITE-BUSINESS从项目类现场业务接入，逐项完成列表、详情、新增/修改、状态、搜索、导出/打印和附件的上下文与原业务授权交集；未接入前不得放宽白名单。本轮不标完整SITE-CONTEXT、SITE-AT-002/004或B1/GAP-02 Gate完成。
