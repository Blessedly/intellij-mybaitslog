# MyBatis Log EasyPlus

本项目 fork 自 [Link-Kou/intellij-mybaitslog](https://github.com/Link-Kou/intellij-mybaitslog)，原作者为 **Link-Kou（linkkou）**。感谢原作者提供的开源插件。

本 fork 基于原版 **5.0.8.2** 修复并改进，当前版本为 **5.0.8.4**。

## 修改内容

- 修复 SQL 输出时缺少 `org.apache.commons.lang.StringUtils` 引发的 `NoClassDefFoundError`，移除这项隐式依赖。
- 优化 SQL 排版：结构关键字大写、四空格缩进、字段和条件分行、分页子句分行、清理行尾空格，函数参数保持同行。
- 格式化模式下移除 SQL 首行的装饰前缀，方便复制 SQL。
- 保留字符串参数中的换行、制表符及引用标识符的原始内容；含注释、美元引用或特殊转义的 SQL 保留原文，格式化过程出现已处理的解析异常时回退到原文。
- 增加回归验证和 Windows 兼容打包脚本 `build-compat.ps1`。

## 测试环境

- 系统：Windows
- IntelliJ IDEA：**2026.2.3 Ultimate**，Build **IU-262.10968.63**
- 插件版本：**5.0.8.4**
- 验证日期：**2026-10-16**

## 测试结果

- **自动化验证通过**：9 项 SQL 类型识别验证、24 项 SQL 排版验证，共 33 项；编译结果与最终安装包中的 JAR 均通过验证。
- **原问题复现与修复验证通过**：模拟插件类加载隔离后，原版可复现缺少 `StringUtils` 的异常，修复版通过验证。
- **实际使用确认通过**：在上述 IDEA 环境安装插件后，SQL 输出正常；在 MyBatis Log 窗口点击漏斗图标 **Filter**，勾选 **Format SQL** 并确认后，新输出的 SQL 格式化生效，已由使用者确认。

兼容安装包仅重新编译修改涉及的类，其余组件复用原版插件二进制，未执行完整 Gradle 构建。上述结果仅代表已验证的环境，不代表所有 IDEA 版本和数据库方言均已验证。
