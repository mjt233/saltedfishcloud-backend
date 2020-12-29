XTColor = {
    "primary":"#409EFF",
    0:"#409EFF",
    "success":"#67C23A",
    1:"#67C23A",
    "warning":"#E6A23C",
    2:"#E6A23C",
    "danger":"#F56C6C",
    3:"#F56C6C"
}

/**
 * 取后缀
 * @param {string} file
 */
function getSuffix(file){
    var t = file.split('.')
    if(t.length >= 2){
        return t.pop().toLowerCase()
    }else{
        return ''
    }
}
Vue.prototype.getSuffix = getSuffix
Vue.prototype.XTColor = XTColor

Vue.prototype.getSizeString = function(size){
    var showSize = size;
    var suffix = "Byte";
    if(size > 1024 && size <= 1048576){
        suffix = "KiB";
        showSize = size/1024;
    }else if(size > 1048576 && size <= 1073741824){
        suffix = "MiB";
        showSize = size/1048576;
    }else if(size > 1073741824){
        suffix = "GiB";
        showSize = size/1073741824;
    }
    return showSize.toFixed(2) + suffix
}
function getSizeString(size){
    return Vue.prototype.getSizeString(size)
}

function formatDate(date,fmt){
    if(fmt == undefined){
        fmt = 'yyyy-MM-dd hh:mm'
    }
    if(typeof(date) == 'number'){
        date = new Date(Number)
    }
    if(date == undefined){
        date = new Date()
    }

    if (/(y+)/.test(fmt)) {
        fmt = fmt.replace(RegExp.$1, (date.getFullYear() + '').substr(4 - RegExp.$1.length));
    }
    let o = {
        'M+': date.getMonth() + 1,
        'd+': date.getDate(),
        'h+': date.getHours(),
        'm+': date.getMinutes(),
        's+': date.getSeconds()
    };
    for (let k in o) {
        if (new RegExp(`(${k})`).test(fmt)) {
            let str = o[k] + '';
            fmt = fmt.replace(RegExp.$1, (RegExp.$1.length === 1) ? str : padLeftZero(str));
        }
    }
    return fmt;
}
function padLeftZero(str) {
    return ('00' + str).substr(str.length);
}
function getMsgBoxFrame(title,content){
    var frame = document.createElement("div")
    frame.classList="xt-msg-box-frame"
    frame.innerHTML=`
        <div class="xt-msg-box-title">` + title + `</div>
        <div class="xt-msg-box-body">` + content + `</div>
        <div class="xt-msg-box-footer"></div>
    `
    frame.getTitle = frame.querySelector(".xt-msg-box-title")
    frame.getBody = frame.querySelector(".xt-msg-box-body")
    frame.getFooter = frame.querySelector(".xt-msg-box-footer")
    return frame
}
Vue.prototype.formatDate = formatDate;
function makeMaskContainer(){
    var mask = document.createElement("div")
    var container = document.createElement("div")
    mask.classList='xt-global-mask'
    container.classList='xt-container-center'
    document.body.appendChild(mask)
    document.body.appendChild(container)
    return {
        remove(){
            mask.classList.add("xt-scale-out")
            container.classList.add("xt-scale-out")
            setTimeout(() => {
                mask.remove()
                container.remove()
            }, 300);
        },
        innerHTML(html){
            container.innerHTML = html
        },
        appendChild(c){
            container.appendChild(c)
        }
    }
}
function makeMsgBox(title,content,opations){
    if(opations == undefined){
        opations = {}
    }
    var container = makeMaskContainer()
    var frame = getMsgBoxFrame(title,content)
    var btn = document.createElement("button")
    btn.classList = "xt-msg-box-btn"
    btn.innerText = opations.confirmText == undefined ? "确定" : opations.confirmText
    frame.getFooter.appendChild(btn)
    container.appendChild(frame)
    var clicked = false
    frame.remove = ()=>{
        container.remove()
    }
    btn.addEventListener('click',()=>{
        if(clicked){
            return
        }
        clicked = true
        if(opations.callback!=undefined){
            opations.callback(true)
        }
        frame.remove()
    })
    return frame
}
Vue.prototype.alert = function (title,content,opations){
    makeMsgBox(title,content,opations)
}
Vue.prototype.confirm = function (title,content,opations){
    var box = makeMsgBox(title,content,opations)
    var btn =document.createElement("button")
    btn.innerText = "取消"
    btn.setAttribute("style","background-color:white;border-color:#eee;color:rgb(120,120,120);margin-left: 5px")
    var clicked = false
    btn.addEventListener('click',()=>{
        if(clicked){
            return
        }
        clicked = true
        if(opations != undefined && opations.callback!=undefined){
            opations.callback(false)
        }
        box.remove()
    })
    box.getFooter.appendChild(btn)
}


Vue.component('xt-card',{
    props : ['title','type','contentstyle'],
    template:`
    <div class="xt-card xt-scale-in">
        <h2 class="title" :style="{backgroundColor:XTColor[type]}">{{title}}</h2>
        <div class="content" :style="contentstyle">
            <slot></slot>
        </div>
    </div>`
})

Vue.component('xt-prog',{
    props:{
        total: [Number, String],
        data:Array,
        value: [Number, String],
        color:String,
        title:String
    },
    data:()=>{
        return {
            hid:false
        }
    },
    template:`
    <div class="xt-prog" :style="hid?'width:0%':''">
        <div class="xt-prog-entry"
            v-if="value!=undefined && data==undefined"
            :style="{width:value/total*100 + '%',backgroundColor:color == undefined ? XTColor[0]: color}"
            :title=" title == undefined ? '占比：' + (value/total*100).toFixed(2) + '%' : title "
            >
        </div>
        <div class="xt-prog-entry"
            v-if="data!=undefined"
            v-for="(item,key) in data"
            :style="{width:item.value/total*100 + '%',backgroundColor:item.color == undefined ? XTColor[key%4] : item.color}"
            :title="item.title == undefined ? '占比：' + (item.value/total*100).toFixed(2) + '%'  : item.title"
            >
        </div>
    </div>
    `
})

Vue.component('xt-pagination',{
    props:{
        maxpage:{
            type:Number,
            default:()=>{
                return 0;
            }
        }
    },
    data:function(){
        return {
            currentPage:1,
            totalPage:0
        }
    },watch: {
        maxpage(n, o) {
            this.totalPage = n
        },
    },mounted(){
        if(this.maxpage != undefined){
            this.totalPage = this.maxpage
        }
    },
    template:`
    <div class="xt-pagination">
        <span @click="switchPage(currentPage - 1)" class="xt-pagination-item">上一页</span>
        <span @click="switchPage(key + 1)" :class="{selected:key==(currentPage-1)}" class="xt-pagination-item" v-for="(item,key) in maxpage">{{item}}</span>

        <span @click="switchPage(currentPage + 1)" class="xt-pagination-item">下一页</span>
    </div>
    `,methods: {
        switchPage(page) {
            if(page > this.totalPage || page < 1){
                return
            }else{
                this.currentPage = page
                this.$emit("page-change",page)
            }
        },
    },
})

Vue.component('xt-btn',{
    props:['type'],
    data:()=>{
        return {
            // type:""
        }
    },
    template:`
    <button @click="$emit('click')" :class="type" class="xt-btn"><slot></slot></button>
    `
})

Vue.component('xt-input',{
    props:[
        'placeholder','value','name','type','autocomplete'
    ],
    data:function () {
        return{
            active:false
        }
    },
    template:`
    <div class="xt-input-group" :class="{active:(value!=undefined && value.length!=0) || active}">
        <span>{{placeholder}}</span>
        <input
            v-bind:value="value"
            v-bind:name="name"
            v-bind:type="type"
            v-bind:autocomplete="autocomplete"
            @input="$emit('input', $event.target.value)"
            @focus="focus"
            @blur="blur">
    </div>
    `,methods:{
        focus(){
            this.active = true
            this.$emit('focus')
        },
        blur(){
            if(this.value == undefined || this.value.length == 0){
                this.active = false
            }
            this.$emit('blur')
        }
    }
})

Vue.component('xt-menu-switch',{
    template:`
        <button @click='click' class="xt-menu-switch" :class="{active:active}">
            <span></span>
        </button>
    `,data:()=>{
        return {
            active:false
        }
    },methods: {
        click() {
            this.active = !this.active
            this.$emit('click', this.active)
        },
    },
})

/**
 *
 * 简易Ajax封装
 * version v233
 * author xiaotao
 * @param {string} type 请求类型
 * @param {string} url 请求URL
 * @param {*} data 表单数据
 * @param {callback} callback 响应回调函数
 * @param {callback} error_callback 网络错误回调函数，默认使用参数callback的值
 * @return {XMLHttpRequest}
 */
function $ajax(type,url,data,callback,error_callback){
    var ajax;
    // 创建ajax对象
    if(window.XMLHttpRequest){
        ajax = new XMLHttpRequest()
    }else{
        ajax = new ActiveXObject("Microsoft.XMLHTTP")
    }
    // 添加事件监听
    ajax.addEventListener('load',()=>{
        var ct = ajax.getResponseHeader('Content-Type')
        ajax.data = ajax.response
        // 自动解析json
        if(ct != null && ct.indexOf('json') != -1){
            try {
                ajax.data = JSON.parse(ajax.data)
            } catch (error) {}
        }
        callback(ajax)
    })
    ajax.onerror = ()=>{
        alert("错误")
        if(error_callback != undefined){
            error_callback(ajax)
        }else{
            callback(ajax)
        }
    }
    // 核心处理
    if(type=='GET'){
        // GET类型的请求自动把data拼接到URL上
        if(url.indexOf('?')==-1){ url+='?' }else{url+='&'}
        var hasData = false
        for (const key in data) {
            hasData = true
            const value = data[key]
            url+=key+'='+value+'&'
        }
        if(hasData){ url = url.substring(0,url.length-1) }
    }
    ajax.open(type,url,true)
    ajax.setRequestHeader("X-Requested-With","XMLHttpRequest")
    if(type=='POST' || type=='post'){
        var fd = new FormData
        for (const key in data) {
            fd.append(key,data[key])
        }
        ajax.send(fd)
    }else{
        ajax.send()
    }
}


/**
 *
 * @param {string} type 请求类型
 * @param {string} url 请求URL
 * @param {object} opation 选项参数
 * @return {XMLHttpRequest}
 */
function majax(type, url, opation) {
    if (opation == undefined) {
        opation = {}
    }
    var ajax;
    // 创建ajax对象
    if (window.XMLHttpRequest) {
        ajax = new XMLHttpRequest()
    } else {
        ajax = new ActiveXObject("Microsoft.XMLHTTP")
    }

    // 添加事件监听 - 请求完成
    ajax.addEventListener('load', () => {
        var ct = ajax.getResponseHeader('Content-Type')
        ajax.data = ajax.response
        // 自动解析json
        if (ct != null && ct.indexOf('json') != -1) {
            try {
                ajax.data = JSON.parse(ajax.data)
            } catch (error) { }
        }
        opation.success ? opation.success(ajax) : ''
    })
    // 添加事件监听 - 请求出错
    ajax.onerror = () => {
        opation.error ? opation.error(ajax) : opation.success ? opation.success(ajax) : null
    }
    // 添加事件监听 - 发送请求过程
    ajax.upload.addEventListener("progress", opation.prog ? opation.prog : null)


    // 添加请求数据 - GET
    if (type == 'GET' || type == 'get') {
        // GET类型的请求自动把data拼接到URL上
        // 若URL中已经有GET参数了，就直接在后面加&否则就加?
        if (url.indexOf('?') == -1) { url += '?' } else { url += '&' }

        // 将数据对象序列化为QueryString拼接到URL中
        for (const key in opation.data) {
            const value = opation.data[key]
            url += key + '=' + value + '&'
        }
        if (opation.data) { url = url.substring(0, url.length - 1) }
    }

    ajax.open(type, url, true)

    // 添加header
    if (opation.header) {
        for (const key in opation.header) {
            ajax.setRequestHeader(key, opation.header[key])
        }
    }
    ajax.setRequestHeader("X-Requested-With", "XMLHttpRequest")


    // 添加请求数据 - POST
    if (type == 'POST' || type == 'post') {
        if (opation.data && opation.data.constructor.name == "FormData") {
            ajax.send(opation.data)
            return
        }
        var fd = new FormData
        for (const key in opation.data) {
            fd.append(key, opation.data[key])
        }
        ajax.send(fd)
    } else {
        ajax.send()
    }
}
