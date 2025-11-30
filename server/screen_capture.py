import asyncio
import time
import mss
import numpy as np
import cv2
from av import VideoFrame
from aiortc.mediastreams import VideoStreamTrack

class ScreenCaptureTrack(VideoStreamTrack):
    """
    A VideoStreamTrack that captures the screen using mss.
    """
    kind = "video"

    def __init__(self):
        super().__init__()
        self.sct = mss.mss()
        # Capture the primary monitor
        self.monitor = self.sct.monitors[1]
        self._start = time.time()

    async def recv(self):
        # Calculate timestamp
        pts, time_base = await self.next_timestamp()

        # Capture screen
        # mss returns BGRA
        sct_img = self.sct.grab(self.monitor)
        
        # Convert to numpy array
        frame = np.array(sct_img)
        
        # Drop alpha channel (BGRA -> BGR)
        frame = frame[:, :, :3]
        
        # Convert BGR to RGB (aiortc expects RGB or YUV)
        # Actually av.VideoFrame.from_ndarray expects specific formats.
        # Let's convert to RGB for simplicity.
        frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        # Create VideoFrame
        new_frame = VideoFrame.from_ndarray(frame, format="rgb24")
        new_frame.pts = pts
        new_frame.time_base = time_base

        # Simulate frame rate (e.g., 30 FPS)
        # wait_time = 1/30 - (time.time() - self._last_time)
        # if wait_time > 0:
        #     await asyncio.sleep(wait_time)
        # self._last_time = time.time()
        
        # Simple sleep to avoid 100% CPU usage if not throttled elsewhere
        # But aiortc pulls frames, so we rely on next_timestamp logic mostly.
        # However, screen capture is fast. Let's add a small sleep to cap FPS roughly if needed.
        # For now, let's just return as fast as possible or let aiortc handle timing.
        
        return new_frame
