(function(e,n){typeof exports=="object"&&typeof module!="undefined"?n(require("vue"),require("sfc-common")):typeof define=="function"&&define.amd?define(["vue","sfc-common"],n):(e=typeof globalThis!="undefined"?globalThis:e||self,n(e.Vue,e.SfcCommon))})(this,function(e,n){"use strict";var ee=Object.defineProperty,te=Object.defineProperties;var ne=Object.getOwnPropertyDescriptors;var I=Object.getOwnPropertySymbols;var oe=Object.prototype.hasOwnProperty,ae=Object.prototype.propertyIsEnumerable;var b=(e,n,c)=>n in e?ee(e,n,{enumerable:!0,configurable:!0,writable:!0,value:c}):e[n]=c,_=(e,n)=>{for(var c in n||(n={}))oe.call(n,c)&&b(e,c,n[c]);if(I)for(var c of I(n))ae.call(n,c)&&b(e,c,n[c]);return e},D=(e,n)=>te(e,ne(n));var c;(t=>{(l=>{function r(a,w){return{url:"/nwt/findWolByUid",params:{uid:a,checkOnline:!!w}}}l.findByUid=r;function s(a){return{url:"/nwt/saveWolDevice",method:"post",headers:{"Content-Type":"application/json"},data:a}}l.saveWolDevice=s;function i(a){return{url:"/nwt/wakeWolDevice",params:{id:a}}}l.wakeWolDevice=i;function p(a){return{url:"/nwt/batchDeleteWol",data:a,method:"post",headers:{"Content-Type":"application/json"}}}l.batchDelete=p})(t.Wol||(t.Wol={}));function d(){return{url:"/nwt/getAllInterface"}}t.getAllInterface=d})(c||(c={}));var le="",N=(t,d)=>{const l=t.__vccOpts||t;for(const[r,s]of d)l[r]=s;return l};const L={class:"tip"},A=e.createTextVNode(" IP\u5730\u5740: "),$={style:{"padding-left":"24px"}},M=e.createTextVNode(" \u5E7F\u64AD\u5730\u5740: "),O={style:{"padding-left":"24px"}},W=e.defineComponent({name:"NetworkInterfaceInfoCard"}),U=e.defineComponent(D(_({},W),{props:{networkInterface:{type:Object,default:()=>({})}},setup(t){return(d,l)=>{const r=e.resolveComponent("VCardContent"),s=e.resolveComponent("VCard");return e.openBlock(),e.createBlock(s,{class:"network-interface-info-card",title:t.networkInterface.name},{default:e.withCtx(()=>[e.createVNode(r,null,{default:e.withCtx(()=>[e.createElementVNode("div",L,[e.createElementVNode("div",null,"\u63A5\u53E3: "+e.toDisplayString(t.networkInterface.name),1),e.createElementVNode("div",null,"\u540D\u79F0: "+e.toDisplayString(t.networkInterface.displayName),1),e.createElementVNode("div",null,"MAC: "+e.toDisplayString(t.networkInterface.mac),1),e.createElementVNode("div",null,[A,e.createElementVNode("ul",$,[(e.openBlock(!0),e.createElementBlock(e.Fragment,null,e.renderList(t.networkInterface.ipList,i=>(e.openBlock(),e.createElementBlock("li",{key:i},e.toDisplayString(i),1))),128))])]),e.createElementVNode("div",null,[M,e.createElementVNode("ul",O,[(e.openBlock(!0),e.createElementBlock(e.Fragment,null,e.renderList(t.networkInterface.broadcastAddressList,i=>(e.openBlock(),e.createElementBlock("li",{key:i},e.toDisplayString(i),1))),128))])]),e.createElementVNode("div",null,"MTU: "+e.toDisplayString(t.networkInterface.mtu),1)])]),_:1})]),_:1},8,["title"])}}}));var h=N(U,[["__scopeId","data-v-55ec9a7c"]]);const S={style:{display:"flex","flex-wrap":"wrap"}},T=e.defineComponent({name:"NetworkInterfaceList",components:{NetworkInterfaceInfoCard:h}}),R=e.defineComponent(D(_({},T),{setup(t){const d=window.SfcUtils,l=new n.LoadingManager,r=l.getLoadingRef(),s=e.ref([]);return n.MethodInterceptor.createAsyncActionProxy({async loadData(){s.value=(await d.request(c.getAllInterface())).data.data}},!1,l).loadData(),(p,a)=>{const w=e.resolveComponent("LoadingMask");return e.openBlock(),e.createElementBlock("div",null,[e.createVNode(w,{loading:e.unref(r)},null,8,["loading"]),e.createElementVNode("div",S,[(e.openBlock(!0),e.createElementBlock(e.Fragment,null,e.renderList(e.unref(s),m=>(e.openBlock(),e.createBlock(h,{key:m.name,style:{animation:"up-in .2s"},"network-interface":m},null,8,["network-interface"]))),128))])])}}}));var re="";const j={class:"d-flex justify-space-between"},q={class:"tip",style:{width:"calc(100% - 12px)","margin-left":"12px"}},P={class:"d-flex align-center"},z=e.defineComponent({name:"WolDeviceCard"}),G=e.defineComponent(D(_({},z),{props:{wolDevice:{type:Object,default:()=>({})},loading:{type:Boolean,default:!1}},emits:["wake","edit","delete"],setup(t,{emit:d}){return(l,r)=>{const s=e.resolveComponent("LoadingMask"),i=e.resolveComponent("CommonIcon"),p=e.resolveComponent("VBtn"),a=e.resolveComponent("VCardContent"),w=e.resolveComponent("VCard");return e.openBlock(),e.createBlock(w,{class:"wol-device-card"},{default:e.withCtx(()=>[e.createVNode(s,{loading:t.loading,type:"circular"},null,8,["loading"]),e.createVNode(a,null,{default:e.withCtx(()=>[e.createElementVNode("div",j,[e.createElementVNode("div",null,[e.createVNode(i,{icon:"mdi-laptop",color:t.wolDevice.isOnline?"primary":"",style:{"font-size":"32px"}},null,8,["color"])]),e.createElementVNode("div",q,[e.createElementVNode("div",P,[e.createTextVNode(" \u8BBE\u5907\u540D: "+e.toDisplayString(t.wolDevice.name)+" ",1),e.createVNode(i,{class:"link d-flex align-center",style:{"font-size":"10px","margin-left":"6px"},icon:"mdi-pencil",onClick:r[0]||(r[0]=m=>d("edit",t.wolDevice))}),e.createVNode(i,{class:"link d-flex align-center text-error",style:{"font-size":"10px","margin-left":"6px","--main-color":"var(--v-theme-error)"},icon:"mdi-close",onClick:r[1]||(r[1]=m=>d("delete",t.wolDevice))})]),e.createElementVNode("div",null,"MAC: "+e.toDisplayString(t.wolDevice.mac),1),e.createElementVNode("div",null,"IP: "+e.toDisplayString(t.wolDevice.ip),1)]),e.createElementVNode("div",null,[t.wolDevice.isOnline?e.createCommentVNode("",!0):(e.openBlock(),e.createBlock(p,{key:0,style:{"margin-top":"6px"},icon:"mdi-power",color:"primary",onClick:r[2]||(r[2]=m=>d("wake",t.wolDevice))}))])])]),_:1})]),_:1})}}}));var F=N(G,[["__scopeId","data-v-4224615e"]]);const H=e.defineComponent({name:"WolDeviceForm",components:{FormRow:n.Components.FormRow,FormCol:n.Components.FormCol,TextInput:n.Components.TextInput}}),J=e.defineComponent(D(_({},H),{props:{initObject:{type:Object,default:void 0},readOnly:{type:Boolean,default:!1}},emits:["submit"],setup(t,{expose:d,emit:l}){const r=t,s=window.SfcUtils,i=e.ref(),p=n.defineForm({actions:{async submit(){return await s.request(c.Wol.saveWolDevice(a))}},formData:{name:"",port:9,ip:"",mac:"",sendIp:"255.255.255.255"},formRef:i,validators:{name:[n.Validators.notNull(),n.Validators.maxLen("\u4E0D\u80FD\u5927\u4E8E255\u4E2A\u5B57\u7B26",255)],mac:[n.Validators.isMatchRegex("^([abcdefABCDEF\\d]{2}[:\\-]?){6}$","\u4E0D\u662F\u6709\u6548\u7684mac\u5730\u5740")],ip:[n.Validators.notNull(),n.Validators.isMatchRegex("^((2((5[0-5])|([0-4]\\d)))|([0-1]?\\d{1,2}))(\\.((2((5[0-5])|([0-4]\\d)))|([0-1]?\\d{1,2}))){3}$","\u4E0D\u662F\u6709\u6548\u7684ipv4\u5730\u5740")],port:[n.Validators.minNum(1),n.Validators.maxNum(65535)]},throwError:!0}),{formData:a,actions:w,validators:m,loadingRef:k,loadingManager:B}=p,C=B.getLoadingRef();return r.initObject&&Object.assign(a,r.initObject),d(p),(E,o)=>{const u=e.resolveComponent("LoadingMask"),y=e.resolveComponent("TextInput"),g=e.resolveComponent("FormCol"),x=e.resolveComponent("FormRow"),V=e.resolveComponent("base-form");return e.openBlock(),e.createBlock(V,{ref_key:"formRef",ref:i,"model-value":e.unref(a),"submit-action":e.unref(w).submit},{default:e.withCtx(()=>[e.createVNode(u,{loading:e.unref(C)},null,8,["loading"]),e.createVNode(x,null,{default:e.withCtx(()=>[e.createVNode(g,{label:"\u8BBE\u5907\u540D\u79F0","top-label":""},{default:e.withCtx(()=>[e.createVNode(y,{modelValue:e.unref(a).name,"onUpdate:modelValue":o[0]||(o[0]=f=>e.unref(a).name=f),rules:e.unref(m).name,placeholder:"\u8D77\u4E2A\u540D\u5B57\u5427~",readonly:t.readOnly},null,8,["modelValue","rules","readonly"])]),_:1}),e.createVNode(g,{label:"MAC\u5730\u5740","top-label":""},{default:e.withCtx(()=>[e.createVNode(y,{modelValue:e.unref(a).mac,"onUpdate:modelValue":o[1]||(o[1]=f=>e.unref(a).mac=f),rules:e.unref(m).mac,placeholder:"\u5173\u952E\u53C2\u6570",readonly:t.readOnly},null,8,["modelValue","rules","readonly"])]),_:1}),e.createVNode(g,{label:"\u7AEF\u53E3(udp)","top-label":""},{default:e.withCtx(()=>[e.createVNode(y,{modelValue:e.unref(a).port,"onUpdate:modelValue":o[2]||(o[2]=f=>e.unref(a).port=f),rules:e.unref(m).port,placeholder:"\u9ED8\u8BA4UDP\u7AEF\u53E3-9",readonly:t.readOnly},null,8,["modelValue","rules","readonly"])]),_:1}),e.createVNode(g,{label:"IP\u5730\u5740(ipv4)","top-label":""},{default:e.withCtx(()=>[e.createVNode(y,{modelValue:e.unref(a).ip,"onUpdate:modelValue":o[3]||(o[3]=f=>e.unref(a).ip=f),rules:e.unref(m).ip,placeholder:"\u7528\u6765\u68C0\u6D4B\u662F\u5426\u5728\u7EBF",readonly:t.readOnly},null,8,["modelValue","rules","readonly"])]),_:1}),e.createVNode(g,{label:"\u53D1\u9001/\u5E7F\u64AD\u5730\u5740","top-label":"",class:"mw-50"},{default:e.withCtx(()=>[e.createVNode(y,{modelValue:e.unref(a).sendIp,"onUpdate:modelValue":o[4]||(o[4]=f=>e.unref(a).sendIp=f),rules:e.unref(m).ip,placeholder:"255.255.255.255",readonly:t.readOnly},null,8,["modelValue","rules","readonly"])]),_:1})]),_:1})]),_:1},8,["model-value","submit-action"])}}})),K={class:"WolDeviceList"},Q=e.createTextVNode(" \u6DFB\u52A0\u8BBE\u5907 "),X=e.createTextVNode(" \u5237\u65B0 "),Y={style:{"margin-top":"12px"}},Z=e.defineComponent({name:"WolDeviceList",components:{WolDeviceCard:F,LoadingMask:n.Components.LoadingMask}}),v=e.defineComponent(D(_({},Z),{props:{uid:{type:[String,Number],default:0}},setup(t){const d=t,l=window.SfcUtils,r=new n.LoadingManager,s=r.getLoadingRef(),i=e.ref([]),p=e.reactive({}),a=async()=>{i.value=(await l.request(c.Wol.findByUid(d.uid,!0))).data.data||[],i.value.forEach(o=>{o.isOnline&&(p[o.id]=!1)})},w=async o=>{await l.confirm(`\u786E\u5B9A\u8981\u5524\u9192\u8BBE\u5907${o.name}\u5417\uFF1F`,"\u5524\u9192\u786E\u8BA4").then(()=>k.wake(o))},m=async o=>{const u=`\u786E\u5B9A\u8981\u5220\u9664 ${o.length==1?o[0].name:o.length+"\u4E2A\u8BBE\u5907"} \u5417\uFF1F`;await l.confirm(u,"\u5220\u9664\u786E\u8BA4"),await k.batchDelete(o),l.snackbar("\u5220\u9664\u6210\u529F"),await k.loadData()},k=n.MethodInterceptor.createAsyncActionProxy({async loadData(){await a()},async wake(o){await l.request(c.Wol.wakeWolDevice(o.id)),p[o.id]=!0},async batchDelete(o){await l.request(c.Wol.batchDelete(o.map(u=>u.id)))}},!1,r),B=o=>{const u=l.openComponentDialog(J,{title:o?"\u7F16\u8F91\u8BBE\u5907":"\u6DFB\u52A0\u8BBE\u5907",props:{initObject:o||{uid:d.uid}},extraDialogOptions:{persistent:!0},async onConfirm(){return(await u.getInstAsForm().submit()).success?(l.snackbar(o?"\u4FDD\u5B58\u6210\u529F":"\u6DFB\u52A0\u6210\u529F"),k.loadData(),!0):!1}})};k.loadData();let C=!1,E=setInterval(async()=>{if(!C)try{C=!0,await a()}finally{C=!1}},5e3);return e.onUnmounted(()=>{E&&clearInterval(E)}),(o,u)=>{const y=e.resolveComponent("VBtn"),g=e.resolveComponent("CommonIcon"),x=e.resolveComponent("LoadingMask");return e.openBlock(),e.createElementBlock("div",K,[e.createVNode(y,{color:"primary",onClick:u[0]||(u[0]=V=>B())},{default:e.withCtx(()=>[Q]),_:1}),e.createVNode(y,{style:{"margin-left":"12px"},onClick:e.unref(k).loadData},{default:e.withCtx(()=>[e.createVNode(g,{icon:"mdi-refresh"}),X]),_:1},8,["onClick"]),e.createVNode(x,{loading:e.unref(s)},null,8,["loading"]),e.createElementVNode("div",Y,[(e.openBlock(!0),e.createElementBlock(e.Fragment,null,e.renderList(e.unref(i),V=>(e.openBlock(),e.createBlock(F,{key:V.id,style:{animation:"up-in .2s"},"wol-device":V,loading:e.unref(p)[V.id],onWake:w,onEdit:u[1]||(u[1]=f=>B(f)),onDelete:u[2]||(u[2]=f=>m([f]))},null,8,["wol-device","loading"]))),128))])])}}}));window.bootContext.addProcessor({taskName:"\u6CE8\u518C\u7F51\u7EDC\u5DE5\u5177",execute(t,d){t.component("WolDeviceList",v),t.component("NetworkInterfaceList",R)}})});