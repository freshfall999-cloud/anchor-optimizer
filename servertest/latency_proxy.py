#!/usr/bin/env python3
"""Tiny TCP proxy that adds a fixed one-way delay in each direction, to simulate ping.

    python3 latency_proxy.py LISTEN_PORT TARGET_HOST:TARGET_PORT ONE_WAY_DELAY_MS

A 50 ms one-way delay gives a 100 ms round trip. Byte order is preserved.
"""
import asyncio
import socket
import sys
import time


def nodelay(writer):
    sock = writer.get_extra_info("socket")
    if sock is not None:
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)


async def pipe(reader, writer, delay):
    queue = asyncio.Queue()

    async def read():
        while True:
            data = await reader.read(65536)
            await queue.put((time.monotonic() + delay, data))
            if not data:
                return

    async def write():
        while True:
            due, data = await queue.get()
            wait = due - time.monotonic()
            if wait > 0:
                await asyncio.sleep(wait)
            if not data:
                writer.close()
                return
            writer.write(data)
            await writer.drain()

    try:
        await asyncio.gather(read(), write())
    except (ConnectionError, OSError):
        writer.close()


async def main():
    listen_port = int(sys.argv[1])
    target_host, target_port = sys.argv[2].rsplit(":", 1)
    delay = int(sys.argv[3]) / 1000.0

    async def handle(client_reader, client_writer):
        server_reader, server_writer = await asyncio.open_connection(target_host, int(target_port))
        nodelay(client_writer)
        nodelay(server_writer)
        await asyncio.gather(
            pipe(client_reader, server_writer, delay),
            pipe(server_reader, client_writer, delay),
            return_exceptions=True,
        )

    server = await asyncio.start_server(handle, "127.0.0.1", listen_port)
    print(f"latency proxy :{listen_port} -> {target_host}:{target_port}, +{int(delay * 1000)} ms each way", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
