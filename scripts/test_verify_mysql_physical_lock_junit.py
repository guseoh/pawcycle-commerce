"""Fail-closed negative controls for the CI physical lock JUnit contract."""
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
from scripts.verify_mysql_physical_lock_junit import T09, T10, verify, require_t10_abc_source

class LockJUnitGateTests(unittest.TestCase):
    def test_missing_xml_or_test_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaisesRegex(ValueError, "Missing JUnit"):
                verify(root, [False, False])
            ET.ElementTree(ET.Element("testsuite")).write(root / "TEST-fixture.xml")
            with self.assertRaisesRegex(ValueError, "expected 1"):
                verify(root, [False, False])

    def test_skipped_and_failed_oracle_rejected(self):
        for state in ("skipped", "error", "failure"):
            with self.subTest(state=state), tempfile.TemporaryDirectory() as tmp:
                case = ET.Element("testcase", classname=T09[0], name=T09[1] + "()")
                ET.SubElement(case, state)
                root = ET.Element("testsuite")
                root.append(case)
                ET.ElementTree(root).write(Path(tmp) / "TEST-fixture.xml")
                with self.assertRaisesRegex(ValueError, "skipped or failed"):
                    verify(Path(tmp), [False, False])

    def test_t10_rejects_duplicate_or_missing_invocation_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            valid = ET.Element("testsuite")
            valid.append(ET.Element("testcase", classname=T09[0], name=T09[1] + "()"))
            for label in ['[1] kind = "A"', '[2] kind = "B"', '[3] kind = "C"']:
                valid.append(ET.Element("testcase", classname=T10[0][0], name=label))
            valid.append(ET.Element("testcase", classname=T10[1][0], name=T10[1][1] + "()"))
            report = Path(tmp) / "TEST-physical.xml"
            ET.ElementTree(valid).write(report)
            self.assertEqual(len(verify(Path(tmp), [True, True])), 3)

            # An aborted/assumption-skipped T10 invocation must fail the required CI gate.
            skipped = ET.SubElement(list(valid)[1], "skipped")
            ET.ElementTree(valid).write(report)
            with self.assertRaisesRegex(ValueError, "skipped or failed"):
                verify(Path(tmp), [True, True])
            list(valid)[1].remove(skipped)

            # Same invocation reported three times must not hide missing B or C.
            cases = list(valid)
            cases[2].set("name", '[1] kind = "A"')
            ET.ElementTree(valid).write(report)
            with self.assertRaisesRegex(ValueError, "missing or duplicated"):
                verify(Path(tmp), [True, True])
            cases[2].set("name", '[2] kind = "B"')

            # Unknown or unlabelled invocation must fail closed, not count as PASS.
            cases[3].set("name", '[3] kind = "X"')
            ET.ElementTree(valid).write(report)
            with self.assertRaisesRegex(ValueError, "missing or duplicated"):
                verify(Path(tmp), [True, True])

    def test_t10_abc_value_source_mapping_is_frozen(self):
        method = "void fixedReadViewHistoryAndBothInsertBlockingPaths(String kind)"
        require_t10_abc_source('@ValueSource(strings = {"A", "B", "C"})\n' + method)
        for values in ('{"A", "A", "C"}', '{"C", "B", "A"}', '{"A", "B"}'):
            with self.subTest(values=values), self.assertRaisesRegex(ValueError, "exactly A, B, C"):
                require_t10_abc_source("@ValueSource(strings = " + values + ")\n" + method)

    def test_t09_and_t10_required_when_sources_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = ET.Element("testsuite")
            root.append(ET.Element("testcase", classname=T09[0], name=T09[1] + "()"))
            for label in ['[1] kind = "A"', '[2] kind = "B"', '[3] kind = "C"']:
                root.append(ET.Element("testcase", classname=T10[0][0], name=label))
            root.append(ET.Element("testcase", classname=T10[1][0], name=T10[1][1] + "()"))
            ET.ElementTree(root).write(Path(tmp) / "TEST-fixture.xml")
            self.assertEqual(len(verify(Path(tmp), [False, False])), 1)
            self.assertEqual(len(verify(Path(tmp), [True, True])), 3)
            root.remove(root[-1])
            ET.ElementTree(root).write(Path(tmp) / "TEST-fixture.xml")
            with self.assertRaisesRegex(ValueError, "expected 1"):
                verify(Path(tmp), [True, True])
            with self.assertRaisesRegex(ValueError, "Partial T10"):
                verify(Path(tmp), [True, False])

if __name__ == "__main__":
    unittest.main()
