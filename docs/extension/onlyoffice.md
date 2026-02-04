# OnlyOffice

## 简介

集成OnlyOffice，实现直接在浏览器上在线预览和编辑docx、ppt、xlsx文件



## 注意事项

1. 本插件需要依赖第三方文档服务-ONLYOFFICE
2. 可参考官方文档 [获取ONLYOFFICE服务](https://api.onlyoffice.com/zh/editors/getdocs)
3. 其他小建议：推荐使用 [Docker方式部署](https://helpcenter.onlyoffice.com/installation/docs-community-install-docker.aspx)
4. 基于安全考虑，在非内部网络环境中不建议关闭ONLYOFFICE服务的JWT功能。

   暴露于公网的ONLYOFFICE和咸鱼云的ONLYOFFICE文件保存回调程序将无法验证请求是否合法，存在严重安全风险。

## Docker方式安装简单案例

> 这里仅作简单示例，详细说明见 [官方文档](https://helpcenter.onlyoffice.com/installation/docs-community-install-docker.aspx)
1. 获取镜像
   ```shell
   $ docker pull onlyoffice/documentserver
   ```

2. 创建容器

- 使用HTTP
  ```shell
  $ docker run -i -t -d -p 本机端口号:80 --restart=always -e JWT_SECRET=JWT密钥 onlyoffice/documentserver
  ```

- 使用HTTP + HTTPS
  ```shell
  $ docker run -i -t -d -p 本机端口号:80 -p 本机端口号:443 \\
  -v 本机的ONLYOFFICE数据目录:/var/www/onlyoffice/Data \\
  --restart=always -e JWT_SECRET=JWT密钥 \\
  onlyoffice/documentserver
  ```
  需要确保\`本机的ONLYOFFICE数据目录/certs/onlyoffice.key\`和\`本机的ONLYOFFICE数据目录/certs/onlyoffice.crt\`下存在对应的HTTPS证书文件

