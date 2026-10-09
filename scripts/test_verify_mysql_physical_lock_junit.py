"""Fail-closed negative controls for the CI physical lock JUnit contract."""
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from scripts.verify_mysql_physical_lock_junit import T09, T10, verify

class LockJUnitGateTests(unittest.TestCase):
    def test_missing_xml_or_test_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaisesRegex(ValueError, "Missing JUnit"):
                verify(root, [False, False])
            ET.ElementTree(ET.Element("testsuite")).write(root / "TEST.xml")
            with self.assertRaisesRegex(ValueError, "expected 1"):
                verify(root, [False, False])

    def test_skipped_and_failed_oracle_rejected(self):
        for state in ("skipped", "error", "failure"):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as tmp:
                case = ET.Element("testcase", classname=T09[0], name=T09[1] + "()")
                ET.SubElement(case, state)
                root = ET.Element("testsuite")
                root.append(case)
                ET.ElementTree(root).write(Path(tmp) / "TEST.xml")
                with self.assertRaisesRegex(ValueError, "skipped or failed"):
                    verify(Path(tmp), [False, False])

    def test_t09_and_t10_required_when_sources_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = ET.Element("testsuite")
            root.append(ET.Element("testcase", classname=T09[0], name=T09[1] + "()"))
            for cls, name, count in T10:
                for i in range(count):
                    root.append(ET.Element("testcase", classname=cls, name=name + f"(String)[{i+1}]"))
            ET.ElementTree(root).write(Path(tmp) / "TEST.xml")
            self.assertEqual(len(verify(Path(tmp), [False, False])), 1)
            self.assertEqual(len(verify(Path(tmp), [True, True])), 3)
            root.remove(root[-1])
            ET.ElementTree(root).write(Path(tmp) / "TEST.xml")
            with self.assertRaisesRegex(ValueError, "expected 1"):
                verify(Path(tmp), [True, True])
            with self.assertRaisesRegex(ValueError, "Partial T10"):
                verify(Path(tmp), [True, False])

if __name__ == "__main__":
    unittest.main()
