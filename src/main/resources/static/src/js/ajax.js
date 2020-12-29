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
 * @param {callback} prog_callback 进度回调
 * @return {XMLHttpRequest}
 */
function $ajax(type,url,data,callback,error_callback,prog_callback){
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
        if(error_callback != undefined){
            error_callback(ajax)
        }else{
            callback(ajax)
        }
    }
    ajax.upload.addEventListener("progress",prog_callback ? prog_callback:null)
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
        if(data && data.constructor.name == "FormData"){
            ajax.send(data)
            return
        }
        var fd = new FormData
        for (const key in data) {
            fd.append(key,data[key])
        }
        ajax.send(fd)
    }else{
        ajax.send()
    }
}
