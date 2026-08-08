#!/usr/bin/env python3
# Copyright 2026 17Artist
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Format user-facing YAML collections as readable block-style YAML.

The formatter touches standalone YAML files and fenced ``yaml``/``yml`` blocks
in Markdown or MDX. Quoted placeholders and block scalar bodies are left untouched.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys


EXCLUDED_PARTS = {"build", ".runtime", ".gradle", ".git"}
FENCE_START = re.compile(r"^\s*```ya?ml\s*$", re.IGNORECASE)
FENCE_END = re.compile(r"^\s*```\s*$")


@dataclass(frozen=True)
class Scalar:
    text: str


@dataclass(frozen=True)
class Mapping:
    entries: tuple[tuple[str, "Node"], ...]


@dataclass(frozen=True)
class Sequence:
    items: tuple["Node", ...]


Node = Scalar | Mapping | Sequence


class FlowParser:
    def __init__(self, source: str) -> None:
        self.source = source
        self.index = 0

    def parse(self) -> Node:
        node = self._node(set())
        self._spaces()
        if self.index != len(self.source):
            raise ValueError(f"unexpected suffix: {self.source[self.index:]}")
        return node

    def _node(self, stops: set[str]) -> Node:
        self._spaces()
        if self._peek() == "{":
            return self._mapping()
        if self._peek() == "[":
            return self._sequence()
        return Scalar(self._scalar(stops).strip())

    def _mapping(self) -> Mapping:
        self._expect("{")
        entries: list[tuple[str, Node]] = []
        self._spaces()
        if self._peek() == "}":
            self.index += 1
            return Mapping(tuple())
        while True:
            key = self._scalar({":"}).strip()
            if not key:
                raise ValueError("empty mapping key")
            self._expect(":")
            value = self._node({",", "}"})
            entries.append((key, value))
            self._spaces()
            marker = self._peek()
            if marker == "}":
                self.index += 1
                return Mapping(tuple(entries))
            self._expect(",")

    def _sequence(self) -> Sequence:
        self._expect("[")
        items: list[Node] = []
        self._spaces()
        if self._peek() == "]":
            self.index += 1
            return Sequence(tuple())
        while True:
            items.append(self._node({",", "]"}))
            self._spaces()
            marker = self._peek()
            if marker == "]":
                self.index += 1
                return Sequence(tuple(items))
            self._expect(",")

    def _scalar(self, stops: set[str]) -> str:
        start = self.index
        quote: str | None = None
        escaped = False
        while self.index < len(self.source):
            char = self.source[self.index]
            if quote == '"':
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == quote:
                    quote = None
                self.index += 1
                continue
            if quote == "'":
                if char == "'" and self.index + 1 < len(self.source) and self.source[self.index + 1] == "'":
                    self.index += 2
                    continue
                if char == quote:
                    quote = None
                self.index += 1
                continue
            if char in {"'", '"'}:
                quote = char
                self.index += 1
                continue
            if char in stops:
                break
            self.index += 1
        if quote is not None:
            raise ValueError("unterminated quoted scalar")
        return self.source[start:self.index]

    def _spaces(self) -> None:
        while self.index < len(self.source) and self.source[self.index].isspace():
            self.index += 1

    def _peek(self) -> str | None:
        return self.source[self.index] if self.index < len(self.source) else None

    def _expect(self, value: str) -> None:
        self._spaces()
        if self._peek() != value:
            raise ValueError(f"expected {value!r} at {self.index}")
        self.index += 1


def is_empty(node: Node) -> bool:
    return isinstance(node, (Mapping, Sequence)) and not (node.entries if isinstance(node, Mapping) else node.items)


def render_child(node: Node, indent: str) -> list[str]:
    if isinstance(node, Mapping):
        lines: list[str] = []
        for key, value in node.entries:
            if is_empty(value):
                continue
            if isinstance(value, Scalar):
                lines.append(f"{indent}{key}: {value.text}")
            else:
                lines.append(f"{indent}{key}:")
                lines.extend(render_child(value, indent + "  "))
        return lines
    if isinstance(node, Sequence):
        lines = []
        for value in node.items:
            if is_empty(value):
                continue
            if isinstance(value, Scalar):
                lines.append(f"{indent}- {value.text}")
            elif isinstance(value, Mapping):
                visible = [(key, child) for key, child in value.entries if not is_empty(child)]
                if not visible:
                    continue
                first_key, first_value = visible[0]
                if isinstance(first_value, Scalar):
                    lines.append(f"{indent}- {first_key}: {first_value.text}")
                else:
                    lines.append(f"{indent}- {first_key}:")
                    lines.extend(render_child(first_value, indent + "    "))
                for key, child in visible[1:]:
                    if isinstance(child, Scalar):
                        lines.append(f"{indent}  {key}: {child.text}")
                    else:
                        lines.append(f"{indent}  {key}:")
                        lines.extend(render_child(child, indent + "    "))
            else:
                lines.append(f"{indent}-")
                lines.extend(render_child(value, indent + "  "))
        return lines
    raise TypeError(f"cannot render scalar child {node}")


def split_mapping(line: str) -> tuple[str, str, str] | None:
    indent_length = len(line) - len(line.lstrip(" "))
    indent = line[:indent_length]
    body = line[indent_length:]
    quote: str | None = None
    escaped = False
    for index, char in enumerate(body):
        if quote == '"':
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if quote == "'":
            if char == quote:
                quote = None
            continue
        if char in {"'", '"'}:
            quote = char
            continue
        if char == ":" and (index + 1 == len(body) or body[index + 1].isspace()):
            return indent, body[:index].rstrip(), body[index + 1 :].strip()
    return None


def format_yaml_lines(lines: list[str]) -> tuple[list[str], int]:
    output: list[str] = []
    changes = 0
    block_scalar_indent: int | None = None
    for line in lines:
        stripped = line.strip()
        indent_length = len(line) - len(line.lstrip(" "))
        if block_scalar_indent is not None:
            if not stripped or indent_length > block_scalar_indent:
                output.append(line)
                continue
            block_scalar_indent = None
        mapping = split_mapping(line)
        if mapping is not None and re.search(r":\s*[>|][-+]?\s*$", line):
            block_scalar_indent = indent_length
            output.append(line)
            continue
        list_match = re.match(r"^(\s*)-\s*(\{.*\}|\[.*\])\s*$", line)
        if list_match:
            try:
                node = FlowParser(list_match.group(2)).parse()
            except ValueError:
                output.append(line)
                continue
            replacement = render_child(Sequence((node,)), list_match.group(1))
            output.extend(replacement)
            changes += 1
            continue
        if mapping is None:
            output.append(line)
            continue
        indent, key, value = mapping
        if not value.startswith(("{", "[")):
            output.append(line)
            continue
        try:
            node = FlowParser(value).parse()
        except ValueError:
            output.append(line)
            continue
        if is_empty(node):
            changes += 1
            continue
        output.append(f"{indent}{key}:")
        output.extend(render_child(node, indent + "  "))
        changes += 1
    return output, changes


def format_markdown_lines(lines: list[str]) -> tuple[list[str], int]:
    output: list[str] = []
    yaml_fence = False
    changes = 0
    buffered: list[str] = []
    for line in lines:
        if not yaml_fence and FENCE_START.match(line):
            yaml_fence = True
            output.append(line)
            continue
        if yaml_fence and FENCE_END.match(line):
            formatted, count = format_yaml_lines(buffered)
            output.extend(formatted)
            output.append(line)
            buffered.clear()
            changes += count
            yaml_fence = False
            continue
        if yaml_fence:
            buffered.append(line)
        else:
            output.append(line)
    if buffered:
        output.extend(buffered)
    return output, changes


def eligible(path: Path, root: Path) -> bool:
    relative = path.relative_to(root)
    return not any(part in EXCLUDED_PARTS or part.startswith(".tmp") for part in relative.parts)


def process_file(path: Path, markdown: bool, write: bool) -> int:
    raw = path.read_bytes()
    bom = raw.startswith(b"\xef\xbb\xbf")
    text = raw.decode("utf-8-sig")
    newline = "\r\n" if b"\r\n" in raw else "\n"
    ended = text.endswith(("\n", "\r"))
    lines = text.splitlines()
    formatted, changes = format_markdown_lines(lines) if markdown else format_yaml_lines(lines)
    if changes and write:
        rendered = newline.join(formatted) + (newline if ended else "")
        path.write_bytes((b"\xef\xbb\xbf" if bom else b"") + rendered.encode("utf-8"))
    return changes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    files = sorted(
        path
        for path in root.rglob("*")
        if path.is_file() and eligible(path, root) and path.suffix.lower() in {".md", ".mdx", ".yml", ".yaml"}
    )
    changed: list[tuple[Path, int]] = []
    for path in files:
        count = process_file(path, path.suffix.lower() in {".md", ".mdx"}, args.write)
        if count:
            changed.append((path.relative_to(root), count))
    for path, count in changed:
        print(f"{path}: {count}")
    print(f"files={len(files)} changed={len(changed)} collections={sum(count for _, count in changed)}")
    return 0 if args.write or not changed else 1


if __name__ == "__main__":
    sys.exit(main())
