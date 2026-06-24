# PhotoProjectManager

Java Swing 照片项目管理系统，支持项目管理、选片九宫格生成、百度网盘集成、多语言（中文/英文/日语）。

## 功能特性

-  项目浏览与文件管理
-  九宫格 PDF 生成（支持背景图/纯色背景）
-  百度网盘授权与上传
- 项目压缩包管理
-  多语言支持（中文/English/日本語）
-  工期排单管理

## 环境要求

- JDK 1.8+
- SQLite（内嵌）

## 配置

1. 首次运行会在 `data/` 目录生成 `app_config.properties`
2. 如需使用百度网盘功能，在菜单「百度网盘」→「设置 API Key」中填入：
   - App Key
   - Secret Key
   - Access Token（可选，也可通过 OAuth 获取）

## 构建

```bash
mvn clean package
```

或直接运行 `PhotoProjectManager.jar`。

## 目录结构

```
src/
  main/
    java/com/photomanager/   # 源代码
    resources/                # 语言文件、配置模板
data/                         # 运行时数据（不提交到仓库）
  app_config.properties      # 本地配置，含敏感信息
  photo_manager.db           # SQLite 数据库
```
