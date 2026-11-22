package com.example.system

/**
 * Self-contained 100% offline responsive HTML/CSS/JS dashboard served directly by the dictionary pen's
 * LAN Web Admin Server. Zero external CDN dependencies (never blocked by campus firewalls) and strict
 * safe DOM bindings (textContent/createElement only; auth token held strictly in JS memory).
 */
object LanWebConsoleHtml {

    val indexHtml: String = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>PenOS 5.0 局域网控制台</title>
<style>
:root{--bg:#0b0e14;--card:#141a26;--line:#263044;--ink:#f4f7fb;--muted:#93a1b8;--accent:#38bdf8;--coral:#ff6b4a;--ok:#34d399;--danger:#f43f5e}
*{box-sizing:border-box;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif}
body{margin:0;background:var(--bg);color:var(--ink);padding:16px}
.wrap{max-width:980px;margin:0 auto}
.top{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap;margin-bottom:16px}
.title{font-size:22px;font-weight:700;letter-spacing:-.3px}
.sub{color:var(--muted);font-size:13px}
.badge{background:#1b2638;border:1px solid var(--line);color:var(--accent);padding:5px 10px;border-radius:999px;font-size:12px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin-bottom:14px}
.card h3{margin:0 0 12px;font-size:15px;color:#fff}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:12px}
label{display:block;font-size:12px;color:var(--muted);margin:8px 0 4px}
input,select,textarea{width:100%;background:#0c111b;border:1px solid var(--line);color:var(--ink);padding:8px 10px;border-radius:10px;font-size:13px}
textarea{min-height:84px;resize:vertical}
.row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
button{background:var(--accent);color:#05141f;border:none;padding:8px 14px;border-radius:10px;font-weight:600;font-size:13px;cursor:pointer}
button.sec{background:#1f293d;color:var(--ink);border:1px solid var(--line)}
button.coral{background:var(--coral);color:#fff}
button.danger{background:var(--danger);color:#fff}
.tabs{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}
.tab{background:#161e2e;color:var(--muted);border:1px solid var(--line)}
.tab.active{background:var(--accent);color:#04131c;border-color:var(--accent)}
.item{background:#0e1420;border:1px solid var(--line);border-radius:12px;padding:10px;margin-top:8px;display:flex;justify-content:space-between;align-items:center;gap:8px}
.mono{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px}
.toast{position:fixed;bottom:18px;right:18px;background:#111a2b;border:1px solid var(--accent);color:#fff;padding:10px 14px;border-radius:12px;display:none;z-index:99;max-width:340px;font-size:13px}
.hidden{display:none!important}
</style>
</head>
<body>
<div class="wrap">
  <div class="top">
    <div>
      <div class="title">PenOS 5.0 词典笔局域网控制台</div>
      <div class="sub">仅限局域网 / ADB USB 转发安全访问 · 零外部依赖 · 支持快照回滚与全量配置备份</div>
    </div>
    <div id="statusBadge" class="badge">未认证</div>
  </div>

  <div id="loginBox" class="card" style="max-width:420px;margin:48px auto">
    <h3>输入笔端 6 位配对码</h3>
    <p class="sub">请在词典笔的「设置向导 / 实验室 / 控制中心」查看当前 6 位 PIN 码。</p>
    <label>6 位配对 PIN</label>
    <input id="pinInput" type="password" inputmode="numeric" maxlength="8" placeholder="例如 482910">
    <div class="row" style="margin-top:12px">
      <button id="loginBtn">连接词典笔</button>
    </div>
  </div>

  <div id="appBox" class="hidden">
    <div class="tabs">
      <button class="tab active" data-tab="appear">外观/壁纸/时钟</button>
      <button class="tab" data-tab="fonts">5通道全局字体</button>
      <button class="tab" data-tab="ai">多 AI 预设 &amp; MCP</button>
      <button class="tab" data-tab="widgets">自定义小组件 &amp; 模板</button>
      <button class="tab" data-tab="mail">IMAP/SMTP 邮箱</button>
      <button class="tab" data-tab="backup">快照回滚 / 配置备份</button>
    </div>

    <div id="pane-appear" class="pane">
      <div class="grid">
        <div class="card">
          <h3>主题与 60fps 动效档位</h3>
          <label>桌面设计风格</label>
          <select id="designStyle">
            <option value="dark_standby">深色 StandBy CarPlay</option>
            <option value="apple_light">浅色 Apple 极简白画廊</option>
          </select>
          <label>StandBy 息屏风格</label>
          <select id="standbyStyle">
            <option value="oled_dark">纯黑 OLED 护眼</option>
            <option value="apple_light">极简白画廊</option>
            <option value="follow">跟随桌面</option>
          </select>
          <label>动画流畅度档位（解决 MT6580 卡顿）</label>
          <select id="motionTier">
            <option value="fast">极速流畅（60fps 推荐）</option>
            <option value="spring">Apple 轻盈弹簧</option>
            <option value="none">零动效（极致省电）</option>
          </select>
          <div class="row" style="margin-top:12px">
            <button id="saveThemeBtn">应用主题与动效</button>
          </div>
        </div>

        <div class="card">
          <h3>四模式动态壁纸与无线上传</h3>
          <label>壁纸模式（1+2+3+4 自由切换）</label>
          <select id="dynMode">
            <option value="static">静态壁纸（独立深/浅主题）</option>
            <option value="procedural">1. 轻量代码动效壁纸</option>
            <option value="gif">2. 本地 GIF 动态图</option>
            <option value="mp4">3. 本地短循环 MP4 视频</option>
            <option value="live">4. 透传系统原生动态壁纸</option>
          </select>
          <label>代码动效主题（模式1）</label>
          <select id="procStyle">
            <option value="aurora_flow">流体极光 Aurora</option>
            <option value="star_trails">星轨粒子 Star Trails</option>
            <option value="soft_rain">细雨微光 Soft Rain</option>
            <option value="gallery_breeze">画廊柔风 Gallery Breeze</option>
          </select>
          <label>无线上传壁纸（JPG/PNG/GIF/MP4）</label>
          <input id="wpFile" type="file" accept=".jpg,.jpeg,.png,.webp,.gif,.mp4">
          <div class="row" style="margin-top:10px">
            <button id="saveWpBtn">应用壁纸模式</button>
            <button id="uploadWpBtn" class="sec">上传并设为壁纸</button>
          </div>
        </div>

        <div class="card">
          <h3>多样式时钟小组件（6套表盘）</h3>
          <label>表盘样式</label>
          <select id="clockStyle">
            <option value="bloom_analog">M3 花瓣指针表</option>
            <option value="railway_swiss">瑞士铁路指针表</option>
            <option value="retro_flip">复古翻页时钟</option>
            <option value="kde_minimal">KDE 极简叠层数字</option>
            <option value="roman_classic">罗马典雅表盘</option>
            <option value="dot_matrix">像素点阵 LED</option>
          </select>
          <label>表盘外轮廓</label>
          <select id="clockShape">
            <option value="scallop">花瓣 Scallop</option>
            <option value="squircle">超椭圆 Squircle</option>
            <option value="circle">经典圆形</option>
            <option value="pill">胶囊 Pill</option>
          </select>
          <div class="row" style="margin-top:12px">
            <button id="saveClockBtn">应用时钟样式</button>
          </div>
        </div>
      </div>
    </div>

    <div id="pane-fonts" class="pane hidden">
      <div class="card">
        <h3>五通道细分全局动态字体调节</h3>
        <div class="grid">
          <div>
            <label>选择通道</label>
            <select id="fontSlot">
              <option value="clock">时钟数字 (Clock)</option>
              <option value="title">标题与卡片头 (Title)</option>
              <option value="body">正文与界面 (Body)</option>
              <option value="poetry">古诗词与阅读 (Poetry)</option>
              <option value="metric">数据与徽章 (Metric)</option>
            </select>
            <label>字体族</label>
            <select id="fontFamily">
              <option value="outfit">Outfit 几何无衬线</option>
              <option value="inter">Inter 极简现代</option>
              <option value="space_grotesk">Space Grotesk 科技</option>
              <option value="jetbrains_mono">JetBrains Mono 等宽</option>
              <option value="noto_serif_sc">思源宋体 (Noto Serif SC)</option>
              <option value="playfair">Playfair 典雅衬线</option>
              <option value="system_sans">系统无衬线</option>
              <option value="system_serif">系统衬线</option>
            </select>
          </div>
          <div>
            <label>字号缩放倍率 (<span id="fontScaleVal">1.00</span>x)</label>
            <input id="fontScale" type="range" min="0.75" max="1.45" step="0.05" value="1.0">
            <label>字重 (<span id="fontWeightVal">500</span>)</label>
            <input id="fontWeight" type="range" min="300" max="800" step="100" value="500">
            <label>字距 (<span id="fontSpacingVal">0.0</span> sp)</label>
            <input id="fontSpacing" type="range" min="-2.0" max="3.0" step="0.1" value="0">
          </div>
        </div>
        <div class="row" style="margin-top:12px">
          <button id="saveFontBtn">保存当前通道字体</button>
          <input id="fontFile" type="file" accept=".ttf,.otf" style="max-width:240px">
          <button id="uploadFontBtn" class="sec">上传 TTF/OTF 到此通道</button>
          <button id="resetFontBtn" class="sec">恢复默认字体</button>
        </div>
      </div>
    </div>

    <div id="pane-ai" class="pane hidden">
      <div class="grid">
        <div class="card">
          <h3>多套 AI API 预设管理（一键切换模型）</h3>
          <div id="aiProfileList"></div>
          <hr style="border-color:var(--line);margin:14px 0">
          <label>预设名称</label>
          <input id="aiName" placeholder="例如 DeepSeek V3 / 智谱 GLM-4">
          <label>接口 Base URL（国内备案 OpenAI 兼容地址）</label>
          <input id="aiBaseUrl" placeholder="https://api.deepseek.com/v1">
          <label>模型名称 Model</label>
          <input id="aiModel" placeholder="deepseek-chat">
          <label>API Key（仅保存在笔端私有存储，留空则保留原密钥）</label>
          <input id="aiApiKey" type="password" placeholder="sk-...">
          <label>系统提示词 System Prompt</label>
          <textarea id="aiPrompt"></textarea>
          <div class="row" style="margin-top:10px">
            <button id="saveAiBtn">保存 / 新增模型预设</button>
          </div>
        </div>

        <div class="card">
          <h3>远程 Harness MCP 指令台</h3>
          <p class="sub">直接用电脑键盘向笔端 AI 下达指令，例如：「帮我写一个高考倒计时加今日三件事小组件放到上滑抽屉」或「总结今天未读邮件」。</p>
          <textarea id="mcpPrompt" placeholder="输入给 PenOS Harness AI 的自然语言指令..."></textarea>
          <div class="row" style="margin-top:10px">
            <button id="runMcpBtn" class="coral">发送给笔端 AI 执行</button>
          </div>
          <pre id="mcpOutput" class="mono" style="white-space:pre-wrap;background:#0a0f18;padding:10px;border-radius:10px;margin-top:10px;min-height:90px;color:#cbd5e1"></pre>
        </div>
      </div>
    </div>

    <div id="pane-widgets" class="pane hidden">
      <div class="grid">
        <div class="card">
          <h3>PenOS 5.0 一键模板库（免 AI Key 直接装）</h3>
          <div class="row" style="margin-bottom:12px">
            <button class="sec tpl-btn" data-tpl="tpl_evening_study_macro">晚自习一键宏</button>
            <button class="sec tpl-btn" data-tpl="tpl_habit_mood_tracker">打卡&amp;心境</button>
            <button class="sec tpl-btn" data-tpl="tpl_physics_flashcard">理科翻面闪卡</button>
            <button class="sec tpl-btn" data-tpl="tpl_mini_timetable">今日迷你课表</button>
            <button class="sec tpl-btn" data-tpl="tpl_stopwatch_calc">秒表&amp;速算</button>
            <button class="sec tpl-btn" data-tpl="tpl_lan_json_sensor">JSON 指标看板</button>
          </div>
          <h3>已安装的 MCP 自定义小组件</h3>
          <div id="customWidgetList"></div>
        </div>
        <div class="card">
          <h3>新建 / 编辑声明式小组件（JSON DSL）</h3>
          <label>组件 ID</label>
          <input id="cwId" placeholder="exam_countdown">
          <label>标题</label>
          <input id="cwTitle" placeholder="冲刺倒计时">
          <label>摆放位置</label>
          <select id="cwPlace">
            <option value="BOTH">主屏 + Apple TV 抽屉</option>
            <option value="DRAWER">仅 Apple TV 上滑抽屉</option>
            <option value="DESKTOP">仅主屏</option>
          </select>
          <label>声明式内容块 JSON 数组</label>
          <textarea id="cwJson" class="mono">[{"type":"countdown","label":"距离目标日","target_iso":"2027-06-07"},{"type":"checklist","items":[{"text":"背 20 个词","done":false},{"text":"整理错题本","done":false}]}]</textarea>
          <div class="row" style="margin-top:10px">
            <button id="saveCwBtn">立即安装到词典笔</button>
          </div>
        </div>
      </div>
    </div>

    <div id="pane-mail" class="pane hidden">
      <div class="grid">
        <div class="card">
          <h3>已配置邮箱账号</h3>
          <div id="mailAccList"></div>
        </div>
        <div class="card">
          <h3>新增 IMAP/SMTP 邮箱账号</h3>
          <label>快速填充国内/常用预设</label>
          <select id="mailPreset">
            <option value="">-- 选择预设自动填服务器 --</option>
            <option value="qq">QQ 邮箱 (imap.qq.com / smtp.qq.com)</option>
            <option value="163">网易 163 (imap.163.com / smtp.163.com)</option>
            <option value="126">网易 126 (imap.126.com / smtp.126.com)</option>
            <option value="aliyun">阿里邮箱 (imap.aliyun.com / smtp.aliyun.com)</option>
            <option value="outlook">Outlook (outlook.office365.com)</option>
          </select>
          <label>邮箱地址</label>
          <input id="mailAddr" placeholder="name@qq.com">
          <label>IMAP 授权码 / 密码（仅存笔端本地）</label>
          <input id="mailPass" type="password" placeholder="客户端授权码">
          <div class="grid">
            <div><label>IMAP 主机</label><input id="mailImapHost" placeholder="imap.qq.com"></div>
            <div><label>IMAP 端口</label><input id="mailImapPort" type="number" value="993"></div>
            <div><label>SMTP 主机</label><input id="mailSmtpHost" placeholder="smtp.qq.com"></div>
            <div><label>SMTP 端口</label><input id="mailSmtpPort" type="number" value="465"></div>
          </div>
          <div class="row" style="margin-top:10px">
            <button id="saveMailBtn">保存邮箱并拉取收件箱</button>
          </div>
        </div>
      </div>
    </div>

    <div id="pane-backup" class="pane hidden">
      <div class="grid">
        <div class="card">
          <h3>桌面快照 &amp; 一键撤销回滚</h3>
          <p class="sub">每次 Harness MCP 或模板更改前自动存档。遇到改乱桌面或字体时可一键回到上一版。</p>
          <div id="snapshotList"></div>
          <div class="row" style="margin-top:12px">
            <button id="rollbackLatestBtn" class="coral">撤销上次桌面修改</button>
          </div>
        </div>
        <div class="card">
          <h3>全量配置 JSON 导出 / 导入（自动脱敏密钥）</h3>
          <p class="sub">导出外观、5 通道字体、时钟、自定义小组件与抽屉布局（默认自动抹除 AI API Key 与邮箱密码）。</p>
          <div class="row" style="margin-bottom:10px">
            <button id="exportCfgBtn">导出全量配置 JSON</button>
          </div>
          <label>粘贴配置 JSON 导入并覆盖</label>
          <textarea id="importCfgJson" class="mono" placeholder='{"schemaVersion":5,...}'></textarea>
          <div class="row" style="margin-top:10px">
            <button id="importCfgBtn" class="sec">校验并导入配置</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
<div id="toast" class="toast"></div>
<script>
let authToken = "";
let cachedState = null;

function toast(msg){
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.style.display = "block";
  clearTimeout(window._t);
  window._t = setTimeout(()=>{ el.style.display = "none"; }, 3200);
}

async function api(path, method="GET", body=null){
  const headers = {"X-Requested-With":"PenOS-LAN"};
  if(authToken) headers["Authorization"] = "Bearer " + authToken;
  if(body && !(body instanceof ArrayBuffer)) headers["Content-Type"] = "application/json";
  const res = await fetch(path, {
    method,
    headers,
    body: body ? (body instanceof ArrayBuffer ? body : JSON.stringify(body)) : undefined
  });
  const data = await res.json().catch(()=>({ok:false,message:"响应解析失败"}));
  if(!res.ok) throw new Error(data.message || ("HTTP " + res.status));
  return data;
}

document.querySelectorAll(".tab").forEach(btn=>{
  btn.addEventListener("click",()=>{
    document.querySelectorAll(".tab").forEach(b=>b.classList.remove("active"));
    document.querySelectorAll(".pane").forEach(p=>p.classList.add("hidden"));
    btn.classList.add("active");
    document.getElementById("pane-"+btn.dataset.tab).classList.remove("hidden");
  });
});

document.getElementById("loginBtn").addEventListener("click", async ()=>{
  try{
    const pin = document.getElementById("pinInput").value.trim();
    const res = await api("/api/auth/login","POST",{pin});
    authToken = res.token;
    document.getElementById("loginBox").classList.add("hidden");
    document.getElementById("appBox").classList.remove("hidden");
    document.getElementById("statusBadge").textContent = "已安全连接";
    await loadState();
    toast("已连接到 PenOS 词典笔");
  }catch(e){ toast("认证失败：" + e.message); }
});

function populateFontSlot(){
  if(!cachedState || !cachedState.typography) return;
  const slot = document.getElementById("fontSlot").value;
  const spec = cachedState.typography[slot];
  if(!spec) return;
  document.getElementById("fontFamily").value = spec.familyKey;
  document.getElementById("fontScale").value = spec.scale;
  document.getElementById("fontScaleVal").textContent = Number(spec.scale).toFixed(2);
  document.getElementById("fontWeight").value = spec.weight;
  document.getElementById("fontWeightVal").textContent = spec.weight;
  document.getElementById("fontSpacing").value = spec.letterSpacingSp;
  document.getElementById("fontSpacingVal").textContent = Number(spec.letterSpacingSp).toFixed(1);
}

["fontScale","fontWeight","fontSpacing"].forEach(id=>{
  document.getElementById(id).addEventListener("input",()=>{
    document.getElementById("fontScaleVal").textContent = Number(document.getElementById("fontScale").value).toFixed(2);
    document.getElementById("fontWeightVal").textContent = document.getElementById("fontWeight").value;
    document.getElementById("fontSpacingVal").textContent = Number(document.getElementById("fontSpacing").value).toFixed(1);
  });
});
document.getElementById("fontSlot").addEventListener("change", populateFontSlot);

async function loadState(){
  const s = await api("/api/state");
  cachedState = s;
  document.getElementById("designStyle").value = s.designStyle;
  document.getElementById("standbyStyle").value = s.standByStyleMode;
  document.getElementById("motionTier").value = s.motionTierId;
  document.getElementById("dynMode").value = s.dynamicWallpaperMode;
  document.getElementById("procStyle").value = s.proceduralWallpaperStyle;
  document.getElementById("clockStyle").value = s.multiClock.style;
  document.getElementById("clockShape").value = s.multiClock.shape;
  populateFontSlot();

  // Render AI profiles safely with textContent
  const aiList = document.getElementById("aiProfileList");
  aiList.textContent = "";
  (s.aiProfiles || []).forEach(p=>{
    const row = document.createElement("div");
    row.className = "item";
    const left = document.createElement("div");
    const title = document.createElement("div");
    title.textContent = (p.id === s.activeAiProfileId ? "★ " : "") + p.name;
    const sub = document.createElement("div");
    sub.className = "sub mono";
    sub.textContent = p.model + " · " + (p.hasKey ? "已配密钥" : "未配密钥");
    left.appendChild(title); left.appendChild(sub);
    const right = document.createElement("div");
    right.className = "row";
    const useBtn = document.createElement("button");
    useBtn.textContent = p.id === s.activeAiProfileId ? "当前使用" : "切换";
    useBtn.className = p.id === s.activeAiProfileId ? "sec" : "";
    useBtn.onclick = async ()=>{ await api("/api/ai/select","POST",{id:p.id}); toast("已切换至 " + p.name); await loadState(); };
    const editBtn = document.createElement("button");
    editBtn.textContent = "编辑";
    editBtn.className = "sec";
    editBtn.onclick = ()=>{
      window._editAiId = p.id;
      document.getElementById("aiName").value = p.name;
      document.getElementById("aiBaseUrl").value = p.baseUrl;
      document.getElementById("aiModel").value = p.model;
      document.getElementById("aiApiKey").value = "";
      document.getElementById("aiPrompt").value = p.systemPrompt;
    };
    right.appendChild(useBtn); right.appendChild(editBtn);
    row.appendChild(left); row.appendChild(right);
    aiList.appendChild(row);
  });

  // Render Custom Widgets safely
  const cwList = document.getElementById("customWidgetList");
  cwList.textContent = "";
  (s.customWidgets || []).forEach(w=>{
    const row = document.createElement("div");
    row.className = "item";
    const left = document.createElement("div");
    const t = document.createElement("div");
    t.textContent = w.title + " (" + w.placement + ")";
    const sub = document.createElement("div");
    sub.className = "sub mono";
    sub.textContent = "ID: " + w.id;
    left.appendChild(t); left.appendChild(sub);
    const del = document.createElement("button");
    del.textContent = "删除"; del.className = "danger";
    del.onclick = async ()=>{ await api("/api/widgets/delete","POST",{id:w.id}); toast("已移除 " + w.title); await loadState(); };
    row.appendChild(left); row.appendChild(del);
    cwList.appendChild(row);
  });

  // Render Mail accounts safely
  const mList = document.getElementById("mailAccList");
  mList.textContent = "";
  (s.mailAccounts || []).forEach(a=>{
    const row = document.createElement("div");
    row.className = "item";
    const left = document.createElement("div");
    const t = document.createElement("div");
    t.textContent = a.emailAddress;
    const sub = document.createElement("div");
    sub.className = "sub mono";
    sub.textContent = a.imapHost + ":" + a.imapPort;
    left.appendChild(t); left.appendChild(sub);
    const del = document.createElement("button");
    del.textContent = "删除"; del.className = "danger";
    del.onclick = async ()=>{ await api("/api/mail/delete","POST",{id:a.id}); toast("已删除邮箱"); await loadState(); };
    row.appendChild(left); row.appendChild(del);
    mList.appendChild(row);
  });

  // Render latest snapshot info safely
  const sList = document.getElementById("snapshotList");
  if(sList){
    sList.textContent = "";
    const snaps = s.snapshots || [];
    if(snaps.length === 0){
      const empty = document.createElement("div");
      empty.className = "sub";
      empty.textContent = "暂无已保存的桌面快照（首次 AI/模板修改后自动生成）";
      sList.appendChild(empty);
    } else {
      snaps.forEach(sp=>{
        const row = document.createElement("div");
        row.className = "item";
        const left = document.createElement("div");
        const t = document.createElement("div");
        t.textContent = sp.label + " [" + sp.source + "]";
        const sub = document.createElement("div");
        sub.className = "sub mono";
        sub.textContent = "ID #" + sp.id;
        left.appendChild(t); left.appendChild(sub);
        const btn = document.createElement("button");
        btn.textContent = "回滚至此";
        btn.className = "sec";
        btn.onclick = async ()=>{ const r = await api("/api/snapshots/rollback","POST",{id:sp.id}); toast(r.message || "已回滚"); await loadState(); };
        row.appendChild(left); row.appendChild(btn);
        sList.appendChild(row);
      });
    }
  }
}

document.getElementById("saveThemeBtn").onclick = async ()=>{
  await api("/api/appearance","POST",{
    designStyle: document.getElementById("designStyle").value,
    standByStyleMode: document.getElementById("standbyStyle").value,
    motionTierId: document.getElementById("motionTier").value
  });
  toast("主题与流畅度已同步至词典笔"); await loadState();
};
document.getElementById("saveWpBtn").onclick = async ()=>{
  await api("/api/wallpaper","POST",{
    mode: document.getElementById("dynMode").value,
    proceduralStyle: document.getElementById("procStyle").value
  });
  toast("动态壁纸模式已生效"); await loadState();
};
document.getElementById("uploadWpBtn").onclick = async ()=>{
  const f = document.getElementById("wpFile").files[0];
  if(!f){ toast("请先选择一个壁纸文件"); return; }
  const buf = await f.arrayBuffer();
  const r = await api("/api/upload?kind=wallpaper&name=" + encodeURIComponent(f.name), "POST", buf);
  toast(r.message || "壁纸已上传并生效"); await loadState();
};
document.getElementById("saveClockBtn").onclick = async ()=>{
  await api("/api/clock","POST",{
    style: document.getElementById("clockStyle").value,
    shape: document.getElementById("clockShape").value
  });
  toast("多样式时钟表盘已更新"); await loadState();
};
document.getElementById("saveFontBtn").onclick = async ()=>{
  await api("/api/font","POST",{
    slot: document.getElementById("fontSlot").value,
    familyKey: document.getElementById("fontFamily").value,
    scale: Number(document.getElementById("fontScale").value),
    weight: Number(document.getElementById("fontWeight").value),
    letterSpacingSp: Number(document.getElementById("fontSpacing").value)
  });
  toast("通道字体设置已生效"); await loadState();
};
document.getElementById("uploadFontBtn").onclick = async ()=>{
  const f = document.getElementById("fontFile").files[0];
  if(!f){ toast("请先选择 TTF/OTF 字体文件"); return; }
  const slot = document.getElementById("fontSlot").value;
  const buf = await f.arrayBuffer();
  const r = await api("/api/upload?kind=font&slot=" + encodeURIComponent(slot) + "&name=" + encodeURIComponent(f.name), "POST", buf);
  toast(r.message || "字体已无线推送至词典笔"); await loadState();
};
document.getElementById("resetFontBtn").onclick = async ()=>{
  await api("/api/font/reset","POST",{});
  toast("已恢复默认 5 通道字体"); await loadState();
};
document.getElementById("saveAiBtn").onclick = async ()=>{
  await api("/api/ai/upsert","POST",{
    id: window._editAiId || ("custom_" + Date.now()),
    name: document.getElementById("aiName").value.trim() || "自定义模型",
    baseUrl: document.getElementById("aiBaseUrl").value.trim(),
    model: document.getElementById("aiModel").value.trim(),
    apiKey: document.getElementById("aiApiKey").value.trim(),
    systemPrompt: document.getElementById("aiPrompt").value.trim()
  });
  window._editAiId = null;
  toast("AI 模型预设已保存"); await loadState();
};
document.getElementById("runMcpBtn").onclick = async ()=>{
  const prompt = document.getElementById("mcpPrompt").value.trim();
  if(!prompt) return;
  document.getElementById("mcpOutput").textContent = "正在通过笔端 Harness AI 执行...";
  try{
    const res = await api("/api/ai/run","POST",{prompt});
    document.getElementById("mcpOutput").textContent = (res.tools ? "[MCP动作] " + res.tools + "\n\n" : "") + (res.reply || "");
    await loadState();
  }catch(e){ document.getElementById("mcpOutput").textContent = "执行失败：" + e.message; }
};
document.getElementById("saveCwBtn").onclick = async ()=>{
  await api("/api/widgets/upsert","POST",{
    id: document.getElementById("cwId").value.trim() || ("cw_" + Date.now()),
    title: document.getElementById("cwTitle").value.trim() || "自定义卡片",
    placement: document.getElementById("cwPlace").value,
    dslJson: document.getElementById("cwJson").value
  });
  toast("自定义小组件已热部署到词典笔"); await loadState();
};
const MAIL_PRESETS = {
  qq:{ih:"imap.qq.com",ip:993,sh:"smtp.qq.com",sp:465},
  "163":{ih:"imap.163.com",ip:993,sh:"smtp.163.com",sp:465},
  "126":{ih:"imap.126.com",ip:993,sh:"smtp.126.com",sp:465},
  aliyun:{ih:"imap.aliyun.com",ip:993,sh:"smtp.aliyun.com",sp:465},
  outlook:{ih:"outlook.office365.com",ip:993,sh:"smtp.office365.com",sp:587}
};
document.getElementById("mailPreset").onchange = ()=>{
  const p = MAIL_PRESETS[document.getElementById("mailPreset").value];
  if(!p) return;
  document.getElementById("mailImapHost").value = p.ih;
  document.getElementById("mailImapPort").value = p.ip;
  document.getElementById("mailSmtpHost").value = p.sh;
  document.getElementById("mailSmtpPort").value = p.sp;
};
document.getElementById("saveMailBtn").onclick = async ()=>{
  await api("/api/mail/upsert","POST",{
    emailAddress: document.getElementById("mailAddr").value.trim(),
    password: document.getElementById("mailPass").value,
    imapHost: document.getElementById("mailImapHost").value.trim(),
    imapPort: Number(document.getElementById("mailImapPort").value || 993),
    smtpHost: document.getElementById("mailSmtpHost").value.trim(),
    smtpPort: Number(document.getElementById("mailSmtpPort").value || 465)
  });
  toast("邮箱账号已保存并开始同步"); await loadState();
};
document.querySelectorAll(".tpl-btn").forEach(btn=>{
  btn.addEventListener("click", async ()=>{
    try{
      const r = await api("/api/widgets/template","POST",{templateId:btn.dataset.tpl});
      toast(r.message || "模板已安装"); await loadState();
    }catch(e){ toast("安装失败：" + e.message); }
  });
});
document.getElementById("rollbackLatestBtn").onclick = async ()=>{
  try{
    const r = await api("/api/snapshots/rollback","POST",{});
    toast(r.message || "已撤销上次修改"); await loadState();
  }catch(e){ toast("回滚失败：" + e.message); }
};
document.getElementById("exportCfgBtn").onclick = async ()=>{
  try{
    const headers = {"X-Requested-With":"PenOS-LAN"};
    if(authToken) headers["Authorization"] = "Bearer " + authToken;
    const res = await fetch("/api/config/export", {headers});
    const txt = await res.text();
    document.getElementById("importCfgJson").value = txt;
    const blob = new Blob([txt], {type:"application/json"});
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "penos_full_config.json";
    a.click();
    toast("已导出脱敏全量配置 JSON");
  }catch(e){ toast("导出失败：" + e.message); }
};
document.getElementById("importCfgBtn").onclick = async ()=>{
  try{
    const raw = document.getElementById("importCfgJson").value.trim();
    if(!raw){ toast("请先粘贴配置 JSON"); return; }
    JSON.parse(raw);
    const headers = {"X-Requested-With":"PenOS-LAN","Content-Type":"application/json"};
    if(authToken) headers["Authorization"] = "Bearer " + authToken;
    const res = await fetch("/api/config/import", {method:"POST",headers,body:raw});
    const data = await res.json();
    if(!res.ok) throw new Error(data.message || "导入失败");
    toast(data.message || "配置已导入"); await loadState();
  }catch(e){ toast("导入失败：" + e.message); }
};
</script>
</body>
</html>
    """.trimIndent()
}
