/**
 * 简易Ajax封装
 * version v233
 * author xiaotao
 *
 */

/**
 *
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
 * @param {*} data 表单数据
 * @param {callback} callback 响应回调函数
 * @param {callback} error_callback 网络错误回调函数，默认使用参数callback的值
 * @return {XMLHttpRequest}
 */
function $ajax2({type,url,data,callback,error_callback}){
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
