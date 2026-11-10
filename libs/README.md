# 编译依赖（不随仓库提交）

构建脚本需要 Folia 26.3 服务端的类路径。请把以下文件复制到本目录：

1. 服务器 `libraries/` 目录下**所有** `.jar`（可递归复制，重名覆盖即可）；
2. `versions/26.3/folia-26.3.jar`。

示例（PowerShell，路径按你的服务器改）：

```powershell
$server = 'D:\Niko-Server2.0\Niko-FoilaServer'
Get-ChildItem "$server\libraries" -Recurse -Filter *.jar |
    Copy-Item -Destination . -Force
Copy-Item "$server\versions\26.3\folia-26.3.jar" . -Force
```

然后回到仓库根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File tools/build-plugin.ps1
```

> 这些 jar 属于 Mojang/Paper 及其第三方依赖，受各自许可证约束，**不要提交到公开仓库**。
> `.gitignore` 已忽略 `libs/*.jar`。
