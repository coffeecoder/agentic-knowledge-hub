import unittest

from akh.models import SourceDocument, SourceType
from akh.parsers.text import TextParser


class TextParserTest(unittest.TestCase):
    def test_preserves_markdown_heading_and_lines(self) -> None:
        document = SourceDocument(
            "doc-1",
            "Design",
            SourceType.MARKDOWN,
            "abc",
            None,
            b"# Architecture\nCloud Run hosts the API.\n\n## Security\nDeny by default.",
            "text/markdown",
        )
        sections = TextParser().parse(document)
        self.assertEqual(("Architecture",), sections[0].heading_path)
        self.assertIn("Cloud Run", sections[0].text)
        self.assertEqual(("Architecture", "Security"), sections[1].heading_path)


if __name__ == "__main__":
    unittest.main()
