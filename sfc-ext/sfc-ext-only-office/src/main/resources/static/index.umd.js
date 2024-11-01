(function(e,o){typeof exports=="object"&&typeof module<"u"?o(require("qs"),require("sfc-common"),require("vue"),require("sfc-common/components")):typeof define=="function"&&define.amd?define(["qs","sfc-common","vue","sfc-common/components"],o):(e=typeof globalThis<"u"?globalThis:e||self,o(e.qs,e.SfcCommon,e.Vue,e.Components))})(this,function(e,o,t,s){"use strict";const p=t.defineComponent({name:"OnlyOfficeHelp",components:{MarkdownView:s.MarkdownView}}),m=t.defineComponent({...p,setup(c){const i=`
#### 简单介绍

1. 本插件需要依赖第三方文档服务-ONLYOFFICE
2. 可参考官方文档 [获取ONLYOFFICE服务](https://api.onlyoffice.com/zh/editors/getdocs)
3. 其他小建议：推荐使用 [Docker方式部署](https://helpcenter.onlyoffice.com/installation/docs-community-install-docker.aspx)
4. 基于安全考虑，在非内部网络环境中不建议关闭ONLYOFFICE服务的JWT功能。

    暴露于公网的ONLYOFFICE和咸鱼云的ONLYOFFICE文件保存回调程序将无法验证请求是否合法，存在严重安全风险。

#### Docker方式安装简单案例

> 这里仅作简单示例，详细说明见 [官方文档](https://helpcenter.onlyoffice.com/installation/docs-community-install-docker.aspx)
1. 获取镜像
\`\`\`shell
$ docker pull onlyoffice/documentserver
\`\`\`

2. 创建容器

  - 使用HTTP
  \`\`\`shell
  $ docker run -i -t -d -p 本机端口号:80 --restart=always -e JWT_SECRET=JWT密钥 onlyoffice/documentserver
  \`\`\`

  - 使用HTTP + HTTPS
  \`\`\`shell
  $ docker run -i -t -d -p 本机端口号:80 -p 本机端口号:443 \\
    -v 本机的ONLYOFFICE数据目录:/var/www/onlyoffice/Data \\
    --restart=always -e JWT_SECRET=JWT密钥 \\
    onlyoffice/documentserver
  \`\`\`
  需要确保\`本机的ONLYOFFICE数据目录/certs/onlyoffice.key\`和\`本机的ONLYOFFICE数据目录/certs/onlyoffice.crt\`下存在对应的HTTPS证书文件

`;return(d,a)=>(t.openBlock(),t.createElementBlock("div",null,[t.createVNode(t.unref(s.MarkdownView),{content:i})]))}}),u=[".docx",".doc",".xlsx",".xls",".ppt",".pptx",".pdf",".hwp",".wps",".html",".odt"];function f(c,i,d,a){return{id:a,icon:d,title:c,matcher(n,r){return!i&&n.readonly?!1:u.findIndex(l=>r.name.endsWith(l))!=-1},sort:o.getContext().fileOpenHandler.value.length,action(n,r){const l={targetId:n.uid,path:n.path,protocol:n.protocol,isView:i||n.readonly,...n.getProtocolParams(),name:r.name},O=`${location.origin}/api/office/editor?${e.stringify(l)}`;window.open(O)}}}window.bootContext.addProcessor({taskName:"注册文档组件",execute(c,i){o.getContext().fileOpenHandler.value.push(f("文档在线编辑",!1,"mdi-file-document-edit","only-office-edit")),o.getContext().fileOpenHandler.value.push(f("文档在线预览",!0,"mdi-file-eye-outline","only-office-view")),c.component("OnlyOfficeHelp",m)}})});
