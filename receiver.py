import socket
import vgamepad as vg
import struct
import time
import subprocess
import os
import threading
import functools
import ctypes

# Try importing pynput for Mouse/Keyboard emulation
try:
    from pynput.keyboard import Controller as KController, KeyCode, Key
    from pynput.mouse import Controller as MController, Button
    keyboard = KController()
    mouse = MController()
    HAS_KEYBOARD = True
    print("Keyboard & Mouse emulation enabled (pynput).")
except ImportError:
    try:
        from pynput.keyboard import Controller as KController, KeyCode
        from pynput.mouse import Controller as MController, Button
        class Key:
            backspace = "backspace" 
            enter = "enter"
        keyboard = KController()
        mouse = MController()
        HAS_KEYBOARD = True
        print("Keyboard & Mouse emulation enabled (pynput) - Partial.")
    except ImportError:
        print("Warning: 'pynput' not found. Custom keys will be ignored.")
        HAS_KEYBOARD = False

# --- ADB / USB Support ---
def run_command_silently(cmd_args, **kwargs):
    if os.name == 'nt':
        kwargs['creationflags'] = 0x08000000 # CREATE_NO_WINDOW
    return subprocess.run(cmd_args, **kwargs)

def setup_adb():
    print("Checking ADB configuration (for USB Mode)...")
    adb_path = "adb" 
    found = False
    try:
        run_command_silently(["adb", "version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        found = True
    except: pass

    if not found:
        paths = [
            os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
            r"C:\Program Files\Android\android-sdk\platform-tools\adb.exe",
        ]
        for p in paths:
            if os.path.exists(p):
                adb_path = p
                found = True
                break
    
    if found:
        print(f"Found ADB: {adb_path}")
        try:
            run_command_silently([adb_path, "reverse", "tcp:6000", "tcp:6000"], check=True)
            print("ADB Reverse Forwarding active (TCP 6000).")
        except:
            print("ADB Setup skipped (Device not connected?).")
    else:
        print("Warning: ADB not found. USB mode requires 'adb reverse tcp:6000 tcp:6000'.")

# --- Utilities ---
def recvall(sock, n):
    data = b''
    while len(data) < n:
        try:
            chunk = sock.recv(n - len(data))
            if not chunk: return None
            data += chunk
        except: return None
    return data

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except: return "127.0.0.1"

# --- Globals ---
packet_counter = 0
current_pps = 0
MAX_PLAYERS = 4
HAPTICS_ENABLED = True
IS_DS4_GLOBAL = False 
running = False

server_sock = None
discovery_sock = None

# --- Player Class ---
class PlayerSession:
    def __init__(self, index):
        self.index = index
        self.gamepad = None
        self.connected = False
        self.conn = None
        self.addr = None
        
        # State
        self.packet_count = 0
        self.current_keys = set()
        
        # Mouse Mode State
        self.was_mouse_mode = False
        self.center_roll = 0
        self.center_pitch = 0
        self.msg_l_clicked = False
        self.msg_r_clicked = False
        self.last_mouse_btns = 0
        self.joystick_center_roll = None
        self.joystick_center_pitch = None
        
        # Mapping / Visuals
        self.key_map = {}
        self.visuals = {'lx':0,'ly':0,'rx':0,'ry':0,'lt':0,'rt':0,'active':False}

    def get_gamepad(self, is_ds4=False):
        if self.gamepad: return self.gamepad
        try:
            if is_ds4:
                self.gamepad = vg.VDS4Gamepad()
                print(f"Player {self.index+1}: DS4 Created")
            else:
                self.gamepad = vg.VX360Gamepad()
                # Use partial to bind player_idx
                self.gamepad.register_notification(callback_function=functools.partial(rumble_callback, self.index))
                print(f"Player {self.index+1}: X360 Created")
        except Exception as e:
            print(f"Failed to create gamepad: {e}")
        return self.gamepad

    def reset(self):
        if self.gamepad:
            self.gamepad.reset()
            self.gamepad.update()
        self.connected = False
        self.conn = None
        self.packet_count = 0
        self.visuals['active'] = False

players = [PlayerSession(i) for i in range(MAX_PLAYERS)]

# --- Processing ---
def map_stick(val):
    # -127..127 -> -1.0..1.0 -> Int16
    norm = val / 127.0
    if norm > 1.0: norm = 1.0
    if norm < -1.0: norm = -1.0
    return norm, int(norm * 32000)

# FIXED SIGNATURE: match vgamepad expectation strictly
def rumble_callback(player_idx, client, target, large_motor, small_motor, led_number, user_data):
    if not HAPTICS_ENABLED: return
    try:
        if player_idx < len(players):
            p = players[player_idx]
            if p.connected and p.conn:
                # Send just large/small (0-255)
                # Note: large_motor/small_motor are likely int 0-255 from vgamepad
                p.conn.sendall(bytes([0x03, int(large_motor), int(small_motor)]))
    except: pass

def trigger_rumble(player_idx, weak, strong):
    if not HAPTICS_ENABLED: return
    try:
        if player_idx < len(players):
            p = players[player_idx]
            if p.connected and p.conn:
                p.conn.sendall(bytes([0x03, int(strong*255), int(weak*255)]))
    except: pass

def process_gamepad_data(player, data):
    global packet_counter
    packet_counter += 1
    player.packet_count += 1
    
    if not player.gamepad: return

    # Parse
    lx, ly, rx, ry = data[0], data[1], data[2], data[3]
    btns_low, btns_high, lt, rt = data[4], data[5], data[6], data[7]
    
    # Python bytes are 0-255. Protocol sends -127..127 as signed bytes.
    # We must convert 0..255 to -128..127
    def to_signed(b): return b - 256 if b > 127 else b
    
    lx = to_signed(lx)
    ly = to_signed(ly)
    rx = to_signed(rx)
    ry = to_signed(ry)
    
    # Gyro (Big Endian Short)
    roll = int.from_bytes(data[8:10], 'big', signed=True)
    pitch = int.from_bytes(data[10:12], 'big', signed=True)
    
    is_mouse_mode = (btns_high & 0x40) != 0

    # Visuals
    player.visuals = {
        'lx': lx/127.0, 'ly': -ly/127.0,
        'rx': rx/127.0, 'ry': -ry/127.0,
        'lt': lt/255.0, 'rt': rt/255.0,
        'active': True
    }

    # Mouse Mode Logic (Player 1 only typically)
    if player.index == 0 and HAS_KEYBOARD and is_mouse_mode:
        if not player.was_mouse_mode:
            player.center_roll = roll
            player.center_pitch = pitch
        
        player.was_mouse_mode = True
        
        # Deadzone
        dr = roll - player.center_roll
        dp = pitch - player.center_pitch
        
        dx, dy = 0, 0
        if abs(dr) > 300: dx = int(dr / 200)
        if abs(dp) > 300: dy = int(dp / 200)
        mouse.move(dx, dy)
        
        # Click Emulation
        if rt > 100 and not player.msg_l_clicked:
            mouse.press(Button.left); player.msg_l_clicked = True
        elif rt <= 100 and player.msg_l_clicked:
            mouse.release(Button.left); player.msg_l_clicked = False
            
        if lt > 100 and not player.msg_r_clicked:
            mouse.press(Button.right); player.msg_r_clicked = True
        elif lt <= 100 and player.msg_r_clicked:
            mouse.release(Button.right); player.msg_r_clicked = False
            
        # Zero out controller inputs while in mouse mode
        lt = rt = 0
    else:
        player.was_mouse_mode = False
        if player.msg_l_clicked: mouse.release(Button.left); player.msg_l_clicked=False
        if player.msg_r_clicked: mouse.release(Button.right); player.msg_r_clicked=False

    # Helper to apply to gamepad
    gp = player.gamepad
    
    lx_f, lx_i = map_stick(lx)
    ly_f, ly_i = map_stick(ly)
    rx_f, rx_i = map_stick(rx)
    ry_f, ry_i = map_stick(ry)
    
    # Invert Y for Games
    ly_f, ly_i = -ly_f, -ly_i
    ry_f, ry_i = -ry_f, -ry_i

    # X360 vs DS4
    if isinstance(gp, vg.VDS4Gamepad):
        gp.left_joystick_float(lx_f, ly_f)
        gp.right_joystick_float(rx_f, ry_f)
        gp.left_trigger(lt)
        gp.right_trigger(rt)
        
        # DS4 Buttons
        b = vg.DS4_BUTTONS
        map_b = [
            (0x01, b.DS4_BUTTON_CROSS), (0x02, b.DS4_BUTTON_CIRCLE),
            (0x04, b.DS4_BUTTON_SQUARE), (0x08, b.DS4_BUTTON_TRIANGLE),
            (0x10, b.DS4_BUTTON_SHOULDER_LEFT), (0x20, b.DS4_BUTTON_SHOULDER_RIGHT),
            (0x40, b.DS4_BUTTON_SHARE), (0x80, b.DS4_BUTTON_OPTIONS)
        ]
        map_h = [
            (0x01, b.DS4_BUTTON_THUMB_LEFT), (0x02, b.DS4_BUTTON_THUMB_RIGHT),
            (0x04, b.DS4_BUTTON_DPAD_NORTH), (0x08, b.DS4_BUTTON_DPAD_SOUTH),
            (0x10, b.DS4_BUTTON_DPAD_WEST), (0x20, b.DS4_BUTTON_DPAD_EAST)
        ]
        
        for mask, btn in map_b:
            if btns_low & mask: gp.press_button(btn)
            else: gp.release_button(btn)
        for mask, btn in map_h:
            if btns_high & mask: gp.press_button(btn)
            else: gp.release_button(btn)
            
    else:
        # X360
        gp.left_joystick(x_value=lx_i, y_value=ly_i)
        
        # FIXED: Use correct internal variables
        gp.right_joystick(x_value=rx_i, y_value=ry_i)
        
        gp.left_trigger(lt)
        gp.right_trigger(rt)
        
        b = vg.XUSB_BUTTON
        map_b = [
            (0x01, b.XUSB_GAMEPAD_A), (0x02, b.XUSB_GAMEPAD_B),
            (0x04, b.XUSB_GAMEPAD_X), (0x08, b.XUSB_GAMEPAD_Y),
            (0x10, b.XUSB_GAMEPAD_LEFT_SHOULDER), (0x20, b.XUSB_GAMEPAD_RIGHT_SHOULDER),
            (0x40, b.XUSB_GAMEPAD_BACK), (0x80, b.XUSB_GAMEPAD_START)
        ]
        map_h = [
            (0x01, b.XUSB_GAMEPAD_LEFT_THUMB), (0x02, b.XUSB_GAMEPAD_RIGHT_THUMB),
            (0x04, b.XUSB_GAMEPAD_DPAD_UP), (0x08, b.XUSB_GAMEPAD_DPAD_DOWN),
            (0x10, b.XUSB_GAMEPAD_DPAD_LEFT), (0x20, b.XUSB_GAMEPAD_DPAD_RIGHT)
        ]
        
        for mask, btn in map_b:
            if btns_low & mask: gp.press_button(btn)
            else: gp.release_button(btn)
        for mask, btn in map_h:
            if btns_high & mask: gp.press_button(btn)
            else: gp.release_button(btn)

    gp.update()


def handle_client(conn, addr, player):
    print(f"MSG: Player {player.index+1} connected from {addr}")
    player.connected = True
    player.conn = conn
    player.addr = addr
    player.get_gamepad(IS_DS4_GLOBAL)
    
    conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)

    while True:
        try:
            head = recvall(conn, 1)
            if not head: break
            
            h = head[0]
            
            if h == 0x10: # HELLO
                conn.sendall(b'\x11') # READY
                
            elif h == 0x01: # DATA
                data = recvall(conn, 16)
                if data: process_gamepad_data(player, data)
                
            elif h == 0xF0: # PING
                conn.sendall(b'\xF1') # PONG
                
            elif h == 0x02: # TEXT
                slen = recvall(conn, 1)
                if slen:
                    txt = recvall(conn, slen[0])
                    if txt and HAS_KEYBOARD and player.index == 0:
                        try:
                            s = txt.decode('utf-8')
                            for c in s:
                                if c == '\b': 
                                    keyboard.press(Key.backspace)
                                    keyboard.release(Key.backspace)
                                elif c == '\n':
                                    keyboard.press(Key.enter)
                                    keyboard.release(Key.enter)
                                else: keyboard.type(c)
                        except: pass

            elif h == 0x04: # MOUSE
                d = recvall(conn, 3)
                if d and HAS_KEYBOARD and player.index == 0:
                    dx = d[0] - 256 if d[0] > 127 else d[0]
                    dy = d[1] - 256 if d[1] > 127 else d[1]
                    btns = d[2]
                    mouse.move(dx, dy)
                    if btns & 1: mouse.press(Button.left)
                    else: mouse.release(Button.left)
                    if btns & 2: mouse.press(Button.right)
                    else: mouse.release(Button.right)
                    
            elif h == 0x05: # SCROLL
                d = recvall(conn, 2)
                if d and HAS_KEYBOARD and player.index == 0:
                    dx = d[0] - 256 if d[0] > 127 else d[0]
                    dy = d[1] - 256 if d[1] > 127 else d[1]
                    mouse.scroll(dx, dy)
                    
        except Exception as e:
            print(f"P{player.index+1} Error: {e}")
            break
            
    print(f"MSG: Player {player.index+1} Disconnected")
    player.reset()


def discovery_loop():
    global discovery_sock, running
    try:
        discovery_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        discovery_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        discovery_sock.bind(('0.0.0.0', 6001))
        
        while running:
            try:
                data, addr = discovery_sock.recvfrom(1024)
                if data == b'DISCOVER_CONTROLLER':
                    resp = f"PC_SERVER:{socket.gethostname()}".encode()
                    discovery_sock.sendto(resp, addr)
            except: break
    except Exception as e:
        print(f"Discovery Error: {e}")
    finally:
        if discovery_sock: discovery_sock.close()

def start_server(ip_bind=None, show_qr=False):
    global running, server_sock
    
    stop_server()
    running = True
    setup_adb()
    
    # Firewall
    try:
        subprocess.run(["netsh", "advfirewall", "firewall", "delete", "rule", "name=NexusControllerTCP"], capture_output=True)
        subprocess.run([
            "netsh", "advfirewall", "firewall", "add", "rule", 
            "name=NexusControllerTCP", "dir=in", "action=allow", 
            "protocol=TCP", "localport=6000"
        ], capture_output=True)
    except: pass

    threading.Thread(target=discovery_loop, daemon=True).start()
    
    try:
        server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_sock.bind(('0.0.0.0', 6000))
        server_sock.listen(4)
        print("Server Listening on TCP 6000")
        
        while running:
            try:
                conn, addr = server_sock.accept()
                
                # Assign to slot
                assigned = False
                for p in players:
                    if not p.connected:
                        t = threading.Thread(target=handle_client, args=(conn, addr, p))
                        t.daemon = True
                        t.start()
                        assigned = True
                        break
                        
                if not assigned:
                    print(f"Connection rejected from {addr}: Server Full (Max {MAX_PLAYERS})")
                    conn.close()
            except: break
            
    except Exception as e:
        print(f"Server Startup Error: {e}")
    finally:
        stop_server()

def stop_server():
    global running, server_sock, discovery_sock
    running = False
    
    if server_sock:
        try: server_sock.close()
        except: pass
        server_sock = None
        
    if discovery_sock:
        try: discovery_sock.close()
        except: pass
        discovery_sock = None
        
    for p in players:
        if p.conn:
            try: p.conn.close()
            except: pass
        p.reset()

if __name__ == "__main__":
    start_server()
