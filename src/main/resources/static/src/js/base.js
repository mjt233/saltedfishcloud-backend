aoej = {
    imgDOM:null,
    maskDOM:null,
    loading_cnt:0,
    load(){
        if(++this.loading_cnt > 0){
            this.imgDOM.show()
            this.maskDOM.show()
        }
    },
    init(){
        this.imgDOM = insertCenterContainer("<img src=\"/src/imgs/bg/奥尔加跑步.gif\">")
        this.maskDOM = insertMask();
    },
    finish(){
        if(--this.loading_cnt <= 0){
            this.imgDOM.hid()
            this.maskDOM.hid()
        }
    }
}
aoej.init()
window.addEventListener("load",()=>{
    // 渲染input
    var inputs = document.querySelectorAll(".input-group>input,.input-group>textarea")
    for (let i = 0; i < inputs.length; i++) {
        const elem = inputs[i];
        elem.addEventListener("focus", ()=>{
            elem.parentElement.classList.add("active")
        })
        elem.addEventListener("blur", ()=>{
            if(elem.value == ''){
                elem.parentElement.classList.remove("active")
            }
        })
    }

    // 渲染菜单
    document.querySelectorAll(".sub-menu span").forEach((e)=>{
        let cl = e.parentNode.classList
        e.addEventListener("click",()=>{
            if(cl.contains("active")){
                cl.remove("active")
            }else{
                cl.add("active")
            }
        })
    })
    var items = document.querySelectorAll(".menu-item")
    items.forEach((e)=>{
        let cl = e.classList
        e.addEventListener("click",()=>{
            items.forEach((e2)=>{
                e2.classList.remove("active")
            })
            if(cl.contains("active")){
                cl.remove("active")
            }else{
                cl.add("active")
            }
        })
    })
})


function getQueryString(name){

    var result = location.search.match(new RegExp("[\?\&]" + name+ "=([^\&]+)","i"));

    if(result == null || result.length < 1){

        return "";

    }

    return result[1];

}
function $confirm(title,message,callback){
    var frame = getAlertFrame(title,message)
    var ok = document.createElement("button")
    var cancel = document.createElement("button")
    frame.footer.appendChild(ok)
    frame.footer.appendChild(cancel)

    ok.innerText = "确定"
    ok.style.marginRight = "10px"
    ok.className = "primary"
    cancel.innerText = "取消"
    // cancel.className = "danger"
    ok.addEventListener("click",()=>{
        frame.close(callback,true)
    })
    cancel.addEventListener("click",()=>{
        frame.close(callback,false)
    })
}

/**
 *
 * @param {string} title
 * @param {string} message
 * @param {function} callback
 */
function $alert(title,message,callback){
    var btn = document.createElement("button")
    var content = getAlertFrame(title,message)
    content.footer.appendChild(btn)
    btn.focus()
    btn.classList.add("primary")
    btn.innerText = "确定"
    btn.addEventListener("click",()=>{
        content.close(callback)
    })
}

/**
 * @return {HTMLDivElement}
 */
function getAlertFrame(title,message){
    var mask = insertMask()
    mask.show()
    var frame = document.createElement("div")
    var content =   "<div class=\"message-title\">" + title + "</div>" +
            "<div class=\"message-content\"><span>" + message + "</span></div>" +
            "<div class=\"message-footer\"></div>"
    frame.classList.add("message-frame")
    frame.innerHTML = content
    frame.content = frame.getElementsByClassName("message-content")[0]
    frame.footer = frame.getElementsByClassName("message-footer")[0]
    var container = insertCenterContainer(frame)
    container.show()
    frame.hid = ()=>{
        frame.classList.add("hid")
    }
    var closed = false
    frame.close = (callback,callback_prop)=>{
        // 防止过渡期间被多次触发
        if(!closed){
            closed = true
        }else{
            return
        }
        frame.hid()
        container.remove()
        mask.remove()
        if(callback!=undefined){
            if(callback_prop != undefined){
                callback(callback_prop)
            }else{
                callback()
            }
        }
    }
    return frame
}

function insertMask(){
    var mask = document.createElement("div")
    mask.classList.add("bg-mask")
    document.documentElement.appendChild(mask)
    mask.remove = ()=>{
        mask.classList.remove("show")
        setTimeout(() => {
            document.documentElement.removeChild(mask)
        }, 200);
    }
    mask.hid = ()=>{
        mask.classList.remove("show")
    }
    mask.show = ()=>{
        mask.classList.add("show")
    }
    return mask;
}

function insertCenterContainer(content){
    var container = document.createElement("div")
    container.classList.add("center-container")
    if(typeof(content) == "string"){
        container.innerHTML = content
    }else{
        container.appendChild(content)
    }
    document.documentElement.appendChild(container)
    container.remove = ()=>{
        container.hid()
        setTimeout(() => {
            document.documentElement.removeChild(container)
        }, 200);
    }

    container.hid = ()=>{
        container.classList.remove("show")
    }
    container.show = ()=>{
        container.classList.add("show")
    }
    return container
}

function startload(){

}
