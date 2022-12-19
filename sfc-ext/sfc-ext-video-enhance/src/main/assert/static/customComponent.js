window.bootContext.addProcessor({
    taskName: '注册VE组件',
    onFinish(app) {
        app.component('ffmpeg-configure', {
            render() {
                return window.Vue.h('div', {
                    onClick: () => {
                        console.log(this)
                        window.SfcUtils.alert(this.value, '编译选项')
                    },
                    class: 'link'
                }, '查看')
            },
            props: ['value'],
            methods: {
                showInfo() {
                    window.SfcUtils.alert(this.value)
                }
            }
        })
    }
})
