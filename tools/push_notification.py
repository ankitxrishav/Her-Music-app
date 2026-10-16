#!/usr/bin/env python3
import sys
import socket
import struct
import json
import argparse
import random
import time
import urllib.request

BROKERS = [
    ("broker.emqx.io", 1883),
    ("broker.hivemq.com", 1883),
    ("test.mosquitto.org", 1883)
]

def encode_remaining_length(length):
    encoded = bytearray()
    while True:
        digit = length % 128
        length //= 128
        if length > 0:
            digit |= 0x80
        encoded.append(digit)
        if length <= 0:
            break
    return bytes(encoded)

def publish_mqtt(host, port, topic, payload_str, retain=False):
    s = socket.create_connection((host, port), timeout=6)
    client_id = f"her_cli_{int(time.time())}_{random.randint(1000, 9999)}".encode("utf-8")
    proto_name = b"MQTT"
    var_header = struct.pack("!H", len(proto_name)) + proto_name + bytes([4, 2, 0, 60])
    payload = struct.pack("!H", len(client_id)) + client_id
    body = var_header + payload
    packet = bytes([0x10]) + encode_remaining_length(len(body)) + body
    s.sendall(packet)

    resp = s.recv(4)
    if len(resp) < 4 or resp[0] != 0x20 or resp[3] != 0:
        s.close()
        raise RuntimeError(f"Connection to {host}:{port} refused: {resp.hex() if resp else 'empty'}")

    t_bytes = topic.encode("utf-8")
    p_bytes = payload_str.encode("utf-8")
    var_header = struct.pack("!H", len(t_bytes)) + t_bytes
    body = var_header + p_bytes
    header_byte = 0x31 if retain else 0x30
    packet = bytes([header_byte]) + encode_remaining_length(len(body)) + body
    s.sendall(packet)

    s.sendall(b"\xe0\x00")
    s.close()
    return True

def broadcast_to_all(topic, payload_str, retain=False):
    success_count = 0
    for host, port in BROKERS:
        try:
            publish_mqtt(host, port, topic, payload_str, retain=retain)
            success_count += 1
            print(f"  [✓] Sent via MQTT ({host})")
        except Exception as e:
            print(f"  [x] MQTT failed via {host}: {e}")
    return success_count > 0

def publish_https(topic, title, message):
    try:
        url = f"https://ntfy.sh/{topic}"
        data = message.encode("utf-8")
        req = urllib.request.Request(url, data=data, method="POST")
        req.add_header("Title", title)
        req.add_header("Priority", "high")
        with urllib.request.urlopen(req, timeout=8) as resp:
            if resp.status == 200:
                print(f"  [✓] Sent via HTTPS relay (ntfy.sh/{topic})")
                return True
    except Exception as e:
        print(f"  [x] HTTPS relay failed: {e}")
    return False

def push_update(version):
    topic = "her_music/broadcast/update"
    ntfy_topic = "her_music_broadcast_update"
    payload = json.dumps({"version": version})
    print(f"Pushing app update notification for version {version}...")
    publish_https(ntfy_topic, "Her Music Update Available", version)
    broadcast_to_all(topic, payload, retain=True)
    print(f"\nBroadcasted update notification for v{version}!")

def push_custom(title, message, sender="Him", space_id=None):
    if space_id:
        topic = f"her_music/space/{space_id.strip()}/notify"
        ntfy_topic = f"her_music_space_{space_id.strip()}_notify"
    else:
        topic = "her_music/broadcast/notify"
        ntfy_topic = "her_music_broadcast_notify"

    payload = json.dumps({
        "title": title,
        "message": message,
        "sender": sender
    })
    print(f"Pushing notification: '{title}' - '{message}'...")
    publish_https(ntfy_topic, title, message)
    broadcast_to_all(topic, payload, retain=False)
    print("\nPush notification dispatched successfully across HTTPS and MQTT channels!")

def main():
    parser = argparse.ArgumentParser(description="Her Music Push Notification Tool")
    parser.add_argument("--update", help="Push an app update notification (e.g. 1.5.23)")
    parser.add_argument("--title", help="Custom notification title")
    parser.add_argument("--message", help="Custom notification body message")
    parser.add_argument("--sender", default="Him", help="Sender label (default: Him)")
    parser.add_argument("--space", help="Target couple space ID (optional, defaults to broadcast)")

    args = parser.parse_args()

    if args.update:
        push_update(args.update)
    elif args.message:
        title = args.title if args.title else "Her Music"
        push_custom(title, args.message, args.sender, args.space)
    else:
        print("Her Music Notification Tool")
        print("===========================")
        print("1) Push App Update Notification")
        print("2) Push Custom Message Notification")
        choice = input("Select an option (1/2): ").strip()
        if choice == "1":
            ver = input("Enter new version (e.g. 1.5.23): ").strip()
            if ver:
                push_update(ver)
        elif choice == "2":
            title = input("Enter notification title (e.g. Good Morning): ").strip()
            msg = input("Enter notification message: ").strip()
            sender = input("Enter sender name (default: Him): ").strip() or "Him"
            space = input("Enter couple space ID (leave empty for broadcast): ").strip() or None
            if msg:
                push_custom(title or "Her Music", msg, sender, space)
        else:
            parser.print_help()

if __name__ == "__main__":
    main()
