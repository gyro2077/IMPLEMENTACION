#!/usr/bin/env python3
"""Fase 3A: parser minimo para resultados OpenSCAP ARF."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
import xml.etree.ElementTree as ET


def _txt(node: ET.Element | None) -> str:
	if node is None:
		return ""
	return " ".join("".join(node.itertext()).split())


def _catalog_rules(root: ET.Element) -> dict[str, dict[str, str]]:
	catalog: dict[str, dict[str, str]] = {}
	for rule in root.findall(".//{*}Rule"):
		rule_id = rule.attrib.get("id", "")
		if not rule_id:
			continue
		catalog[rule_id] = {
			"severity": (rule.attrib.get("severity") or "unknown").lower(),
			"title": _txt(rule.find("./{*}title")),
		}
	return catalog


def parse_arf(arf_path: Path, html_path: Path, host: str) -> dict:
	tree = ET.parse(arf_path)
	root = tree.getroot()

	rules = _catalog_rules(root)
	test_result = root.find(".//{*}TestResult")
	if test_result is None:
		raise ValueError("No se encontro TestResult en el ARF")

	profile_node = test_result.find("./{*}profile")
	profile_id = profile_node.attrib.get("idref", "unknown") if profile_node is not None else "unknown"

	score_node = test_result.find("./{*}score")
	score_text = _txt(score_node) if score_node is not None else "0"
	try:
		score = float(score_text)
	except ValueError:
		score = 0.0

	started_at = test_result.attrib.get("start-time", "")
	finished_at = test_result.attrib.get("end-time", "")

	failed_rules: list[dict[str, str]] = []
	for rr in test_result.findall(".//{*}rule-result"):
		rule_id = rr.attrib.get("idref", "")
		result = _txt(rr.find("./{*}result")).lower()
		if result != "fail":
			continue

		rule_info = rules.get(rule_id, {})
		failed_rules.append(
			{
				"rule_id": rule_id,
				"severity": rule_info.get("severity", "unknown"),
				"result": result,
				"title": rule_info.get("title", ""),
			}
		)

	summary = {
		"host": host,
		"profile_id": profile_id,
		"score": score,
		"status": "completed",
		"arf_path": str(arf_path),
		"html_report_path": str(html_path),
		"started_at": started_at,
		"finished_at": finished_at,
		"failed_rules": failed_rules,
		"failed_rules_count": len(failed_rules),
		"generated_at": datetime.now(timezone.utc).isoformat(),
	}
	return summary


def main() -> None:
	parser = argparse.ArgumentParser(description="Parsea ARF de OpenSCAP a JSON minimo")
	parser.add_argument("--arf", required=True, help="Ruta al archivo ARF/XML")
	parser.add_argument("--html", required=True, help="Ruta al reporte HTML asociado")
	parser.add_argument("--host", required=True, help="Nombre del host escaneado")
	parser.add_argument("--output", required=True, help="Ruta del JSON de salida")
	args = parser.parse_args()

	arf_path = Path(args.arf).resolve()
	html_path = Path(args.html).resolve()
	output_path = Path(args.output).resolve()

	if not arf_path.exists():
		raise FileNotFoundError(f"No existe ARF: {arf_path}")
	if not html_path.exists():
		raise FileNotFoundError(f"No existe HTML: {html_path}")

	summary = parse_arf(arf_path=arf_path, html_path=html_path, host=args.host)

	output_path.parent.mkdir(parents=True, exist_ok=True)
	output_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
	print(str(output_path))


if __name__ == "__main__":
	main()
