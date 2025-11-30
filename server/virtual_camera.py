import asyncio
import cv2
import numpy as np
from aiortc.contrib.media import MediaBlackhole

class VirtualCameraStub:
    def __init__(self):
        self.track = None
        self.task = None

    def add_track(self, track):
        self.track = track
        self.task = asyncio.create_task(self.consume_track())
        print("Virtual Camera: Track added")

    async def consume_track(self):
        print("Virtual Camera: Started consuming frames")
        try:
            while True:
                frame = await self.track.recv()
                
                # Convert to numpy array (YUV/RGB -> BGR for OpenCV)
                img = frame.to_ndarray(format="bgr24")
                
                # Display
                cv2.imshow("Android Camera", img)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
                    
        except Exception as e:
            print(f"Virtual Camera error: {e}")
        finally:
            print("Virtual Camera: Stopped")
            cv2.destroyAllWindows()

    async def stop(self):
        if self.task:
            self.task.cancel()
            try:
                await self.task
            except asyncio.CancelledError:
                pass
