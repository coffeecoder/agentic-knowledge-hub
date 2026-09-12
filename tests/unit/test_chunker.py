import unittest

from akh.chunking import SemanticChunker
from akh.models import ParsedSection, SourceDocument, SourceType


class SemanticChunkerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.document = SourceDocument(
            "runbook",
            "Gateway Runbook",
            SourceType.TEXT,
            "v1",
            "https://example.test/runbook",
            b"unused",
            "text/plain",
            ("support",),
        )

    def test_creates_deterministic_chunks_with_citations(self) -> None:
        section = ParsedSection(
            " ".join(f"word{i}" for i in range(45)), ("Recovery",), None, 10, 20
        )
        chunker = SemanticChunker(max_words=20, overlap_words=5)
        first = chunker.chunk(self.document, [section])
        second = chunker.chunk(self.document, [section])
        self.assertEqual([c.chunk_id for c in first], [c.chunk_id for c in second])
        self.assertEqual(3, len(first))
        self.assertEqual(("Recovery",), first[0].citation.heading_path)
        self.assertEqual(("support",), first[0].allowed_groups)

    def test_rejects_invalid_overlap(self) -> None:
        with self.assertRaises(ValueError):
            SemanticChunker(max_words=20, overlap_words=20)


if __name__ == "__main__":
    unittest.main()
