Vue.component('tp-menu',{
    props:['title'],
    data:() => {
        return {
            show:true
        }
    },
    template:`
        <div class="tp-menu" :class="{hid:!show}">
            <p @click="show = !show" class="title">{{title}}</p>
            <div class="tp-menu-content">
                <slot></slot>
            </div>
        </div>
    `
})

