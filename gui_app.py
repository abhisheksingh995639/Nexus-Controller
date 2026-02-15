import webview
import threading
import sys
import os
import socket
import json
import time
import base64
from io import BytesIO
import qrcode
try:
    import receiver
except Exception as e:
    # Handle missing ViGEmBus Driver
    if "VIGEM" in str(e) or "VBus" in str(e):
        error_html = f"""
        <!DOCTYPE html>
        <html>
        <body style="background-color: #121212; color: #e0e0e0; font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; padding: 20px; text-align: center;">
            <h2 style="color: #ff4d4d; margin-bottom: 10px;">Missing System Component</h2>
            <p style="font-size: 16px; line-height: 1.5;">The <b>ViGEm Bus Driver</b> is required but not installed on this PC.</p>
            <p style="color: #aaa; font-size: 14px; margin-bottom: 30px;">This driver is needed to create virtual Xbox/DS4 controllers.</p>
            
            <a href="https://github.com/nefarius/ViGEmBus/releases/latest" 
               style="background-color: #00f3ff; color: #000; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 14px; transition: opacity 0.2s;">
               Download ViGEmBus Driver
            </a>
            
            <div style="margin-top: 40px; border-top: 1px solid #333; padding-top: 20px; width: 100%;">
                 <p style="font-family: monospace; color: #666; font-size: 11px;">Error Code: {str(e)}</p>
            </div>
        </body>
        </html>
        """
        webview.create_window("Critical Error: Missing Driver", html=error_html, width=550, height=450, background_color='#121212')
        webview.start()
        sys.exit(1)
    else:
        raise e

# Global State
server_thread = None
SETTINGS_FILE = "settings.json"

# "NEXUS CORE" v3.0 - Haptics Toggle & Persistent Settings
HTML_TEMPLATE = """
<!DOCTYPE html>
<html class="dark" lang="en">
<head>
    <meta charset="utf-8"/>
    <meta content="width=device-width, initial-scale=1.0" name="viewport"/>
    <title>Nexus Gamepad Server</title>
    <script src="https://cdn.tailwindcss.com?plugins=forms,typography"></script>
    <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet"/>
    <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;700&amp;family=Orbitron:wght@400;500;700;900&amp;family=Inter:wght@300;400;500;600&amp;display=swap" rel="stylesheet"/>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.9.1/chart.min.js"></script>
    <script>
        tailwind.config = {
            darkMode: "class",
            theme: {
                extend: {
                    colors: {
                        primary: "var(--color-primary)", 
                        secondary: "var(--color-secondary)", 
                        dark: "#050505",
                        card: "#0a0a0a",
                        surface: "#111111"
                    },
                    fontFamily: {
                        display: ["Orbitron", "sans-serif"],
                        mono: ["JetBrains Mono", "monospace"],
                        sans: ["Inter", "sans-serif"],
                    },
                    animation: {
                        'pulse-fast': 'pulse 1s cubic-bezier(0.4, 0, 0.6, 1) infinite',
                    }
                },
            },
        };
    </script>
    <style>
        :root {
            --color-primary: #00f3ff;
            --color-secondary: #ff00ff;
        }

        body {
            background-color: #020202;
            background-image: 
                linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
                linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
            background-size: 40px 40px;
        }
        
        .neon-text { text-shadow: 0 0 5px var(--color-primary), 0 0 10px rgba(0,0,0,0.5); }
        .neon-border { box-shadow: 0 0 5px var(--color-primary), inset 0 0 10px rgba(0,0,0,0.1); }
        
        .cyber-card {
            background: rgba(10, 10, 10, 0.85);
            border: 1px solid #1f1f1f;
            position: relative;
            backdrop-filter: blur(8px);
            transition: all 0.3s;
        }
        .cyber-card::before {
            content: ''; position: absolute; top: 0; left: 0; width: 100%; height: 1px;
            background: linear-gradient(90deg, transparent, var(--color-primary), transparent);
            opacity: 0.5;
        }
        .cyber-card.active {
            border-color: var(--color-primary);
            box-shadow: 0 0 20px rgba(0, 243, 255, 0.05);
        }

        .scanline {
            width: 100%; height: 100%;
            background: linear-gradient(to bottom, transparent 50%, rgba(0,0,0,0.5) 51%);
            background-size: 100% 3px;
            pointer-events: none;
            position: absolute; top: 0; left: 0; z-index: 40;
            opacity: 0.15;
            mix-blend-mode: overlay;
        }

        ::-webkit-scrollbar { width: 4px; }
        ::-webkit-scrollbar-track { background: #000; }
        ::-webkit-scrollbar-thumb { background: #333; }
        ::-webkit-scrollbar-thumb:hover { background: var(--color-primary); }

        .corner-accent { position: absolute; width: 6px; height: 6px; border: 1px solid #333; transition: all 0.3s; }
        .corner-accent.active { border-color: var(--color-primary); }
        .corner-accent.tl { top: -1px; left: -1px; border-right: none; border-bottom: none; }
        .corner-accent.tr { top: -1px; right: -1px; border-left: none; border-bottom: none; }
        .corner-accent.bl { bottom: -1px; left: -1px; border-right: none; border-top: none; }
        .corner-accent.br { bottom: -1px; right: -1px; border-left: none; border-top: none; }

        /* MODAL */
        #settings-modal {
            background: rgba(0,0,0,0.9);
            backdrop-filter: blur(10px);
            z-index: 100;
        }
        
        .key-btn {
            width: 40px; height: 40px; 
            border: 1px solid #333; 
            display: flex; align-items: center; justify-content: center;
            border-radius: 50%;
            cursor: pointer;
            font-family: 'JetBrains Mono'; font-size: 10px;
            transition: all 0.2s;
        }
        .key-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
        .key-btn.bound { background: var(--color-primary); color: black; border-color: var(--color-primary); }

        /* NOTIFICATIONS */
        #achievement-container { position: absolute; bottom: 30px; right: 30px; z-index: 200; }
    </style>
</head>
<body class="text-gray-300 h-screen overflow-hidden flex flex-col selection:bg-primary selection:text-black">

    <div class="scanline"></div>

    <!-- MAPPING MODAL -->
    <div id="settings-modal" class="fixed inset-0 flex items-center justify-center hidden z-[1000]">
        <div class="bg-[#0a0a0a] border border-white/10 p-6 w-[500px] relative shadow-[0_0_50px_rgba(0,0,0,0.8)]">
            <button onclick="closeSettings()" class="absolute top-4 right-4 text-gray-500 hover:text-white"><span class="material-icons">close</span></button>
            <h2 class="font-display text-xl text-primary mb-6 neon-text uppercase tracking-widest">System Configuration</h2>
            
            <div class="space-y-6">
                <!-- Haptics Toggle -->
                <div class="border-b border-white/10 pb-6">
                     <div class="flex items-center justify-between mb-2">
                        <h3 class="font-mono text-xs text-gray-500 uppercase">Haptic Feedback</h3>
                        <button id="haptic-toggle-btn" onclick="toggleHaptics()" class="border border-primary text-gray-500 bg-transparent px-3 py-1 font-display font-bold text-xs uppercase transition-all hover:bg-white/5">DISABLED</button>
                     </div>
                     <p id="haptic-status" class="text-[9px] font-mono text-gray-600">FORCE FEEDBACK MODULE OFFLINE</p>
                </div>

                <!-- Key Map -->
                <div>
                    <h3 class="font-mono text-xs text-gray-500 uppercase mb-4">Key Binding Override</h3>
                    <p class="text-[10px] text-gray-600 mb-4 ">Click a button below then press a keyboard key to bind.</p>
                    
                    <div class="grid grid-cols-4 gap-4 place-items-center bg-black/50 p-4 rounded border border-white/5">
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-a" onclick="bindKey('a')">A</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-a">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-b" onclick="bindKey('b')">B</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-b">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-x" onclick="bindKey('x')">X</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-x">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-y" onclick="bindKey('y')">Y</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-y">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-lb" onclick="bindKey('lb')">LB</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-lb">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn" id="btn-rb" onclick="bindKey('rb')">RB</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-rb">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn text-[8px]" id="btn-select" onclick="bindKey('select')">SEL</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-select">--</span>
                        </div>
                        <div class="flex flex-col items-center gap-1">
                            <div class="key-btn text-[8px]" id="btn-start" onclick="bindKey('start')">STRT</div>
                            <span class="text-[9px] font-mono text-gray-500" id="lbl-start">--</span>
                        </div>
                    </div>
                    <div id="bind-status" class="mt-2 text-center font-mono text-xs text-primary h-4"></div>
                </div>
            </div>
            
            <div class="mt-6 flex justify-end">
                <button onclick="saveSettings()" class="px-6 py-2 bg-primary text-black font-bold uppercase text-xs tracking-wider hover:bg-white transition-colors">Save & Close</button>
            </div>
        </div>
    </div>

    <!-- BOOT OVERLAY -->
    <div id="boot-overlay" class="absolute inset-0 flex items-center justify-center hidden bg-black z-50">
        <div class="w-3/4 max-w-lg space-y-4">
            <h1 class="font-display text-3xl font-bold text-primary neon-text text-center tracking-widest mb-8">NEXUS BIOS v2.4</h1>
            <div id="boot-log" class="font-mono text-xs text-green-500 space-y-1 h-32 overflow-hidden border-l-2 border-green-500 pl-4 bg-black/50 p-2"></div>
            <div class="w-full bg-gray-900 h-1 mt-4"><div id="boot-bar" class="h-full bg-primary w-0"></div></div>
        </div>
    </div>

    <!-- Header -->
    <header class="flex-none h-16 border-b border-white/5 bg-black/60 backdrop-blur-md flex items-center justify-between px-6 draggable z-30">
        <div class="flex items-center gap-4">
            <div id="logo-icon" class="w-8 h-8 rounded bg-primary/10 border border-primary/20 flex items-center justify-center text-primary transition-all">
                <span class="material-icons text-lg">games</span>
            </div>
            <div>
                <h1 class="font-display font-bold text-lg tracking-wider text-white neon-text">NEXUS <span class="text-primary">CORE</span></h1>
            </div>
        </div>
        
        <div class="flex items-center gap-6">
            <div class="flex gap-2">
                <button onclick="setTheme('cyan')" class="w-3 h-3 rounded-full bg-[#00f3ff] hover:scale-125 transition-transform"></button>
                <button onclick="setTheme('green')" class="w-3 h-3 rounded-full bg-[#00ff41] hover:scale-125 transition-transform"></button>
                <button onclick="setTheme('orange')" class="w-3 h-3 rounded-full bg-[#ff9100] hover:scale-125 transition-transform"></button>
                <button onclick="setTheme('red')" class="w-3 h-3 rounded-full bg-[#ff003c] hover:scale-125 transition-transform"></button>
            </div>
            <div class="h-6 w-px bg-white/10"></div>
             <div class="relative group">
                <div class="flex items-center gap-2 px-3 py-1.5 rounded bg-white/5 border border-white/5 text-[10px] uppercase font-mono cursor-pointer hover:bg-white/10 transition-colors">
                    <span class="material-icons text-xs text-gray-500">wifi</span>
                    <span id="current-ip-label" class="text-gray-300">AUTO-DETECT</span>
                </div>
                <div id="ip-dropdown" class="absolute top-full right-0 mt-2 w-48 bg-card border border-white/10 rounded shadow-xl hidden group-hover:block z-50 bg-[#0a0a0a]">
                    <div onclick="selectIp('AUTO')" class="px-3 py-2 hover:bg-white/5 cursor-pointer text-xs font-mono text-gray-400">AUTO DETECT</div>
                </div>
            </div>
        </div>
    </header>

    <!-- Content -->
    <div class="flex-grow p-6 overflow-hidden flex gap-6 relative z-10">
        
        <!-- Left Cluster -->
        <div class="w-80 flex-none flex flex-col gap-4 h-full">
            
            <!-- Power Card -->
            <div id="power-card" class="cyber-card p-6 flex flex-col items-center justify-center gap-4 rounded-sm flex-none">
                <span class="corner-accent tl"></span><span class="corner-accent tr"></span>
                <span class="corner-accent bl"></span><span class="corner-accent br"></span>
                
                <div class="relative w-40 h-40 flex items-center justify-center group pointer-events-none">
                    <svg class="absolute inset-0 w-full h-full -rotate-90">
                        <circle cx="80" cy="80" r="70" stroke="#111" stroke-width="6" fill="none"></circle>
                        <circle id="gauge-circle" cx="80" cy="80" r="70" stroke="var(--color-primary)" stroke-width="6" fill="none"
                            stroke-dasharray="440" stroke-dashoffset="440" stroke-linecap="round" 
                            class="transition-all duration-[1.5s] ease-out filter drop-shadow-[0_0_8px_rgba(var(--color-primary),0.6)]"></circle>
                    </svg>
                    <div class="absolute inset-0 flex flex-col items-center justify-center z-10">
                        <span id="status-text" class="block font-display font-black text-3xl text-gray-700 transition-all duration-500">OFF</span>
                    </div>
                    <div id="inner-spin" class="absolute w-[110px] h-[110px] border border-dashed border-primary/30 rounded-full opacity-0"></div>
                </div>
                
                <button id="btn-power" onclick="toggleServer()" class="w-full group bg-primary/10 border border-primary/20 hover:border-primary/50 text-primary py-3 px-4 transition-all duration-300">
                    <span id="btn-power-text" class="font-display font-bold uppercase tracking-widest text-sm flex items-center justify-center gap-2">
                        <span class="material-icons text-base">power_settings_new</span> Initialize
                    </span>
                </button>
            </div>

            <!-- QR Card -->
            <div id="qr-card" class="cyber-card p-4 flex-grow flex flex-col rounded-sm opacity-50 relative overflow-hidden transition-all duration-500 min-h-0">
                <div class="flex items-center justify-between mb-2 flex-none">
                     <h3 class="font-display text-xs text-primary/70 uppercase tracking-widest">Link Module</h3>
                </div>
                <div class="text-center mb-2 flex-none">
                    <span id="display-ip" class="font-mono text-xl text-primary font-bold tracking-wider opacity-50">--.--.--.--</span>
                </div>
                <div class="flex-grow relative flex items-center justify-center bg-black/40 border border-white/5 rounded overflow-hidden">
                     <img id="qr-img" class="relative z-10 w-full h-full object-contain p-2 opacity-0 transition-all duration-500" src="">
                     <div id="qr-scan-line" class="absolute top-0 w-full h-1 bg-primary/80 shadow-[0_0_15px_var(--color-primary)] z-20 opacity-0"></div>
                </div>
            </div>
        </div>

        <!-- Right Cluster -->
        <div class="flex-grow flex flex-col gap-4">
            
            <!-- Lobby -->
            <div id="lobby-card" class="cyber-card flex-grow p-6 rounded-sm flex flex-col opacity-80 transition-all duration-500">
                <span class="corner-accent tr"></span>
                <div class="flex items-center justify-between mb-4">
                    <div class="flex items-center gap-3">
                        <h2 class="font-display text-sm text-primary uppercase tracking-widest neon-text">Telemetry Bridge</h2>
                        <span id="slot-badge" class="px-2 py-0.5 bg-primary/10 text-primary font-mono text-[10px] rounded border border-primary/30">0 / 4</span>
                    </div>
                </div>
                
                <div id="player-list" class="grid grid-cols-1 gap-3 overflow-y-auto pr-2 flex-grow">
                    <!-- JS fills this -->
                </div>
            </div>

            <!-- Performance Graph -->
            <div id="perf-card" class="cyber-card p-3 rounded-sm opacity-80 h-36 flex flex-col overflow-hidden">
                 <div class="flex justify-between items-center mb-1 px-1 flex-none">
                      <span class="font-mono text-[9px] text-gray-500 uppercase">Input Stream (Ticks/Sec)</span>
                      <span id="stat-pps" class="font-mono text-xs text-primary font-bold">0 PPS</span>
                 </div>
                 <div class="flex-grow relative w-full h-full p-2">
                     <canvas id="perfChart"></canvas>
                 </div>
            </div>

            <!-- Logs -->
            <div class="h-24 bg-black/80 border border-white/10 rounded-sm p-3 font-mono text-[10px] flex flex-col shadow-inner relative">
                <div class="absolute top-2 right-2 opacity-20"><span class="material-icons">terminal</span></div>
                <div id="console-out" class="flex-grow overflow-y-auto space-y-1 pr-2 text-gray-400 z-10"></div>
            </div>
        </div>
    </div>
    
    <div id="achievement-container"></div>

<script>
    // --- AUDIO SYSTEM ---
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    
    function playBeep(freq=800, type='sine', dur=0.1, vol=0.1) {
        if(ctx.state === 'suspended') ctx.resume();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = type;
        osc.frequency.setValueAtTime(freq, ctx.currentTime);
        gain.gain.setValueAtTime(vol, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + dur);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + dur);
    }
    
    function playBootSound() {
        if(ctx.state === 'suspended') ctx.resume();
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.frequency.setValueAtTime(100, ctx.currentTime);
        osc.frequency.exponentialRampToValueAtTime(800, ctx.currentTime + 1.5);
        gain.gain.setValueAtTime(0.1, ctx.currentTime);
        gain.gain.linearRampToValueAtTime(0, ctx.currentTime + 2.0);
        osc.connect(gain);
        gain.connect(ctx.destination);
        osc.start();
        osc.stop(ctx.currentTime + 2);
    }
    
    function playAchievementSound() { playBeep(800, 'square', 0.1, 0.2); setTimeout(() => playBeep(1200, 'square', 0.3, 0.2), 100); }

    // --- CHART ---
    const ctxChart = document.getElementById('perfChart').getContext('2d');
    const perfChart = new Chart(ctxChart, {
        type: 'line',
        data: {
            labels: Array(30).fill(''),
            datasets: [{
                label: 'PPS',
                data: Array(30).fill(0),
                borderColor: '#00f3ff',
                backgroundColor: 'rgba(0, 243, 255, 0.15)',
                borderWidth: 1.5,
                fill: true,
                tension: 0.3,
                pointRadius: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false }, tooltip: { enabled: false } },
            scales: { x: { display: false }, y: { display: false, min: 0, suggestedMax: 100, beginAtZero: true } },
            animation: { duration: 0 }
        }
    });

    // --- GLOBALS ---
    let selectedIp = null;
    let availableIps = [];
    let connectedPlayers = new Set();
    let editPlayerIdx = 0; // Currently editing player
    let pendingBind = null; // Key waiting to be bound
    let isRunningGlobal = false;

    // --- NOTIFICATIONS ---
    function showNotification(title, msg) {
        const c = document.getElementById('achievement-container');
        const div = document.createElement('div');
        div.className = 'achievement';
        div.innerHTML = `<div class="icon-box"><span class="material-icons text-primary text-sm">emoji_events</span></div><div><div class="font-display font-bold text-xs tracking-wider text-primary uppercase">${title}</div><div class="font-mono text-[10px] text-gray-400">${msg}</div></div>`;
        c.appendChild(div);
        playAchievementSound();
        setTimeout(() => div.remove(), 4000);
    }

    // --- SETTINGS MODAL LOGIC---
    function openSettings(idx) {
        receiveLog("Opening Config for Unit 0" + (idx+1));
        editPlayerIdx = idx;
        // Show immediately for responsiveness
        document.getElementById('settings-modal').classList.remove('hidden');
        
        // Reset labels immediately
        ['a','b','x','y','lb','rb','select','start'].forEach(btn => {
             const lbl = document.getElementById('lbl-'+btn);
             if(lbl) lbl.innerText = "--";
        });
        
        // Load Haptics State
        pywebview.api.get_haptic_state().then(enabled => {
             const btn = document.getElementById('haptic-toggle-btn');
             const status = document.getElementById('haptic-status');
             if(enabled) {
                 btn.classList.add('bg-primary', 'text-black');
                 btn.classList.remove('bg-transparent', 'text-gray-500');
                 btn.innerText = "ENABLED";
                 status.innerText = "VIBRO-TACTILE FEEDBACK ONLINE";
                 status.className = "text-[9px] font-mono text-primary";
             } else {
                 btn.classList.remove('bg-primary', 'text-black');
                 btn.classList.add('bg-transparent', 'text-gray-500');
                 btn.innerText = "DISABLED";
                 status.innerText = "FORCE FEEDBACK MODULE OFFLINE";
                 status.className = "text-[9px] font-mono text-gray-600";
             }
        });

        // Load existing
        pywebview.api.get_mapping(idx).then(map => {
            for(const [btn, key] of Object.entries(map)) {
                if(document.getElementById('lbl-'+btn)) document.getElementById('lbl-'+btn).innerText = key.toUpperCase();
                if(document.getElementById('btn-'+btn)) document.getElementById('btn-'+btn).classList.add('bound');
            }
        });
    }

    function closeSettings() {
        document.getElementById('settings-modal').classList.add('hidden');
        pendingBind = null;
    }

    function toggleHaptics() {
        const btn = document.getElementById('haptic-toggle-btn');
        const isEnabled = btn.innerText === "ENABLED";
        const newState = !isEnabled;
        
        pywebview.api.set_haptic_state(newState);
        
        // UI Update
        const status = document.getElementById('haptic-status');
        if(newState) {
             btn.classList.add('bg-primary', 'text-black');
             btn.classList.remove('bg-transparent', 'text-gray-500');
             btn.innerText = "ENABLED";
             status.innerText = "VIBRO-TACTILE FEEDBACK ONLINE";
             status.className = "text-[9px] font-mono text-primary";
             // Test pulse
             pywebview.api.test_rumble(editPlayerIdx, 0.5);
        } else {
             btn.classList.remove('bg-primary', 'text-black');
             btn.classList.add('bg-transparent', 'text-gray-500');
             btn.innerText = "DISABLED";
             status.innerText = "FORCE FEEDBACK MODULE OFFLINE";
             status.className = "text-[9px] font-mono text-gray-600";
        }
    }

    function bindKey(btn) {
        pendingBind = btn;
        document.getElementById('bind-status').innerText = `PRESS KEY FOR '${btn.toUpperCase()}'...`;
        
        // Listen for next keypress
        const handler = (e) => {
            e.preventDefault();
            const key = e.key === " " ? "space" : e.key.toLowerCase();
            
            // UI Update
            document.getElementById('lbl-'+btn).innerText = key.toUpperCase();
            document.getElementById('btn-'+btn).classList.add('bound');
            document.getElementById('bind-status').innerText = `BOUND '${btn.toUpperCase()}' TO '${key.toUpperCase()}'`;
            
            // API Call
            pywebview.api.set_key_bind(editPlayerIdx, btn, key);
            
            window.removeEventListener('keydown', handler);
            pendingBind = null;
        };
        window.addEventListener('keydown', handler, {once:true});
    }
    
    function saveSettings() {
        closeSettings();
        playBeep(1200, 'sine', 0.1);
    }

    // --- THEME ---
    function setTheme(colorName) {
        playBeep(1000, 'sine', 0.05);
        const r = document.documentElement;
        let pColor = '#00f3ff';
        if(colorName === 'cyan') pColor = '#00f3ff';
        else if(colorName === 'green') pColor = '#00ff41';
        else if(colorName === 'orange') pColor = '#ff9100';
        else if(colorName === 'red') pColor = '#ff003c';
        
        r.style.setProperty('--color-primary', pColor);
        perfChart.data.datasets[0].borderColor = pColor;
        perfChart.data.datasets[0].backgroundColor = pColor + '20';
        perfChart.update();
    }

    function selectIp(ip) {
        selectedIp = ip;
        document.getElementById('current-ip-label').textContent = ip === 'AUTO' ? 'AUTO-DETECT' : ip;
        playBeep(400, 'sine', 0.05);
    }

    // --- APP FLOW ---
    function toggleServer() {
        if (!isRunningGlobal) initiateBoot();
        else stopServer();
    }

    async function initiateBoot() {
        const btn = document.getElementById('btn-power');
        btn.disabled = true;
        playBootSound();
        const overlay = document.getElementById('boot-overlay');
        const logBox = document.getElementById('boot-log');
        const bar = document.getElementById('boot-bar');
        overlay.classList.remove('hidden');
        logBox.innerHTML = ''; bar.style.width = '0%';
        const lines = ["Initializing Nexus Core...", "Mounting Virtual Drivers...", "Checking Protocols...", "Starting Visualizer...", "SYSTEM READY."];
        for(let i=0; i<lines.length; i++) {
            logBox.innerHTML += `<div>> ${lines[i]}</div>`; logBox.scrollTop = logBox.scrollHeight;
            bar.style.width = `${((i+1)/lines.length)*100}%`; await new Promise(r => setTimeout(r, 200));
        }
        await new Promise(r => setTimeout(r, 300));
        overlay.classList.add('hidden');
        pywebview.api.start_server(selectedIp);
    }

    function stopServer() { 
        playBeep(300, 'sawtooth', 0.3); 
        pywebview.api.stop_server(); 
    }

    function updateState(state) {
        const { running, ip, players, qr, ips, pps } = state;
        isRunningGlobal = running;
        
        if(availableIps.length !== ips.length && ips.length > 0) {
            availableIps = ips;
            const drop = document.getElementById('ip-dropdown');
            drop.innerHTML = '<div onclick="selectIp(\\'AUTO\\')" class="px-3 py-2 hover:bg-white/5 cursor-pointer text-xs font-mono text-gray-400">AUTO DETECT</div>';
            ips.forEach(addr => drop.innerHTML += `<div onclick="selectIp('${addr}')" class="px-3 py-2 hover:bg-white/5 cursor-pointer text-xs font-mono text-gray-300">${addr}</div>`);
        }
        
        const statusText = document.getElementById('status-text');
        const gauge = document.getElementById('gauge-circle');
        const btn = document.getElementById('btn-power');
        const btnText = document.getElementById('btn-power-text');
        
        if(running) {
            statusText.textContent = "ONLINE"; statusText.className = "block font-display font-black text-3xl text-primary neon-text animate-pulse-fast"; gauge.style.strokeDashoffset = '0';
            ['power-card','qr-card','lobby-card','perf-card'].forEach(id=>document.getElementById(id).classList.add('active','opacity-100'));
            document.getElementById('inner-spin').classList.add('animate-spin'); document.getElementById('inner-spin').style.opacity = '1';
            
            // Running State Button
            if(btn) {
                btn.disabled = false;
                btn.className = "w-full group bg-red-500/10 border border-red-500/20 text-red-500 py-3 px-4 transition-all duration-300 hover:bg-red-500/20";
                if(btnText) btnText.innerHTML = '<span class="material-icons text-base">power_settings_new</span> TERMINATE';
            }
            
            document.getElementById('display-ip').textContent = ip || "INITIALIZING..."; document.getElementById('display-ip').classList.remove('opacity-50');
            
            if(qr && document.getElementById('qr-img').src.indexOf('base64') === -1) {
                document.getElementById('qr-img').src = "data:image/png;base64," + qr;
                document.getElementById('qr-img').classList.remove('scale-90', 'opacity-0');
                document.getElementById('qr-scan-line').style.opacity = '1'; document.getElementById('qr-scan-line').style.animation = 'scan 2s linear infinite';
            }
            document.getElementById('stat-pps').innerText = pps + " PPS"; perfChart.data.datasets[0].data.push(pps); perfChart.data.datasets[0].data.shift(); perfChart.update();
        } else {
            statusText.textContent = "OFF"; statusText.className = "block font-display font-black text-3xl text-gray-600"; gauge.style.strokeDashoffset = '440';
            ['power-card','qr-card','lobby-card','perf-card'].forEach(id=>document.getElementById(id).classList.remove('active','opacity-100'));
            document.getElementById('inner-spin').classList.remove('animate-spin');
            
            // Stopped State Button
            if(btn) {
                btn.disabled = false;
                btn.className = "w-full group bg-primary/10 border border-primary/20 hover:border-primary/50 text-primary py-3 px-4 transition-all duration-300";
                if(btnText) btnText.innerHTML = '<span class="material-icons text-base">power_settings_new</span> INITIALIZE';
            }

            document.getElementById('display-ip').textContent = "--.--.--.--";
            document.getElementById('stat-pps').innerText = "0 PPS";
        }
        
        // Player List Optimization
        const list = document.getElementById('player-list');
        
        // Ensure slots exist (Create once)
        if(list.children.length !== players.length) {
            list.innerHTML = '';
            players.forEach((_, idx) => {
                const div = document.createElement('div');
                div.id = `p-card-${idx}`;
                // Default Inactive State
                div.className = "p-3 border border-white/5 bg-white/5 opacity-50 rounded-sm flex items-center gap-4 group transition-all duration-300";
                div.innerHTML = `
                <div class="w-12 h-12 relative flex items-center justify-center rounded-sm bg-black border border-white/10 overflow-hidden">
                    <div id="p-stick-${idx}" class="absolute w-2 h-2 bg-primary rounded-full shadow-[0_0_5px_var(--color-primary)] input-bar hidden"></div>
                    <span id="p-icon-${idx}" class="material-icons text-gray-700">gamepad</span>
                </div>
                <div class="flex-grow">
                    <div class="flex justify-between items-center mb-1">
                        <span id="p-label-${idx}" class="font-display text-xs font-bold tracking-wider text-gray-600">UNIT 0${idx+1}</span>
                        <div id="p-action-${idx}"></div>
                    </div>
                    <div class="h-1 w-full bg-black rounded-full overflow-hidden">
                        <div id="p-bar-${idx}" class="h-full bg-primary transition-all duration-300" style="width: 0%"></div>
                    </div>
                </div>`;
                list.appendChild(div);
            });
        }

        let pCount = 0;
        let currentIds = new Set();

        players.forEach((p, idx) => {
             const card = document.getElementById(`p-card-${idx}`);
             const stick = document.getElementById(`p-stick-${idx}`);
             const icon = document.getElementById(`p-icon-${idx}`);
             const label = document.getElementById(`p-label-${idx}`);
             const action = document.getElementById(`p-action-${idx}`);
             const bar = document.getElementById(`p-bar-${idx}`);
             
             if(p.connected) {
                 pCount++; currentIds.add(idx);
                 if(!connectedPlayers.has(idx)) showNotification("NEW LINK DETECTED", `Unit 0${idx+1} Connected`);
                 
                 // Active State Styles
                 // Use classList for performance/cleanliness logic or full string to ensure match
                 card.className = "p-3 border border-primary/50 bg-primary/5 neon-border rounded-sm flex items-center gap-4 group transition-all duration-300";
                 
                 // Visuals
                 const vis = p.visuals || {lx:0, ly:0};
                 const lx_pct = 50 + (vis.lx * 40); 
                 const ly_pct = 50 + (vis.ly * 40);
                 
                 stick.style.left = `${lx_pct}%`;
                 stick.style.top = `${ly_pct}%`;
                 stick.classList.remove('hidden');
                 icon.classList.add('hidden');
                 
                 label.innerHTML = `UNIT 0${idx+1} <span class="font-mono text-[9px] text-gray-500 opacity-70 ml-2">${p.pps||0} PPS</span>`;
                 label.className = "font-display text-xs font-bold tracking-wider text-primary neon-text";
                 
                 if(action.getAttribute('data-state') !== 'config') {
                     action.innerHTML = `<button onclick="openSettings(${idx})" class="text-[9px] px-2 py-0.5 border border-primary text-primary hover:bg-primary hover:text-black uppercase">Config</button>`;
                     action.setAttribute('data-state', 'config');
                 }
                 
                 bar.style.width = "100%";
                 
             } else {
                 // Inactive State
                 card.className = "p-3 border border-white/5 bg-white/5 opacity-50 rounded-sm flex items-center gap-4 group transition-all duration-300";
                 stick.classList.add('hidden');
                 icon.classList.remove('hidden');
                 label.className = "font-display text-xs font-bold tracking-wider text-gray-600";
                 
                 if(action.getAttribute('data-state') !== 'offline') {
                     action.innerHTML = `<span class="font-mono text-[9px] text-gray-700">OFFLINE</span>`;
                     action.setAttribute('data-state', 'offline');
                 }
                 bar.style.width = "0%";
             }
        });
        
        document.getElementById('slot-badge').textContent = `${pCount} / 4`;
        connectedPlayers = currentIds;
    }
    
    setInterval(() => { pywebview.api.get_state().then(updateState); }, 250); 
    function receiveLog(msg) { const c=document.getElementById('console-out'); const d=document.createElement('div'); d.innerHTML = `<span class="opacity-30 mr-2">></span> ${msg}`; d.className="text-primary/70"; c.appendChild(d); c.scrollTop=c.scrollHeight; }
    setTimeout(() => playBeep(200, 'sine', 0.05), 500);
</script>
</body>
</html>
"""

class Api:
    def __init__(self):
        self.cached_qr = None
        self.last_check = time.time()
        self.last_packets = 0
        self.last_player_packets = {}
        self.player_pps = {}
        self.ips = []
        try: self.ips = socket.gethostbyname_ex(socket.gethostname())[2]
        except: self.ips = [receiver.get_local_ip()]
        self.settings = {"haptics": True}
        self.load_settings()

    def load_settings(self):
        try:
            if os.path.exists(SETTINGS_FILE):
                with open(SETTINGS_FILE, 'r') as f:
                    self.settings = json.load(f)
            # Apply initial state globally if needed
            receiver.HAPTICS_ENABLED = self.settings.get("haptics", True)
        except: pass

    def save_settings(self):
        try:
            with open(SETTINGS_FILE, 'w') as f:
                json.dump(self.settings, f)
        except: pass

    def get_haptic_state(self):
        return self.settings.get("haptics", True)

    def set_haptic_state(self, enabled):
        self.settings["haptics"] = enabled
        receiver.HAPTICS_ENABLED = enabled
        self.save_settings()

    def get_state(self):
        global receiver
        now = time.time()
        dt = now - self.last_check
        pps = 0
        if dt >= 1.0:
            current = receiver.packet_counter
            pps = int((current - self.last_packets) / dt)
            self.last_packets = current
            
            # Calculate Per-Player PPS
            for i, p in enumerate(receiver.players):
                cur_p = getattr(p, 'packet_count', 0)
                last_p = self.last_player_packets.get(i, 0)
                if last_p > cur_p: last_p = 0 # Reset handler
                self.player_pps[i] = int((cur_p - last_p) / dt)
                self.last_player_packets[i] = cur_p
            
            self.last_check = now
            receiver.current_pps = pps 
        else: pps = receiver.current_pps

        player_list = []
        for i, p in enumerate(receiver.players):
            player_list.append({
                "connected": p.connected,
                "addr": p.addr[0] if p.addr else "",
                "visuals": getattr(p, 'visuals', {}),
                "pps": self.player_pps.get(i, 0)
            })
            
        return {
            "running": receiver.running,
            "ip": receiver.get_local_ip() if receiver.running else None,
            "players": player_list,
            "qr": self.cached_qr if receiver.running else None,
            "ips": self.ips,
            "pps": pps
        }

    def start_server(self, manual_ip=None):
        global server_thread
        if not receiver.running:
            ip = manual_ip if (manual_ip and manual_ip != 'AUTO') else receiver.get_local_ip()
            qr = qrcode.QRCode(box_size=10, border=1)
            qr.add_data(ip); qr.make(fit=True)
            img = qr.make_image(fill_color="#00f3ff", back_color="transparent")
            buffered = BytesIO(); img.save(buffered, format="PNG")
            self.cached_qr = base64.b64encode(buffered.getvalue()).decode("utf-8")
            server_thread = threading.Thread(target=self._run, daemon=True)
            server_thread.start()

    def _run(self):
        try:
                 receiver.start_server(show_qr=False)
        except:     
            pass

    def stop_server(self):
        receiver.stop_server()
    
    # --- NEW API METHODS ---
    def get_mapping(self, p_idx):
        if p_idx < len(receiver.players):
            return getattr(receiver.players[p_idx], 'key_map', {})
        return {}

    def set_key_bind(self, p_idx, btn, key):
        if p_idx < len(receiver.players):
            p = receiver.players[p_idx]
            if not hasattr(p, 'key_map'): p.key_map = {}
            p.key_map[btn] = key
            print(f"Mapped P{p_idx+1} [{btn}] -> {key}")

    def test_rumble(self, p_idx, strength):
        receiver.trigger_rumble(p_idx, strength, strength)

if __name__ == '__main__':
    api = Api()
    window = webview.create_window('Nexus Core', html=HTML_TEMPLATE, js_api=api, width=1000, height=760, background_color='#020202', resizable=True)
    webview.start(debug=False)
    receiver.stop_server()
    