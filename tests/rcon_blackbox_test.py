#!/usr/bin/env python3
"""
EM-WM-Bridge RCON 黑盒测试脚本

通过 RCON 协议连接 Minecraft 服务端，执行 /emwm test 命令，
解析插件返回的 JSON 结果，输出格式化测试报告。

用法:
    python rcon_blackbox_test.py [--host HOST] [--port PORT] --password PASSWORD
    python rcon_blackbox_test.py  # 从环境变量读取配置

环境变量:
    RCON_HOST      服务器地址 (默认: localhost)
    RCON_PORT      RCON 端口   (默认: 25575)
    RCON_PASSWORD  RCON 密码   (必需)

退出码:
    0 - 全部测试通过
    1 - 有测试失败
    2 - 连接/协议错误
"""

import json
import os
import socket
import struct
import sys
import argparse
import time
from datetime import datetime


# ============================================================
# RCON 协议实现 (无外部依赖)
# ============================================================

RCON_PACKET_TYPE_AUTH = 3
RCON_PACKET_TYPE_AUTH_RESPONSE = 2
RCON_PACKET_TYPE_EXECCOMMAND = 2
RCON_PACKET_TYPE_RESPONSE_VALUE = 0

RCON_MAX_RETRIES = 3
RCON_RECV_TIMEOUT = 10  # 秒


class RCONError(Exception):
    """RCON 协议错误"""
    pass


class RCONConnectionError(RCONError):
    """RCON 连接错误"""
    pass


class RCONAuthenticationError(RCONError):
    """RCON 认证错误"""
    pass


class RCONClient:
    """简易 RCON 客户端，纯 Python 实现。"""

    def __init__(self, host: str, port: int, password: str):
        self.host = host
        self.port = port
        self.password = password
        self._sock = None
        self._request_id = 0

    def connect(self):
        """建立 TCP 连接并认证。"""
        try:
            self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._sock.settimeout(RCON_RECV_TIMEOUT)
            self._sock.connect((self.host, self.port))
        except (socket.error, ConnectionRefusedError) as e:
            raise RCONConnectionError(
                f"无法连接到 {self.host}:{self.port}: {e}"
            ) from e

        # 发送认证包
        self._authenticate()

    def _authenticate(self):
        """RCON 认证流程。"""
        # 发送 AUTH 包 (type=3)
        req_id = self._next_request_id()
        payload = self.password.encode("utf-8") + b"\x00\x00"
        packet = self._build_packet(req_id, RCON_PACKET_TYPE_AUTH, payload)
        self._sock.sendall(packet)

        # 读取认证响应
        resp_id, resp_type, _ = self._read_packet()

        if resp_id == -1:
            raise RCONAuthenticationError("RCON 认证失败: 密码错误")
        if resp_type != RCON_PACKET_TYPE_AUTH_RESPONSE:
            raise RCONError(
                f"认证响应类型异常: expected {RCON_PACKET_TYPE_AUTH_RESPONSE}, got {resp_type}"
            )

    def send_command(self, command: str) -> str:
        """发送命令并返回响应文本。"""
        req_id = self._next_request_id()
        payload = command.encode("utf-8") + b"\x00\x00"
        packet = self._build_packet(req_id, RCON_PACKET_TYPE_EXECCOMMAND, payload)
        self._sock.sendall(packet)

        # 读取响应 (可能有多包，用 sentinel 包检测结束)
        # 发送一个空命令作为 sentinel
        sentinel_packet = self._build_packet(
            self._next_request_id(),
            RCON_PACKET_TYPE_EXECCOMMAND,
            b"\x00\x00",
        )

        # 读取第一个响应包
        _, _, body1 = self._read_packet()

        # 尝试读取更多数据 (多包响应)
        # 发送 sentinel
        self._sock.sendall(sentinel_packet)

        # 读取直到收到 sentinel 响应
        full_response = body1
        retries = 0
        while retries < RCON_MAX_RETRIES:
            try:
                _, _, body = self._read_packet()
                if not body:
                    break
                full_response += body
            except socket.timeout:
                break
            retries += 1

        return full_response

    def disconnect(self):
        """关闭连接。"""
        if self._sock:
            try:
                self._sock.close()
            except socket.error:
                pass
            self._sock = None

    def _next_request_id(self) -> int:
        self._request_id += 1
        return self._request_id

    @staticmethod
    def _build_packet(req_id: int, ptype: int, payload: bytes) -> bytes:
        """构建 RCON 数据包。"""
        # Length = 4 (req_id) + 4 (type) + len(payload) + 1 (padding)
        length = 4 + 4 + len(payload) + 1
        header = struct.pack("<iii", length, req_id, ptype)
        return header + payload

    def _read_packet(self) -> tuple:
        """读取一个 RCON 数据包，返回 (req_id, type, body)。"""
        # 读取 Length (4 bytes)
        length_data = self._recv_exactly(4)
        if length_data is None:
            raise RCONError("连接已关闭: 无法读取包长度")
        length = struct.unpack("<i", length_data)[0]

        if length < 10 or length > 4096:
            raise RCONError(f"包长度异常: {length}")

        # 读取剩余数据
        data = self._recv_exactly(length)
        if data is None:
            raise RCONError("连接已关闭: 无法读取包数据")

        req_id = struct.unpack("<i", data[0:4])[0]
        ptype = struct.unpack("<i", data[4:8])[0]
        body = data[8:-2]  # 去掉两个 padding 字节
        body_str = body.decode("utf-8", errors="replace")

        return req_id, ptype, body_str

    def _recv_exactly(self, n: int) -> bytes | None:
        """精确读取 n 个字节。"""
        data = b""
        while len(data) < n:
            try:
                chunk = self._sock.recv(n - len(data))
            except socket.timeout:
                return None
            if not chunk:
                return None
            data += chunk
        return data


# ============================================================
# 测试结果解析与报告
# ============================================================

RESULT_MARKER = "[EMWM_TEST_RESULT]"


def parse_test_response(response: str) -> dict:
    """从 RCON 响应中提取 JSON 测试结果。"""
    # 查找标记
    idx = response.find(RESULT_MARKER)
    if idx == -1:
        raise ValueError(
            f"响应中未找到 {RESULT_MARKER} 标记。\n"
            f"原始响应: {response[:500]}"
        )

    json_str = response[idx + len(RESULT_MARKER):].strip()

    # 尝试提取 JSON (可能有额外文本)
    # 找到第一个 { 和最后一个 }
    start = json_str.find("{")
    end = json_str.rfind("}")
    if start == -1 or end == -1:
        raise ValueError(f"无法在响应中提取 JSON: {json_str[:200]}")

    json_str = json_str[start:end + 1]

    try:
        return json.loads(json_str)
    except json.JSONDecodeError as e:
        raise ValueError(f"JSON 解析失败: {e}\n原始: {json_str[:500]}") from e


def print_report(result: dict) -> bool:
    """打印格式化测试报告，返回是否全部通过。"""
    total = result.get("total", 0)
    passed = result.get("passed", 0)
    failed = result.get("failed", 0)
    duration = result.get("duration_ms", 0)
    version = result.get("version", "unknown")
    tests = result.get("tests", [])

    # 头部
    print()
    print("=" * 60)
    print(f"  EM-WM-Bridge 黑盒测试报告")
    print(f"  版本: {version}  |  耗时: {duration}ms")
    print(f"  时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # 逐项结果
    for t in tests:
        name = t.get("name", "?")
        ok = t.get("passed", False)
        ms = t.get("duration_ms", 0)
        detail = t.get("detail", "")

        status = "PASS" if ok else "FAIL"
        icon = "[PASS]" if ok else "[FAIL]"
        color = "\033[92m" if ok else "\033[91m"
        reset = "\033[0m"

        # 截断过长的 detail
        if len(detail) > 80:
            detail = detail[:77] + "..."

        print(f"  {color}{icon}{reset} {name:<28} {ms:>4}ms  {detail}")

    # 汇总
    print()
    print("-" * 60)
    if failed == 0:
        print(f"  \033[92m全部通过! {passed}/{total} PASS, 0 FAIL\033[0m")
    else:
        print(f"  \033[91m{passed}/{total} PASS, {failed} FAIL\033[0m")

        # 列出失败项
        print()
        print("  失败详情:")
        for t in tests:
            if not t.get("passed", False):
                print(f"    - {t.get('name')}: {t.get('detail', '')}")
    print()
    print("=" * 60)

    return failed == 0


# ============================================================
# 主入口
# ============================================================

def main():
    parser = argparse.ArgumentParser(
        description="EM-WM-Bridge RCON 黑盒测试脚本"
    )
    parser.add_argument(
        "--host",
        default=os.environ.get("RCON_HOST", "localhost"),
        help="服务器地址 (默认: localhost)",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=int(os.environ.get("RCON_PORT", "25575")),
        help="RCON 端口 (默认: 25575)",
    )
    parser.add_argument(
        "--password",
        default=os.environ.get("RCON_PASSWORD", ""),
        help="RCON 密码 (必需, 可通过环境变量 RCON_PASSWORD 设置)",
    )
    parser.add_argument(
        "--command",
        default="emwm test",
        help="执行的测试命令 (默认: emwm test)",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="测试超时秒数 (默认: 30)",
    )
    args = parser.parse_args()

    if not args.password:
        print("错误: 未提供 RCON 密码", file=sys.stderr)
        print("请使用 --password 参数或设置 RCON_PASSWORD 环境变量", file=sys.stderr)
        sys.exit(2)

    # 连接并执行测试
    client = RCONClient(args.host, args.port, args.password)
    try:
        print(f"连接到 {args.host}:{args.port} ...")
        client.connect()
        print("认证成功, 执行测试命令...")
        print()

        start = time.time()
        response = client.send_command(args.command)
        elapsed = time.time() - start

        if elapsed > args.timeout:
            print(f"错误: 测试超时 ({elapsed:.1f}s > {args.timeout}s)", file=sys.stderr)
            sys.exit(2)

    except RCONConnectionError as e:
        print(f"连接错误: {e}", file=sys.stderr)
        sys.exit(2)
    except RCONAuthenticationError as e:
        print(f"认证错误: {e}", file=sys.stderr)
        sys.exit(2)
    except RCONError as e:
        print(f"RCON 错误: {e}", file=sys.stderr)
        sys.exit(2)
    finally:
        client.disconnect()

    # 解析并打印报告
    try:
        result = parse_test_response(response)
    except ValueError as e:
        print(f"结果解析错误: {e}", file=sys.stderr)
        print(f"\n原始响应:\n{response[:1000]}", file=sys.stderr)
        sys.exit(2)

    all_passed = print_report(result)

    # 退出码: 0=全部通过, 1=有失败
    sys.exit(0 if all_passed else 1)


if __name__ == "__main__":
    main()
