import asyncio
import socket
from pynput.mouse import Button, Controller

class InputReceiver:
    def __init__(self, host='0.0.0.0', port=9999):
        self.host = host
        self.port = port
        self.mouse = Controller()
        self.screen_width = 1920 
        self.screen_height = 1080
        self.scroll_x_accum = 0.0
        self.scroll_y_accum = 0.0
        
        # Get actual screen size
        import mss
        with mss.mss() as sct:
            monitor = sct.monitors[1]
            self.screen_width = monitor['width']
            self.screen_height = monitor['height']
            print(f"Server Screen Size: {self.screen_width}x{self.screen_height}")

    async def handle_client(self, reader, writer):
        print("Input client connected")
        try:
            while True:
                data = await reader.read(1024) # Increased buffer
                if not data:
                    break
                messages = data.decode().strip().split('\n')
                for message in messages:
                    if message:
                        self.process_input(message)
        except Exception as e:
            print(f"Input handling error: {e}")
        finally:
            print("Input client disconnected")
            writer.close()
            await writer.wait_closed()

    def process_input(self, message):
        try:
            parts = message.split(',')
            if len(parts) < 3:
                return
            
            cmd_type = parts[0]
            x = float(parts[1])
            y = float(parts[2])
            
            if cmd_type == "SCROLL":
                # Accumulate scroll deltas
                self.scroll_x_accum += x
                self.scroll_y_accum += y
                
                dx = int(self.scroll_x_accum)
                dy = int(self.scroll_y_accum)
                
                if dx != 0 or dy != 0:
                    self.mouse.scroll(dx, dy)
                    self.scroll_x_accum -= dx
                    self.scroll_y_accum -= dy
                return

            abs_x = int(x * self.screen_width)
            abs_y = int(y * self.screen_height)
            
            # Clamp coordinates
            abs_x = max(0, min(abs_x, self.screen_width - 1))
            abs_y = max(0, min(abs_y, self.screen_height - 1))
            
            if cmd_type in ["DOWN", "UP", "CLICK", "DBL_CLICK"]:
                print(f"{cmd_type} at {abs_x}, {abs_y} (Raw: {x:.3f}, {y:.3f})")

            if cmd_type == "MOVE":
                self.mouse.position = (abs_x, abs_y)
            elif cmd_type == "DOWN":
                self.mouse.position = (abs_x, abs_y)
                self.mouse.press(Button.left)
            elif cmd_type == "UP":
                self.mouse.position = (abs_x, abs_y)
                self.mouse.release(Button.left)
            elif cmd_type == "CLICK":
                self.mouse.position = (abs_x, abs_y)
                self.mouse.click(Button.left, 1)
            elif cmd_type == "DBL_CLICK":
                self.mouse.position = (abs_x, abs_y)
                self.mouse.click(Button.left, 2)
                
        except ValueError:
            pass

    async def start(self):
        server = await asyncio.start_server(
            self.handle_client, self.host, self.port)
        print(f"Input receiver listening on {self.host}:{self.port}")
        async with server:
            await server.serve_forever()
