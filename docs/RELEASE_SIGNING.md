# Release 签名与 CI 发布包

本文件说明如何生成、保管 release keystore，以及如何通过 GitHub Actions
`.github/workflows/release.yml` 构建签名 APK。

> 安全边界：release keystore 和密码只保存在仓库所有者控制的密码管理器与
> GitHub Actions Secrets 中，**不进入仓库、不走 PR、不贴到聊天/Issue**。
> keystore 丢失后无法再更新同一 `applicationId` 的应用；泄露后必须立即轮换。

## 1. 生成 keystore（由仓库所有者在本机执行）

```bash
mkdir -p ~/secure/ai-offer-radar
keytool -genkeypair -v \
  -keystore ~/secure/ai-offer-radar/release.jks \
  -alias ai-offer-radar \
  -keyalg RSA -keysize 4096 -validity 10000
```

按提示设置 store password 和 key password（可以相同，但建议不同），并把
keystore、alias、两个密码保存到密码管理器。不要执行 `git add`。

## 2. 配置 GitHub Actions Secrets

仓库 Settings → Secrets and variables → Actions → New repository secret：

| Secret | 内容 |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 < release.jks \| tr -d '\n'` 的输出 |
| `RELEASE_KEYSTORE_PASSWORD` | store password |
| `RELEASE_KEY_ALIAS` | `ai-offer-radar` |
| `RELEASE_KEY_PASSWORD` | key password |

也可以用已认证的 `gh`：

```bash
base64 < ~/secure/ai-offer-radar/release.jks | tr -d '\n' | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_KEYSTORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEY_PASSWORD
```

四条 secret 缺任意一条时，`sign_release=true` 的 workflow 会在构建前明确失败；
不会静默产出未签名包。

## 3. 触发签名构建

```bash
gh workflow run release.yml \
  -f version_name=1.0.1 \
  -f version_code=2 \
  -f endpoint_url=https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json \
  -f sign_release=true
```

workflow 会：

1. 解码 keystore 到 runner 临时目录（权限 600），构建后随 runner 销毁；
2. 执行 `testDebugUnitTest` 和 `assembleRelease`，注入 endpoint 与版本号；
3. 用 Gradle signingConfig 签名；
4. `apksigner verify --print-certs` 验证签名；
5. 输出 APK SHA-256 到 job summary，并上传 artifact `ai-offer-radar-release`（保留 14 天）。

只验证构建链路、不接触密钥时，可用 `sign_release=false` 产出 unsigned APK。

## 3.1 已验证 / 未验证

- 已验证（2026-10-31 23:26–23:29）：`sign_release=false` 远端构建成功，
  versionCode 2 / versionName 1.0.1-ci，APK SHA-256
  `cf32651adbfc989c840c489c00b0f48c18bee3e2a2049e254cf07f74faf39307`，
  `apksigner` 显示 `DOES NOT VERIFY`（符合 unsigned）。
- 已验证：`sign_release=true` 但四个 Secrets 未配置时，workflow 在构建前
  22s 失败，并逐项报告缺失的 Secret；不会静默产出 unsigned 包。
- 已验证（2026-10-31 23:40–23:48，本机）：使用新生成的 release key 构建
  versionCode 1 / versionName 1.0.0；v2 签名通过，证书 SHA-256
  `54f6ee32cb7a7d28ed89e5ea131f1e134904b4eeb97dad7b02f238260d19979e`；
  APK SHA-256 `376be6952ccb984b72e197646ce1ceb48c16d085b3b4fc905cf82d3c47dbca36`；
  已安装到 PMX110 / Android 17，官方模式通过线上 endpoint 成功同步。
- 已验证（2026-11-01 00:43–00:46，GitHub Actions）：四个 Secrets 配置后触发
  `release.yml` 的 `sign_release=true`，[run 41221071911](https://github.com/yuntianming1874-design/ai-offer-radar/actions/runs/41221071911)
  成功（1m59s）。artifact `ai-offer-radar-1.0.0-signed.apk`，SHA-256
  `b08b6208691f567cfec88ff4a555dd57319c13676509816fb94e88ab420aaf27`；
  v2 签名通过，证书 SHA-256 仍为
  `54f6ee32cb7a7d28ed89e5ea131f1e134904b4eeb97dad7b02f238260d19979e`。
  CI 与本地 APK 哈希不同但证书一致；CI artifact 已安装到 PMX110，设备内 APK
  SHA-256 `b08b6208691f567cfec88ff4a555dd57319c13676509816fb94e88ab420aaf27`
  与 artifact 完全一致，官方模式同步成功。

## 4. 本地签名构建

密码通过环境变量提供，不写进命令行历史：

```bash
export RELEASE_KEYSTORE_PATH="$HOME/secure/ai-offer-radar/release.jks"
export RELEASE_KEYSTORE_PASSWORD='...'
export RELEASE_KEY_ALIAS='ai-offer-radar'
export RELEASE_KEY_PASSWORD='...'

./gradlew --offline --no-daemon assembleRelease \
  -PglmCollectorUrl='https://yuntianming1874-design.github.io/ai-offer-radar/glm-coding-plan.json' \
  -PreleaseVersionCode=2 \
  -PreleaseVersionName='1.0.1'

find app/build/outputs/apk/release -name '*.apk'
```

没有这四个环境变量时，Gradle 保持 `release` 未签名，普通本地/CI 构建不受影响。

## 5. 签名验证与安装

```bash
APKSIGNER=$(find "$ANDROID_HOME/build-tools" -name apksigner | sort -V | tail -1)
"$APKSIGNER" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

若设备上已有 debug 签名或旧证书签名包，必须先卸载旧包；Android 不允许证书
不同的同包名覆盖安装。

## 6. 版本与发布纪律

- `versionCode` 必须是正整数且每次发布递增；`versionName` 只用于展示。
- 先用 `sign_release=false` 验证 endpoint/构建，再触发签名发布。
- 签名构建成功后，把版本号、commit、endpoint、APK SHA-256 和 workflow run
  链接写入 `docs/evidence/`；不要只保留 artifact（14 天后过期）。
- 正式上架/分发仍需 Play 配置、隐私政策和 release notes，本骨架不包含这些。
