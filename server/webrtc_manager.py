import asyncio
import json
import logging
from aiortc import RTCPeerConnection, RTCSessionDescription
from screen_capture import ScreenCaptureTrack

class WebRTCManager:
    def __init__(self):
        self.pcs = set()

    async def offer(self, params):
        offer = RTCSessionDescription(sdp=params["sdp"], type=params["type"])

        pc = RTCPeerConnection()
        self.pcs.add(pc)

        @pc.on("connectionstatechange")
        async def on_connectionstatechange():
            print(f"Connection state is {pc.connectionState}")
            if pc.connectionState == "failed":
                await pc.close()
                self.pcs.discard(pc)
            elif pc.connectionState == "closed":
                self.pcs.discard(pc)

        # Add screen capture track
        # Note: In a real app we might want to manage the track lifecycle better
        # to avoid creating multiple mss instances if not needed, or reuse one.
        # For this prototype, creating a new track per connection is fine.
        video_track = ScreenCaptureTrack()
        pc.addTrack(video_track)

        @pc.on("track")
        def on_track(track):
            if track.kind == "video":
                print("Received video track from client")
                from virtual_camera import VirtualCameraStub
                vc = VirtualCameraStub()
                vc.add_track(track)
                # Keep reference to avoid GC? 
                # In real app, manage lifecycle.
                self.virtual_camera = vc

        await pc.setRemoteDescription(offer)

        answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)

        return {
            "sdp": pc.localDescription.sdp,
            "type": pc.localDescription.type
        }

    async def on_shutdown(self):
        coros = [pc.close() for pc in self.pcs]
        await asyncio.gather(*coros)
        self.pcs.clear()
