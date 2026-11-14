#!/usr/bin/env python3
"""usage: rcon.py "command" ["command" ...]

Runs commands on the dev server (no leading slash) and prints the answers.
The port and password come from runs/server/server.properties, which setup.sh fills in.
"""
import pathlib
import socket
import struct
import sys

LOGIN, COMMAND = 3, 2


def settings():
    file = pathlib.Path(__file__).resolve().parents[2] / "runs" / "server" / "server.properties"
    lines = (line for line in file.read_text().splitlines() if "=" in line and not line.startswith("#"))
    return dict(line.split("=", 1) for line in lines)


def send(sock, request, kind, text):
    body = struct.pack("<ii", request, kind) + text.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(body)) + body)


def receive(sock):
    def read(count):
        data = b""
        while len(data) < count:
            chunk = sock.recv(count - len(data))
            if not chunk:
                raise ConnectionError("the server closed the connection")
            data += chunk
        return data

    body = read(struct.unpack("<i", read(4))[0])
    return struct.unpack("<i", body[:4])[0], body[8:-2].decode()


def run(props, command):
    # One connection per command: a very long answer comes in several parts and only the first is read.
    with socket.create_connection(("127.0.0.1", int(props.get("rcon.port", "25575"))), timeout=30) as sock:
        send(sock, 1, LOGIN, props.get("rcon.password", ""))
        if receive(sock)[0] == -1:
            sys.exit("The server refused the RCON password in server.properties. Was it changed while the server ran?")
        send(sock, 2, COMMAND, command)
        return receive(sock)[1]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    props = settings()
    for command in sys.argv[1:]:
        print(">", command, "\n ", run(props, command))


if __name__ == "__main__":
    main()
