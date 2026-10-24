"""The key gate in front of the live phone's screen (ws-scrcpy).

ws-scrcpy has no password. Its page and its live connection (a WebSocket) both live under one path, which the workflow
sets to /<LIVE_PHONE_KEY>/. This gate is the only thing the public tunnel reaches: it reads the first line of each new
connection and passes the connection on to ws-scrcpy only if the path starts with the key. Anyone without the key gets
"404 Not found", so knowing the tunnel's web address alone is not enough.

Standard library only. Run:
  LIVE_PHONE_KEY=my-key python3 tools/virtual-phone/gate.py --listen 8080 --to 8000
"""
import argparse
import asyncio
import hmac
import os

NOT_FOUND = b"HTTP/1.1 404 Not Found\r\nContent-Length: 10\r\nConnection: close\r\n\r\nNot found\n"


def key_ok(request_line: bytes, key: str) -> bool:
    """True if the request's path starts with /<key>/ (or is exactly /<key>). Compared in constant time."""
    parts = request_line.split(b" ")
    if len(parts) < 2 or not key:
        return False
    first = parts[1].split(b"?", 1)[0].lstrip(b"/").split(b"/", 1)[0]
    return hmac.compare_digest(first, key.encode())


async def pipe(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        writer.close()


async def handle(client_r, client_w, key: str, to_port: int) -> None:
    try:
        head = await asyncio.wait_for(client_r.readuntil(b"\r\n"), 15)
    except (asyncio.TimeoutError, asyncio.IncompleteReadError, asyncio.LimitOverrunError, ConnectionError):
        client_w.close()
        return
    if not key_ok(head, key):
        client_w.write(NOT_FOUND)
        await client_w.drain()
        client_w.close()
        return
    # "/<key>" without the slash: send the browser to "/<key>/" so the page's relative links work.
    path = head.split(b" ")[1]
    if path.rstrip(b"/") == b"/" + key.encode() and not path.endswith(b"/"):
        client_w.write(b"HTTP/1.1 301 Moved\r\nLocation: /" + key.encode() + b"/\r\nContent-Length: 0\r\n"
                       b"Connection: close\r\n\r\n")
        await client_w.drain()
        client_w.close()
        return
    try:
        up_r, up_w = await asyncio.open_connection("127.0.0.1", to_port)
    except OSError:
        client_w.close()
        return
    up_w.write(head)  # the line we already read, then everything else as it comes (WebSockets included)
    await asyncio.gather(pipe(client_r, up_w), pipe(up_r, client_w))


async def main() -> None:
    ap = argparse.ArgumentParser(description="Key gate for the live phone screen")
    ap.add_argument("--listen", type=int, default=8080)
    ap.add_argument("--to", type=int, default=8000)
    args = ap.parse_args()
    key = os.environ.get("LIVE_PHONE_KEY", "")
    if not key:
        raise SystemExit("LIVE_PHONE_KEY is not set")
    server = await asyncio.start_server(lambda r, w: handle(r, w, key, args.to), "127.0.0.1", args.listen)
    print(f"gate: 127.0.0.1:{args.listen} -> 127.0.0.1:{args.to}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
