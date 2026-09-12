import unittest

from akh.chunking import SemanticChunker
from akh.ingestion import IngestionService
from akh.models import SourceDocument, SourceType


class IngestionServiceTest(unittest.TestCase):
    def test_skips_unchanged_content(self) -> None:
        document = SourceDocument(
            "doc", "Doc", SourceType.TEXT, "v1", None, b"some useful knowledge", "text/plain"
        )
        service = IngestionService(chunker=SemanticChunker(max_words=20, overlap_words=3))
        first = service.ingest(document)
        second = service.ingest(document, previous_hash=first.content_hash)
        self.assertEqual("CHANGED", first.status)
        self.assertEqual("UNCHANGED", second.status)
        self.assertEqual((), second.chunks)


if __name__ == "__main__":
    unittest.main()
