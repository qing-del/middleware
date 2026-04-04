
---

# 目录
- [[#未识别]]
	- [[#没识别出是-idea-配置导致模块被忽略]]
	- [[#没识别出是-maven-实际-jdk-版本不一致]]
- [[#犯错]]
	- [[#只改了-pom-没有核对-maven-运行时-jdk]]
	- [[#只重启-idea-没有检查-maven-ignored-配置]]
- [[#标准排查流程5-分钟版]]
- [[#最终修复动作本次实战]]
- [[#预防清单下次照着做]]
- [[#复盘口诀]]

---

## 未识别

### 没识别出是 .idea 配置导致模块被忽略
- 现象：`middleware-common` 在 IDEA 的 Maven 视图里表现为被忽略/不参与导入。
- 关键线索：`.idea/misc.xml` 中 `MavenProjectsManager -> ignoredFiles` 包含了 `middleware-common/pom.xml`。
- 结论：这是 IDE 级别的忽略，不是 POM 写错。

### 没识别出是 Maven 实际 JDK 版本不一致
- 现象：
  - `[ERROR] 不支持发行版本 21`
- 关键线索：终端 `mvn -v` 显示 Java version 是 17。
- 结论：`pom.xml` 里写了 Java 21，但 Maven 运行时还是 17，导致编译器拒绝 `release 21`。

---

## 犯错

### 只改了 pom，没有核对 Maven 运行时 JDK
- 错误动作：看到编译错误后先怀疑依赖和插件配置。
- 正确动作：先执行 `mvn -v`，确认 Maven 正在使用的 Java 版本。
- 经验：构建错误先看“运行环境”，再看“项目配置”。

### 只重启 IDEA，没有检查 Maven ignored 配置
- 错误动作：多次重启 IDEA，希望自动恢复。
- 正确动作：检查 `.idea/misc.xml` 是否存在 `ignoredFiles` 条目。
- 经验：被忽略模块通常是“状态配置问题”，重启不一定能改状态。

---

## 标准排查流程（5 分钟版）
1. 先确认 Maven Java 版本：`mvn -v`
2. 确认项目目标版本：父 `pom.xml` 的 `java.version`
3. 若报 `不支持发行版本 21`：优先判断是否 Maven/JDK 不匹配
4. 检查 IDEA Maven 设置：
   - Maven Runner JRE 是否为 21+
   - Maven Importer JDK 是否为 21+
5. 检查 `.idea/misc.xml`：是否把目标模块放进 `ignoredFiles`
6. 修复后执行：Reload Maven Projects + 重新编译

---

## 最终修复动作（本次实战）
1. 安装 JDK 21（Temurin 21）
2. 设置 `JAVA_HOME` 到 JDK 21
3. 确认 `mvn -v` 输出 Java 21
4. 删除 `.idea/misc.xml` 中对 `middleware-common/pom.xml` 的忽略
5. 将 `.idea/workspace.xml` 的 `jdkForImporter` 调整为 21
6. 重新执行模块编译，结果 `BUILD SUCCESS`

---

## 预防清单（下次照着做）
- 新项目第一天就执行一次：`java -version`、`mvn -v`
- 每次升级 Java 后，统一检查：
  - 系统 `JAVA_HOME`
  - IDEA Project SDK
  - IDEA Maven Runner JRE
  - IDEA Maven Importer JDK
- 遇到“模块被忽略”，第一反应检查 `.idea/misc.xml` 的 `ignoredFiles`
- 遇到“release xx 不支持”，第一反应检查 `mvn -v` 的 Java version

---

## 复盘口诀
- `先环境，后配置；先版本，后依赖。`
- `模块被忽略，看 .idea；release 报错，看 mvn -v。`
