import asyncio
import json
import os
from aiohttp import web
from webrtc_manager import WebRTCManager
from input_receiver import InputReceiver

async def offer(request):
    params = await request.json()
    response = await webrtc_manager.offer(params)
    return web.json_response(response)

async def index(request):
    content = open(os.path.join(os.path.dirname(__file__), "index.html"), "r").read()
    return web.Response(content_type="text/html", text=content)

async def on_shutdown(app):
    await webrtc_manager.on_shutdown()

async def start_background_tasks(app):
    app['input_receiver'] = asyncio.create_task(input_receiver.start())

async def cleanup_background_tasks(app):
    app['input_receiver'].cancel()
    await app['input_receiver']

if __name__ == "__main__":
    webrtc_manager = WebRTCManager()
    input_receiver = InputReceiver(port=9999)

    app = web.Application()
    app.router.add_post("/offer", offer)
    
    # Optional: Serve a simple test page if we wanted to test in browser
    # app.router.add_get("/", index)

    app.on_startup.append(start_background_tasks)
    app.on_shutdown.append(on_shutdown)
    app.on_cleanup.append(cleanup_background_tasks)

    print("Starting server on http://0.0.0.0:8080")
    web.run_app(app, host="0.0.0.0", port=8080)
