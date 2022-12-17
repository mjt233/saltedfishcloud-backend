(function(n,y){typeof exports=="object"&&typeof module!="undefined"?y(require("vue")):typeof define=="function"&&define.amd?define(["vue"],y):(n=typeof globalThis!="undefined"?globalThis:n||self,y(n.Vue))})(this,function(n){"use strict";var Ot=Object.defineProperty,jt=Object.defineProperties;var zt=Object.getOwnPropertyDescriptors;var G=Object.getOwnPropertySymbols;var Ge=Object.prototype.hasOwnProperty,Ye=Object.prototype.propertyIsEnumerable;var qe=(n,y,b)=>y in n?Ot(n,y,{enumerable:!0,configurable:!0,writable:!0,value:b}):n[y]=b,h=(n,y)=>{for(var b in y||(y={}))Ge.call(y,b)&&qe(n,b,y[b]);if(G)for(var b of G(y))Ye.call(y,b)&&qe(n,b,y[b]);return n},T=(n,y)=>jt(n,zt(y));var ce=(n,y)=>{var b={};for(var k in n)Ge.call(n,k)&&y.indexOf(k)<0&&(b[k]=n[k]);if(n!=null&&G)for(var k of G(n))y.indexOf(k)<0&&Ye.call(n,k)&&(b[k]=n[k]);return b};function y(e,t,r){if(r&&(t={_isVue:!0,$parent:r,$options:t}),t){if(t.$_alreadyWarned=t.$_alreadyWarned||[],t.$_alreadyWarned.includes(e))return;t.$_alreadyWarned.push(e)}return`[Vuetify] ${e}`+(t?Xe(t):"")}function b(e,t,r){const o=y(e,t,r);o!=null&&console.warn(o)}const k=/(?:^|[-_])(\w)/g,Ke=e=>e.replace(k,t=>t.toUpperCase()).replace(/[-_]/g,"");function Y(e,t){if(e.$root===e)return"<Root>";const r=typeof e=="function"&&e.cid!=null?e.options:e._isVue?e.$options||e.constructor.options:e||{};let o=r.name||r._componentTag;const l=r.__file;if(!o&&l){const i=l.match(/([^/\\]+)\.vue$/);o=i==null?void 0:i[1]}return(o?`<${Ke(o)}>`:"<Anonymous>")+(l&&t!==!1?` at ${l}`:"")}function Xe(e){if(e._isVue&&e.$parent){const t=[];let r=0;for(;e;){if(t.length>0){const o=t[t.length-1];if(o.constructor===e.constructor){r++,e=e.$parent;continue}else r>0&&(t[t.length-1]=[o,r],r=0)}t.push(e),e=e.$parent}return`

found in

`+t.map((o,l)=>`${l===0?"---> ":" ".repeat(5+l*2)}${Array.isArray(o)?`${Y(o[0])}... (${o[1]} recursive calls)`:Y(o)}`).join(`
`)}else return`

(found in ${Y(e)})`}function de(e,t){if(e===t)return!0;if(e instanceof Date&&t instanceof Date&&e.getTime()!==t.getTime()||e!==Object(e)||t!==Object(t))return!1;const r=Object.keys(e);return r.length!==Object.keys(t).length?!1:r.every(o=>de(e[o],t[o]))}function R(e){let t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:"px";if(!(e==null||e===""))return isNaN(+e)?String(e):isFinite(+e)?`${Number(e)}${t}`:void 0}function K(e){return e!==null&&typeof e=="object"&&!Array.isArray(e)}const fe=Object.freeze({enter:13,tab:9,delete:46,esc:27,space:32,up:38,down:40,left:37,right:39,end:35,home:36,del:46,backspace:8,insert:45,pageup:33,pagedown:34,shift:16});Object.freeze({enter:"Enter",tab:"Tab",delete:"Delete",esc:"Escape",space:"Space",up:"ArrowUp",down:"ArrowDown",left:"ArrowLeft",right:"ArrowRight",end:"End",home:"Home",del:"Delete",backspace:"Backspace",insert:"Insert",pageup:"PageUp",pagedown:"PageDown",shift:"Shift"});function X(e,t){const r=Object.create(null),o=Object.create(null);for(const l in e)t.some(i=>i instanceof RegExp?i.test(l):i===l)?r[l]=e[l]:o[l]=e[l];return[r,o]}function me(e){return X(e,["class","style","id",/^data-/])}function L(e){return e==null?[]:Array.isArray(e)?e:[e]}function Q(){let e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:{},t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:{},r=arguments.length>2?arguments[2]:void 0;const o={};for(const l in e)o[l]=e[l];for(const l in t){const i=e[l],a=t[l];if(K(i)&&K(a)){o[l]=Q(i,a,r);continue}if(Array.isArray(i)&&Array.isArray(a)&&r){o[l]=r(i,a);continue}o[l]=a}return o}function pe(e){return e.map(t=>t.type===n.Fragment?pe(t.children):t).flat()}function J(){return(arguments.length>0&&arguments[0]!==void 0?arguments[0]:"").replace(/[^a-z]/gi,"-").replace(/\B([A-Z])/g,"-$1").toLowerCase()}function ge(e){return!!e&&/^(#|var\(--|(rgb|hsl)a?\()/.test(e)}const ye=Symbol.for("vuetify:defaults");function ve(){const e=n.inject(ye);if(!e)throw new Error("[Vuetify] Could not find defaults instance");return e}function Qe(e,t){const r=ve(),o=n.ref(e),l=n.computed(()=>{const i=n.unref(t==null?void 0:t.scoped),a=n.unref(t==null?void 0:t.reset),s=n.unref(t==null?void 0:t.root);let u=Q(o.value,{prev:r.value});if(i)return u;if(a||s){const d=Number(a||1/0);for(let m=0;m<=d&&u.prev;m++)u=u.prev;return u}return Q(u.prev,u)});return n.provide(ye,l),l}function Je(e,t){var r,o;return((r=e.props)==null?void 0:r.hasOwnProperty(t))||((o=e.props)==null?void 0:o.hasOwnProperty(J(t)))}const V=function(t){var r;if(t._setup=(r=t._setup)!=null?r:t.setup,!t.name)return b("The component is missing an explicit name, unable to generate default prop value"),t;if(t._setup){var o;t.props=(o=t.props)!=null?o:{},t.props._as=String,t.setup=function(i,a){const s=n.getCurrentInstance(),u=ve(),d=n.shallowRef(),m=n.shallowReactive(h({},n.toRaw(i)));n.watchEffect(()=>{var f;const w=u.value.global,C=u.value[(f=i._as)!=null?f:t.name];if(C){const v=Object.entries(C).filter(g=>{let[x]=g;return x.startsWith("V")});v.length&&(d.value=Object.fromEntries(v))}for(const v of Object.keys(i)){let g;if(Je(s.vnode,v))g=i[v];else{var $,E;g=($=(E=C==null?void 0:C[v])!=null?E:w==null?void 0:w[v])!=null?$:i[v]}m[v]!==g&&(m[v]=g)}});const p=t._setup(m,a);let c;return n.watch(d,(f,w)=>{!f&&c?c.stop():f&&!w&&(c=n.effectScope(),c.run(()=>{Qe(f)}))},{immediate:!0}),p}}return t};function he(){let e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:!0;return t=>(e?V:n.defineComponent)(t)}function O(e,t){const r=n.getCurrentInstance();if(!r)throw new Error(`[Vuetify] ${e} ${t||"must be called from inside a setup function"}`);return r}function Z(){var e;let t=arguments.length>0&&arguments[0]!==void 0?arguments[0]:"composables";return J((e=O(t).type)==null?void 0:e.name)}let _e=0,U=new WeakMap;function N(){const e=O("getUid");if(U.has(e))return U.get(e);{const t=_e++;return U.set(e,t),t}}N.reset=()=>{_e=0,U=new WeakMap};const we=typeof window!="undefined"&&CSS.supports("selector(:focus-visible)");function P(e,t){return r=>Object.keys(e).reduce((o,l)=>{const a=typeof e[l]=="object"&&e[l]!=null&&!Array.isArray(e[l])?e[l]:{type:e[l]};return r&&l in r?o[l]=T(h({},a),{default:r[l]}):o[l]=a,t&&(o[l].source=t),o},{})}function j(e){const t=O("useRender");t.render=e}const be=Symbol.for("vuetify:theme"),ee=P({theme:String},"theme");function Ze(e){O("provideTheme");const t=n.inject(be,null);if(!t)throw new Error("Could not find Vuetify theme injection");const r=n.computed(()=>{var i;return(i=e.theme)!=null?i:t==null?void 0:t.name.value}),o=n.computed(()=>t.isDisabled?void 0:`v-theme--${r.value}`),l=T(h({},t),{name:r,themeClasses:o});return n.provide(be,l),l}function _(e){let t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:"top center 0",r=arguments.length>2?arguments[2]:void 0;return V({name:e,props:{group:Boolean,hideOnLeave:Boolean,leaveAbsolute:Boolean,mode:{type:String,default:r},origin:{type:String,default:t}},setup(o,l){let{slots:i}=l;return()=>{const a=o.group?n.TransitionGroup:n.Transition;return n.h(a,{name:e,mode:o.mode,onBeforeEnter(s){s.style.transformOrigin=o.origin},onLeave(s){if(o.leaveAbsolute){const{offsetTop:u,offsetLeft:d,offsetWidth:m,offsetHeight:p}=s;s._transitionInitialStyles={position:s.style.position,top:s.style.top,left:s.style.left,width:s.style.width,height:s.style.height},s.style.position="absolute",s.style.top=`${u}px`,s.style.left=`${d}px`,s.style.width=`${m}px`,s.style.height=`${p}px`}o.hideOnLeave&&s.style.setProperty("display","none","important")},onAfterLeave(s){if(o.leaveAbsolute&&s!=null&&s._transitionInitialStyles){const{position:u,top:d,left:m,width:p,height:c}=s._transitionInitialStyles;delete s._transitionInitialStyles,s.style.position=u||"",s.style.top=d||"",s.style.left=m||"",s.style.width=p||"",s.style.height=c||""}}},i.default)}}})}function Ce(e,t){let r=arguments.length>2&&arguments[2]!==void 0?arguments[2]:"in-out";return V({name:e,props:{mode:{type:String,default:r}},setup(o,l){let{slots:i}=l;return()=>n.h(n.Transition,h({name:e},t),i.default)}})}function Ve(){let e=arguments.length>0&&arguments[0]!==void 0?arguments[0]:"";const r=(arguments.length>1&&arguments[1]!==void 0?arguments[1]:!1)?"width":"height",o=n.camelize(`offset-${r}`);return{onBeforeEnter(a){a._parent=a.parentNode,a._initialStyle={transition:a.style.transition,overflow:a.style.overflow,[r]:a.style[r]}},onEnter(a){const s=a._initialStyle;a.style.setProperty("transition","none","important"),a.style.overflow="hidden";const u=`${a[o]}px`;a.style[r]="0",a.offsetHeight,a.style.transition=s.transition,e&&a._parent&&a._parent.classList.add(e),requestAnimationFrame(()=>{a.style[r]=u})},onAfterEnter:i,onEnterCancelled:i,onLeave(a){a._initialStyle={transition:"",overflow:a.style.overflow,[r]:a.style[r]},a.style.overflow="hidden",a.style[r]=`${a[o]}px`,a.offsetHeight,requestAnimationFrame(()=>a.style[r]="0")},onAfterLeave:l,onLeaveCancelled:l};function l(a){e&&a._parent&&a._parent.classList.remove(e),i(a)}function i(a){const s=a._initialStyle[r];a.style.overflow=a._initialStyle.overflow,s!=null&&(a.style[r]=s),delete a._initialStyle}}_("carousel-transition"),_("carousel-reverse-transition"),_("tab-transition"),_("tab-reverse-transition"),_("menu-transition"),_("fab-transition","center center","out-in"),_("dialog-bottom-transition"),_("dialog-top-transition"),_("fade-transition"),_("scale-transition"),_("scroll-x-transition"),_("scroll-x-reverse-transition"),_("scroll-y-transition"),_("scroll-y-reverse-transition"),_("slide-x-transition"),_("slide-x-reverse-transition");const et=_("slide-y-transition");_("slide-y-reverse-transition"),Ce("expand-transition",Ve()),Ce("expand-x-transition",Ve("",!0));const tt=P({transition:{type:[Boolean,String,Object],default:"fade-transition",validator:e=>e!==!0}},"transition"),nt=(e,t)=>{var r;let{slots:o}=t;const u=e,{transition:l}=u,i=ce(u,["transition"]);if(!l||typeof l=="boolean")return(r=o.default)==null?void 0:r.call(o);const d=typeof l=="object"?l:{},{component:a=n.Transition}=d,s=ce(d,["component"]);return n.h(a,n.mergeProps(typeof l=="string"?{name:l}:s,i),o)},ot=P({tag:{type:String,default:"div"}},"tag");function rt(e){const t=n.computed(()=>ge(e.value.background)),r=n.computed(()=>ge(e.value.text)),o=n.computed(()=>{const i=[];return e.value.background&&!t.value&&i.push(`bg-${e.value.background}`),e.value.text&&!r.value&&i.push(`text-${e.value.text}`),i}),l=n.computed(()=>{const i={};return e.value.background&&t.value&&(i.backgroundColor=e.value.background),e.value.text&&r.value&&(i.color=e.value.text,i.caretColor=e.value.text),i});return{colorClasses:o,colorStyles:l}}function te(e,t){const r=n.computed(()=>({text:n.isRef(e)?e.value:t?e[t]:null})),{colorClasses:o,colorStyles:l}=rt(r);return{textColorClasses:o,textColorStyles:l}}function ne(e,t,r){let o=arguments.length>3&&arguments[3]!==void 0?arguments[3]:u=>u,l=arguments.length>4&&arguments[4]!==void 0?arguments[4]:u=>u;const i=O("useProxiedModel"),a=n.computed(()=>{var u,d;return!!(typeof e[t]!="undefined"&&(i!=null&&(u=i.vnode.props)!=null&&u.hasOwnProperty(t)||i!=null&&(d=i.vnode.props)!=null&&d.hasOwnProperty(J(t))))}),s=n.ref(o(e[t]));return n.computed({get(){return a.value?o(e[t]):s.value},set(u){(a.value?o(e[t]):s.value)!==u&&(s.value=u,i==null||i.emit(`update:${t}`,l(u)))}})}const lt=[null,"default","comfortable","compact"],Se=P({density:{type:String,default:"default",validator:e=>lt.includes(e)}},"density");function $e(e){let t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:Z();return{densityClasses:n.computed(()=>`${t}--density-${e.density}`)}}var Mt="";const Ie=["x-small","small","default","large","x-large"],it=P({size:{type:[String,Number],default:"default"}},"size");function at(e){let t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:Z();const r=n.computed(()=>Ie.includes(e.size)?`${t}--size-${e.size}`:null),o=n.computed(()=>!Ie.includes(e.size)&&e.size?{width:R(e.size),height:R(e.size)}:null);return{sizeClasses:r,sizeStyles:o}}const I=[String,Function,Object],st=Symbol.for("vuetify:icons"),W=P({icon:{type:I,required:!0},tag:{type:String,required:!0}},"icon"),ut=V({name:"VComponentIcon",props:W(),setup(e){return()=>n.createVNode(e.tag,null,{default:()=>[n.createVNode(e.icon,null,null)]})}});V({name:"VSvgIcon",inheritAttrs:!1,props:W(),setup(e,t){let{attrs:r}=t;return()=>n.createVNode(e.tag,n.mergeProps(r,{style:null}),{default:()=>[n.createVNode("svg",{class:"v-icon__svg",xmlns:"http://www.w3.org/2000/svg",viewBox:"0 0 24 24",role:"img","aria-hidden":"true"},[n.createVNode("path",{d:e.icon},null)])]})}}),V({name:"VLigatureIcon",props:W(),setup(e){return()=>n.createVNode(e.tag,null,{default:()=>[e.icon]})}}),V({name:"VClassIcon",props:W(),setup(e){return()=>n.createVNode(e.tag,{class:e.icon},null)}});const ct=e=>{const t=n.inject(st);if(!t)throw new Error("Missing Vuetify Icons provide!");return{iconData:n.computed(()=>{const o=n.isRef(e)?e.value:e.icon;if(!o)throw new Error("Icon value is undefined or null");let l=o;if(typeof o=="string"&&o.includes("$")){var i;l=(i=t.aliases)==null?void 0:i[o.slice(o.indexOf("$")+1)]}if(!l)throw new Error(`Could not find aliased icon "${o}"`);if(typeof l!="string")return{component:ut,icon:l};const a=Object.keys(t.sets).find(d=>typeof l=="string"&&l.startsWith(`${d}:`)),s=a?l.slice(a.length+1):l;return{component:t.sets[a!=null?a:t.defaultSet].component,icon:s}})}},dt=P(h(h(h({color:String,start:Boolean,end:Boolean,icon:I},it()),ot({tag:"i"})),ee()),"v-icon"),oe=V({name:"VIcon",props:dt(),setup(e,t){let{attrs:r,slots:o}=t,l;o.default&&(l=n.computed(()=>{var m,p;const c=(m=o.default)==null?void 0:m.call(o);if(!!c)return(p=pe(c).filter(f=>f.children&&typeof f.children=="string")[0])==null?void 0:p.children}));const{themeClasses:i}=Ze(e),{iconData:a}=ct(l||e),{sizeClasses:s}=at(e),{textColorClasses:u,textColorStyles:d}=te(n.toRef(e,"color"));return()=>n.createVNode(a.value.component,{tag:e.tag,icon:a.value.icon,class:["v-icon","notranslate",s.value,u.value,i.value,{"v-icon--clickable":!!r.onClick,"v-icon--start":e.start,"v-icon--end":e.end}],style:[s.value?void 0:{fontSize:R(e.size),width:R(e.size),height:R(e.size)},d.value],"aria-hidden":"true"},null)}});var Ut="";const re=Symbol("rippleStop"),ft=80;function Ee(e,t){e.style.transform=t,e.style.webkitTransform=t}function le(e,t){e.style.opacity=`calc(${t} * var(--v-theme-overlay-multiplier))`}function ie(e){return e.constructor.name==="TouchEvent"}function ke(e){return e.constructor.name==="KeyboardEvent"}const mt=function(e,t){var r;let o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:{},l=0,i=0;if(!ke(e)){const c=t.getBoundingClientRect(),f=ie(e)?e.touches[e.touches.length-1]:e;l=f.clientX-c.left,i=f.clientY-c.top}let a=0,s=.3;(r=t._ripple)!=null&&r.circle?(s=.15,a=t.clientWidth/2,a=o.center?a:a+Math.sqrt((l-a)**2+(i-a)**2)/4):a=Math.sqrt(t.clientWidth**2+t.clientHeight**2)/2;const u=`${(t.clientWidth-a*2)/2}px`,d=`${(t.clientHeight-a*2)/2}px`,m=o.center?u:`${l-a}px`,p=o.center?d:`${i-a}px`;return{radius:a,scale:s,x:m,y:p,centerX:u,centerY:d}},H={show(e,t){var r;let o=arguments.length>2&&arguments[2]!==void 0?arguments[2]:{};if(!(t!=null&&(r=t._ripple)!=null&&r.enabled))return;const l=document.createElement("span"),i=document.createElement("span");l.appendChild(i),l.className="v-ripple__container",o.class&&(l.className+=` ${o.class}`);const{radius:a,scale:s,x:u,y:d,centerX:m,centerY:p}=mt(e,t,o),c=`${a*2}px`;i.className="v-ripple__animation",i.style.width=c,i.style.height=c,t.appendChild(l);const f=window.getComputedStyle(t);f&&f.position==="static"&&(t.style.position="relative",t.dataset.previousPosition="static"),i.classList.add("v-ripple__animation--enter"),i.classList.add("v-ripple__animation--visible"),Ee(i,`translate(${u}, ${d}) scale3d(${s},${s},${s})`),le(i,0),i.dataset.activated=String(performance.now()),setTimeout(()=>{i.classList.remove("v-ripple__animation--enter"),i.classList.add("v-ripple__animation--in"),Ee(i,`translate(${m}, ${p}) scale3d(1,1,1)`),le(i,.08)},0)},hide(e){var t;if(!(e!=null&&(t=e._ripple)!=null&&t.enabled))return;const r=e.getElementsByClassName("v-ripple__animation");if(r.length===0)return;const o=r[r.length-1];if(o.dataset.isHiding)return;o.dataset.isHiding="true";const l=performance.now()-Number(o.dataset.activated),i=Math.max(250-l,0);setTimeout(()=>{o.classList.remove("v-ripple__animation--in"),o.classList.add("v-ripple__animation--out"),le(o,0),setTimeout(()=>{e.getElementsByClassName("v-ripple__animation").length===1&&e.dataset.previousPosition&&(e.style.position=e.dataset.previousPosition,delete e.dataset.previousPosition),o.parentNode&&e.removeChild(o.parentNode)},300)},i)}};function xe(e){return typeof e=="undefined"||!!e}function z(e){const t={},r=e.currentTarget;if(!(!(r!=null&&r._ripple)||r._ripple.touched||e[re])){if(e[re]=!0,ie(e))r._ripple.touched=!0,r._ripple.isTouch=!0;else if(r._ripple.isTouch)return;if(t.center=r._ripple.centered||ke(e),r._ripple.class&&(t.class=r._ripple.class),ie(e)){if(r._ripple.showTimerCommit)return;r._ripple.showTimerCommit=()=>{H.show(e,r,t)},r._ripple.showTimer=window.setTimeout(()=>{var o;r!=null&&(o=r._ripple)!=null&&o.showTimerCommit&&(r._ripple.showTimerCommit(),r._ripple.showTimerCommit=null)},ft)}else H.show(e,r,t)}}function Pe(e){e[re]=!0}function S(e){const t=e.currentTarget;if(!(!t||!t._ripple)){if(window.clearTimeout(t._ripple.showTimer),e.type==="touchend"&&t._ripple.showTimerCommit){t._ripple.showTimerCommit(),t._ripple.showTimerCommit=null,t._ripple.showTimer=window.setTimeout(()=>{S(e)});return}window.setTimeout(()=>{t._ripple&&(t._ripple.touched=!1)}),H.hide(t)}}function Ae(e){const t=e.currentTarget;!t||!t._ripple||(t._ripple.showTimerCommit&&(t._ripple.showTimerCommit=null),window.clearTimeout(t._ripple.showTimer))}let F=!1;function Te(e){!F&&(e.keyCode===fe.enter||e.keyCode===fe.space)&&(F=!0,z(e))}function Le(e){F=!1,S(e)}function Ne(e){F&&(F=!1,S(e))}function De(e,t,r){var o;const{value:l,modifiers:i}=t,a=xe(l);if(a||H.hide(e),e._ripple=(o=e._ripple)!=null?o:{},e._ripple.enabled=a,e._ripple.centered=i.center,e._ripple.circle=i.circle,K(l)&&l.class&&(e._ripple.class=l.class),a&&!r){if(i.stop){e.addEventListener("touchstart",Pe,{passive:!0}),e.addEventListener("mousedown",Pe);return}e.addEventListener("touchstart",z,{passive:!0}),e.addEventListener("touchend",S,{passive:!0}),e.addEventListener("touchmove",Ae,{passive:!0}),e.addEventListener("touchcancel",S),e.addEventListener("mousedown",z),e.addEventListener("mouseup",S),e.addEventListener("mouseleave",S),e.addEventListener("keydown",Te),e.addEventListener("keyup",Le),e.addEventListener("blur",Ne),e.addEventListener("dragstart",S,{passive:!0})}else!a&&r&&Be(e)}function Be(e){e.removeEventListener("mousedown",z),e.removeEventListener("touchstart",z),e.removeEventListener("touchend",S),e.removeEventListener("touchmove",Ae),e.removeEventListener("touchcancel",S),e.removeEventListener("mouseup",S),e.removeEventListener("mouseleave",S),e.removeEventListener("keydown",Te),e.removeEventListener("keyup",Le),e.removeEventListener("dragstart",S),e.removeEventListener("blur",Ne)}function pt(e,t){De(e,t,!1)}function gt(e){delete e._ripple,Be(e)}function yt(e,t){if(t.value===t.oldValue)return;const r=xe(t.oldValue);De(e,t,r)}const vt={mounted:pt,unmounted:gt,updated:yt};var Wt="",Ht="";const ht=V({name:"VMessages",props:h({active:Boolean,color:String,messages:{type:[Array,String],default:()=>[]}},tt({transition:{component:et,leaveAbsolute:!0,group:!0}})),setup(e,t){let{slots:r}=t;const o=n.computed(()=>L(e.messages)),{textColorClasses:l,textColorStyles:i}=te(n.computed(()=>e.color));return()=>n.createVNode(nt,{transition:e.transition,tag:"div",class:["v-messages",l.value],style:i.value},{default:()=>[e.active&&o.value.map((a,s)=>n.createVNode("div",{class:"v-messages__message",key:`${s}-${o.value}`},[r.message?r.message({message:a}):a]))]})}}),_t=Symbol.for("vuetify:form");function wt(){return n.inject(_t,null)}const bt=P({disabled:Boolean,error:Boolean,errorMessages:{type:[Array,String],default:()=>[]},maxErrors:{type:[Number,String],default:1},name:String,readonly:Boolean,rules:{type:Array,default:()=>[]},modelValue:null,validationValue:null});function Ct(e){let t=arguments.length>1&&arguments[1]!==void 0?arguments[1]:Z(),r=arguments.length>2&&arguments[2]!==void 0?arguments[2]:N();const o=ne(e,"modelValue"),l=n.computed(()=>{var g;return(g=e.validationValue)!=null?g:o.value}),i=wt(),a=n.ref([]),s=n.ref(!0),u=n.computed(()=>!!(L(o.value===""?null:o.value).length||L(l.value===""?null:l.value).length)),d=n.computed(()=>!!(e.disabled||i!=null&&i.isDisabled.value)),m=n.computed(()=>!!(e.readonly||i!=null&&i.isReadonly.value)),p=n.computed(()=>e.errorMessages.length?L(e.errorMessages):a.value),c=n.computed(()=>e.rules.length?e.error||p.value.length?!1:s.value?null:!0:!0),f=n.ref(!1),w=n.computed(()=>({[`${t}--error`]:c.value===!1,[`${t}--dirty`]:u.value,[`${t}--disabled`]:d.value,[`${t}--readonly`]:m.value})),C=n.computed(()=>{var g;return(g=e.name)!=null?g:n.unref(r)});n.onBeforeMount(()=>{i==null||i.register(C.value,v,$,E,c)}),n.onBeforeUnmount(()=>{i==null||i.unregister(C.value)}),n.watch(l,()=>{l.value!=null&&v()});function $(){E(),o.value=null}function E(){s.value=!0,a.value=[]}async function v(){const g=[];f.value=!0;for(const x of e.rules){if(g.length>=(e.maxErrors||1))break;const A=await(typeof x=="function"?x:()=>x)(l.value);if(A!==!0){if(typeof A!="string"){console.warn(`${A} is not a valid value. Rule functions must return boolean true or a string.`);continue}g.push(A)}}return a.value=g,f.value=!1,s.value=!1,a.value}return{errorMessages:p,isDirty:u,isDisabled:d,isReadonly:m,isPristine:s,isValid:c,isValidating:f,reset:$,resetValidation:E,validate:v,validationClasses:w}}const Re=P(h(h({id:String,appendIcon:I,prependIcon:I,hideDetails:[Boolean,String],messages:{type:[Array,String],default:()=>[]},direction:{type:String,default:"horizontal",validator:e=>["horizontal","vertical"].includes(e)}},Se()),bt())),Oe=he()({name:"VInput",props:h({},Re()),emits:{"update:modelValue":e=>!0},setup(e,t){let{attrs:r,slots:o,emit:l}=t;const{densityClasses:i}=$e(e),a=N(),s=n.computed(()=>e.id||`input-${a}`),{errorMessages:u,isDirty:d,isDisabled:m,isReadonly:p,isPristine:c,isValid:f,isValidating:w,reset:C,resetValidation:$,validate:E,validationClasses:v}=Ct(e,"v-input",s),g=n.computed(()=>({id:s,isDirty:d,isDisabled:m,isReadonly:p,isPristine:c,isValid:f,isValidating:w,reset:C,resetValidation:$,validate:E}));return j(()=>{var x,B,A,M,q;const se=!!(o.prepend||e.prependIcon),ue=!!(o.append||e.appendIcon),He=!!((x=e.messages)!=null&&x.length||u.value.length),Rt=!e.hideDetails||e.hideDetails==="auto"&&He;return n.createVNode("div",{class:["v-input",`v-input--${e.direction}`,i.value,v.value]},[se&&n.createVNode("div",{class:"v-input__prepend"},[o==null||(B=o.prepend)==null?void 0:B.call(o,g.value),e.prependIcon&&n.createVNode(oe,{onClick:r["onClick:prepend"],icon:e.prependIcon},null)]),o.default&&n.createVNode("div",{class:"v-input__control"},[(A=o.default)==null?void 0:A.call(o,g.value)]),ue&&n.createVNode("div",{class:"v-input__append"},[o==null||(M=o.append)==null?void 0:M.call(o,g.value),e.appendIcon&&n.createVNode(oe,{onClick:r["onClick:append"],icon:e.appendIcon},null)]),Rt&&n.createVNode("div",{class:"v-input__details"},[n.createVNode(ht,{active:He,messages:u.value.length>0?u.value:e.messages},{message:o.message}),(q=o.details)==null?void 0:q.call(o,g.value)])])}),{reset:C,resetValidation:$,validate:E}}});function Vt(e){return X(e,Object.keys(Oe.props))}var qt="",Gt="";const je=V({name:"VLabel",props:h({text:String},ee()),setup(e,t){let{slots:r}=t;return()=>{var o;return n.createVNode("label",{class:"v-label"},[e.text,(o=r.default)==null?void 0:o.call(r)])}}});var Yt="";const ze=Symbol.for("vuetify:selection-control-group"),St=V({name:"VSelectionControlGroup",props:{disabled:Boolean,id:String,inline:Boolean,name:String,falseIcon:I,trueIcon:I,multiple:{type:Boolean,default:null},readonly:Boolean,type:String,modelValue:null},emits:{"update:modelValue":e=>!0},setup(e,t){let{slots:r}=t;const o=ne(e,"modelValue"),l=N(),i=n.computed(()=>e.id||`v-selection-control-group-${l}`),a=n.computed(()=>e.name||i.value);return n.provide(ze,{disabled:n.toRef(e,"disabled"),inline:n.toRef(e,"inline"),modelValue:o,multiple:n.computed(()=>!!e.multiple||e.multiple==null&&Array.isArray(o.value)),name:a,falseIcon:n.toRef(e,"falseIcon"),trueIcon:n.toRef(e,"trueIcon"),readonly:n.toRef(e,"readonly"),type:n.toRef(e,"type")}),j(()=>{var s;return n.createVNode("div",{class:"v-selection-control-group","aria-labelled-by":e.type==="radio"?i.value:void 0,role:e.type==="radio"?"radiogroup":void 0},[r==null||(s=r.default)==null?void 0:s.call(r)])}),{}}}),Fe=P(h(h({color:String,disabled:Boolean,error:Boolean,id:String,inline:Boolean,label:String,falseIcon:I,trueIcon:I,ripple:{type:Boolean,default:!0},multiple:{type:Boolean,default:null},name:String,readonly:Boolean,trueValue:null,falseValue:null,modelValue:null,type:String,value:null,valueComparator:{type:Function,default:de}},ee()),Se()));function $t(e){const t=n.inject(ze,void 0),{densityClasses:r}=$e(e),o=ne(e,"modelValue"),l=n.computed(()=>e.trueValue!==void 0?e.trueValue:e.value!==void 0?e.value:!0),i=n.computed(()=>e.falseValue!==void 0?e.falseValue:!1),a=n.computed(()=>(t==null?void 0:t.multiple.value)||!!e.multiple||e.multiple==null&&Array.isArray(o.value)),s=n.computed({get(){const p=t?t.modelValue.value:o.value;return a.value?p.some(c=>e.valueComparator(c,l.value)):e.valueComparator(p,l.value)},set(p){if(e.readonly)return;const c=p?l.value:i.value;let f=c;a.value&&(f=p?[...L(o.value),c]:L(o.value).filter(w=>!e.valueComparator(w,l.value))),t?t.modelValue.value=f:o.value=f}}),{textColorClasses:u,textColorStyles:d}=te(n.computed(()=>s.value&&!e.error&&!e.disabled?e.color:void 0)),m=n.computed(()=>{var p,c;return s.value?(p=t==null?void 0:t.trueIcon.value)!=null?p:e.trueIcon:(c=t==null?void 0:t.falseIcon.value)!=null?c:e.falseIcon});return{group:t,densityClasses:r,trueValue:l,falseValue:i,model:s,textColorClasses:u,textColorStyles:d,icon:m}}const Me=he()({name:"VSelectionControl",directives:{Ripple:vt},inheritAttrs:!1,props:Fe(),emits:{"update:modelValue":e=>!0},setup(e,t){let{attrs:r,slots:o}=t;const{densityClasses:l,group:i,icon:a,model:s,textColorClasses:u,textColorStyles:d,trueValue:m}=$t(e),p=N(),c=n.computed(()=>e.id||`input-${p}`),f=n.ref(!1),w=n.ref(!1),C=n.ref();function $(v){f.value=!0,(!we||we&&v.target.matches(":focus-visible"))&&(w.value=!0)}function E(){f.value=!1,w.value=!1}return j(()=>{var v,g,x,B;const A=o.label?o.label({label:e.label,props:{for:c.value}}):e.label,M=(v=i==null?void 0:i.type.value)!=null?v:e.type,[q,se]=me(r);return n.createVNode("div",n.mergeProps({class:["v-selection-control",{"v-selection-control--dirty":s.value,"v-selection-control--disabled":e.disabled,"v-selection-control--error":e.error,"v-selection-control--focused":f.value,"v-selection-control--focus-visible":w.value,"v-selection-control--inline":(i==null?void 0:i.inline.value)||e.inline},l.value]},q),[n.createVNode("div",{class:["v-selection-control__wrapper",u.value],style:d.value},[(g=o.default)==null?void 0:g.call(o),n.withDirectives(n.createVNode("div",{class:["v-selection-control__input"]},[a.value&&n.createVNode(oe,{icon:a.value},null),n.withDirectives(n.createVNode("input",n.mergeProps({"onUpdate:modelValue":ue=>s.value=ue,ref:C,disabled:e.disabled,id:c.value,onBlur:E,onFocus:$,"aria-readonly":e.readonly,type:M,value:m.value,name:(x=i==null?void 0:i.name.value)!=null?x:e.name,"aria-checked":M==="checkbox"?s.value:void 0},se),null),[[n.vModelDynamic,s.value]]),(B=o.input)==null?void 0:B.call(o,{model:s,textColorClasses:u,props:{onFocus:$,onBlur:E,id:c.value}})]),[[n.resolveDirective("ripple"),e.ripple&&[!e.disabled&&!e.readonly,null,["center","circle"]]]])]),A&&n.createVNode(je,{for:c.value},{default:()=>[A]})])}),{isFocused:f,input:C}}});function It(e){return X(e,Object.keys(Me.props))}const Et=V({name:"VRadio",props:{falseIcon:{type:I,default:"$radioOff"},trueIcon:{type:I,default:"$radioOn"}},setup(e,t){let{slots:r}=t;return j(()=>n.createVNode(Me,{class:"v-radio",trueIcon:e.trueIcon,falseIcon:e.falseIcon,type:"radio"},r)),{}}});var Kt="";const kt=V({name:"VRadioGroup",inheritAttrs:!1,props:T(h(h({height:{type:[Number,String],default:"auto"}},Re()),Fe()),{trueIcon:{type:I,default:"$radioOn"},falseIcon:{type:I,default:"$radioOff"},type:{type:String,default:"radio"}}),setup(e,t){let{attrs:r,slots:o}=t;const l=N(),i=n.computed(()=>e.id||`radio-group-${l}`);return j(()=>{const[a,s]=me(r),[u,d]=Vt(e),[m,p]=It(e),c=o.label?o.label({label:e.label,props:{for:i.value}}):e.label;return n.createVNode(Oe,n.mergeProps({class:"v-radio-group"},a,u,{id:i.value}),T(h({},o),{default:f=>{let{id:w,isDisabled:C,isReadonly:$}=f;return n.createVNode(n.Fragment,null,[c&&n.createVNode(je,{for:w.value},{default:()=>[c]}),n.createVNode(St,n.mergeProps(m,{id:w.value,trueIcon:e.trueIcon,falseIcon:e.falseIcon,type:e.type,disabled:C.value,readonly:$.value},s),o)])}}))}),{}}}),xt=n.defineComponent({name:"SubtitleSelector"}),Pt=n.defineComponent(T(h({},xt),{props:{subtitles:{type:Array,default:()=>[]}},setup(e){const t=e;return(r,o)=>(n.openBlock(),n.createBlock(n.unref(kt),null,{default:n.withCtx(()=>[(n.openBlock(!0),n.createElementBlock(n.Fragment,null,n.renderList(t.subtitles,l=>(n.openBlock(),n.createBlock(n.unref(Et),{key:l.no,color:"primary",label:`${l.remark}${l.title?"("+l.title+")":""}`,value:l},null,8,["label","value"]))),128))]),_:1}))}})),At=[n.createElementVNode("h1",null,"\u64AD\u653E\u52A0\u8F7D\u5931\u8D25\u4E86QAQ",-1)],Tt=n.defineComponent({name:"VideoEnhancePlayer"}),Lt=n.defineComponent(T(h({},Tt),{props:{url:{type:String,default:void 0},videoInfo:{type:Object,default:void 0},subtitleUrls:{type:Array,default:()=>[]}},setup(e){const t=e,r=n.ref();let o;const l=()=>{if(!t.url)return;const i={container:r.value,video:{url:t.url}},a=n.ref();t.videoInfo&&(t.videoInfo.subtitleStreamList.length&&(window.SfcUtils.snackbar(`\u68C0\u6D4B\u5230${t.videoInfo.subtitleStreamList.length}\u4E2A\u5B57\u5E55`),i.contextmenu=[{text:"\u9009\u62E9\u5B57\u5E55",click(){var s;window.SfcUtils.openComponentDialog(Pt,{props:n.reactive({subtitles:(s=t.videoInfo)==null?void 0:s.subtitleStreamList,"onUpdate:modelValue"(u){a.value=u},modelValue:a}),title:"\u9009\u62E9\u5B57\u5E55",contentMaxHeight:"360px",async onConfirm(){const u=o.video.currentTime,d=t.subtitleUrls.find(m=>m.no==a.value.no);return d?(i.subtitle={url:d.url,fontSize:"21px"},i.contextmenu[0].text="\u5B57\u5E55\uFF1A"+a.value.remark+(a.value.title?"("+a.value.title+")":""),console.log(i.contextmenu),o.destroy(),o=new window.DPlayer(i),o.play(),o.seek(u),!0):(window.SfcUtils.snackbar("\u627E\u4E0D\u5230\u6D41\u7F16\u53F7\u4E3A"+a.value+"\u7684\u5B57\u5E55\u6D41"),!0)}})}}]),t.videoInfo.chapterList.length&&(i.highlight=t.videoInfo.chapterList.map(s=>({text:s.title,time:Number(s.start)/1e3})))),o=new window.DPlayer(i),o.play()};return n.onMounted(async()=>{await n.nextTick(),l()}),n.watch(()=>t.url,l),(i,a)=>(n.openBlock(),n.createElementBlock("div",{ref_key:"rootRef",ref:r},At,512))}})),ae=window.context,D=window.SfcUtils;function Nt(e,t,r){if(!t.name.endsWith(".mkv"))return null;const o={name:t.name,path:e.path,protocol:"subtitle",targetId:e.uid,stream:r};if(e.protocol=="main")return window.SfcUtils.getApiUrl(window.API.resource.getCommonResource(o));const l=e.getProtocolParams();return o.sourceProtocol=e.protocol,o.sourceId=l.id,Object.keys(l).filter(i=>i!="id").forEach(i=>{o[i]=l[i]}),window.SfcUtils.getApiUrl(window.API.resource.getCommonResource(o))}async function Dt(e,t){let r=t.path;r||(r=(await D.request(window.API.resource.parseNodeId(t.uid,t.node))).data.data);const o={name:t.name,path:r,targetId:e.uid,sourceProtocol:e.protocol},l=e.getProtocolParams();return o.sourceId=l.id,Object.keys(l).filter(i=>i!="id").forEach(i=>{o[i]=l[i]}),o}async function Bt(e,t){const r=await Dt(e,t);return r.protocol="videoInfo",(await await D.request(window.API.resource.getCommonResource(r))).data}const Ue={id:"player",icon:"mdi-play-circle",matcher(e,t){if(t.mount)return!1;const r=new Set(["mp4","mkv","avi","rm","rmvb","m4v","flv","mpg","mpeg","mpe"]),o=t.name.split(".").pop();return!!o&&r.has(o)},title:"\u64AD\u653E\u89C6\u9891",async action(e,t){try{D.beginLoading(),await window.SfcUtils.sleep(100);const r=await Bt(e,t);D.openComponentDialog(Lt,{props:{url:e.getFileUrl(t),videoInfo:r,subtitleUrls:r.subtitleStreamList.map(o=>({no:o.no,url:Nt(e,t,o.no)}))},dense:!0,showCancel:!1,title:"\u89C6\u9891\u9884\u89C8\uFF1A"+t.name,extraDialogOptions:{maxWidth:"80%"}})}catch(r){D.snackbar(r)}finally{D.closeLoading()}},sort:0},We=ae.fileOpenHandler.value.findIndex(e=>e.id=="play-video");We!=-1?ae.fileOpenHandler.value[We]=Ue:ae.fileOpenHandler.value.push(Ue)});
