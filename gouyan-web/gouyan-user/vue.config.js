module.exports = {
    css: {
        loaderOptions: {
            sass: { implementation: require('sass') }
        }
    },
    devServer: {
        port: 8080
    },
    chainWebpack: config => {
        // 设置页面标题
        config.plugin('html').tap(args => {
            args[0].title = '狗眼电影'; // 在这里设置你想要的标题
            return args;
        });
    }
}